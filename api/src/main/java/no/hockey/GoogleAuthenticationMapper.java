package no.hockey;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.oauth2.endpoint.authorization.state.State;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdAuthenticationMapper;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdClaims;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import no.hockey.SocialRepositories.AppUserRepository;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Lets in verified Google accounts from the allowed Workspace domain and keeps their profile in {@code app_user}.
 * The session's principal name is the app user id.
 */
@Singleton
@Named("google")
public class GoogleAuthenticationMapper implements OpenIdAuthenticationMapper {

    private final AppUserRepository users;
    private final String allowedDomain;

    public GoogleAuthenticationMapper(AppUserRepository users, @Value("${auth.allowed-domain}") String allowedDomain) {
        this.users = users;
        this.allowedDomain = allowedDomain;
    }

    @Override
    public Publisher<AuthenticationResponse> createAuthenticationResponse(String providerName,
                                                                         OpenIdTokenResponse tokenResponse,
                                                                         OpenIdClaims claims,
                                                                         @Nullable State state) {

        // `hd` is only present for Workspace accounts, so personal gmail addresses are rejected too
        var domain = claims.getClaims().get("hd");
        if (!Boolean.TRUE.equals(claims.isEmailVerified()) || !allowedDomain.equalsIgnoreCase(String.valueOf(domain))) {
            return Mono.just(AuthenticationResponse.failure("Only @" + allowedDomain + " accounts can sign in"));
        }

        return Mono.fromCallable(() -> upsert(claims))
            .subscribeOn(Schedulers.boundedElastic())
            .map(user -> AuthenticationResponse.success(String.valueOf(user.id())));
    }

    private AppUser upsert(OpenIdClaims claims) {

        var name = claims.getName() == null ? claims.getEmail() : claims.getName();

        return users.findByGoogleSubject(claims.getSubject())
            .map(user -> users.update(user.withProfile(claims.getEmail(), name, claims.getPicture())))
            .orElseGet(() -> users.save(new AppUser(null, claims.getSubject(), claims.getEmail(), name, claims.getPicture(), null, true, true)));
    }
}
