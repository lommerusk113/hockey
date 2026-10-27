package no.hockey;

import io.micronaut.context.annotation.Value;
import io.micronaut.json.JsonMapper;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import no.hockey.SocialRepositories.PushSubscriptionRepository;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Sends Web Push notifications in the background. Disabled (logs only) when no VAPID keys are configured.
 */
@Singleton
public class Notifier {

    private static final Logger LOG = LoggerFactory.getLogger(Notifier.class);

    private final PushSubscriptionRepository subscriptions;
    private final JsonMapper json;
    private final ExecutorService executor;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    private PushService push;

    public Notifier(PushSubscriptionRepository subscriptions,
                    JsonMapper json,
                    @Named(TaskExecutors.BLOCKING) ExecutorService executor,
                    @Value("${push.public-key}") String publicKey,
                    @Value("${push.private-key}") String privateKey,
                    @Value("${push.subject}") String subject) {
        this.subscriptions = subscriptions;
        this.json = json;
        this.executor = executor;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    @PostConstruct
    void init() throws Exception {

        if (publicKey.isBlank() || privateKey.isBlank()) {
            LOG.warn("VAPID keys not configured, push notifications are disabled");
            return;
        }

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        push = new PushService(publicKey, privateKey, subject);
    }

    public String publicKey() {
        return publicKey;
    }

    /** Sends to every device of the given users; never throws, never blocks the caller. */
    public void notify(Collection<Long> userIds, String title, String body, String url) {

        if (push == null || userIds.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                var payload = json.writeValueAsBytes(Map.of("title", title, "body", body, "url", url));
                for (var subscription : subscriptions.findByUserIdIn(userIds)) {
                    send(subscription, payload);
                }
            } catch (Exception e) {
                LOG.warn("Push notification failed", e);
            }
        });
    }

    private void send(PushSubscription subscription, byte[] payload) throws Exception {

        var response = push.send(new Notification(subscription.endpoint(), subscription.p256dh(), subscription.auth(), payload));
        var status = response.getStatusLine().getStatusCode();

        // 404/410: the browser dropped this subscription (uninstalled, permission revoked)
        if (status == 404 || status == 410) {
            subscriptions.deleteByEndpoint(subscription.endpoint());
        } else if (status >= 400) {
            LOG.warn("Push to {} failed with {}", subscription.endpoint(), status);
        }
    }
}
