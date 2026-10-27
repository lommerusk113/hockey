package no.hockey;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.hockey.SocialRepositories.AppUserRepository;
import no.hockey.SocialRepositories.ChatMessageRepository;
import no.hockey.SocialRepositories.ChatReactionRepository;
import no.hockey.SocialRepositories.MatchCommentRepository;
import no.hockey.SocialRepositories.MatchReactionRepository;
import no.hockey.SocialRepositories.PushSubscriptionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api")
public class SocialController {

    static final Set<String> EMOJIS = Set.of("🔥", "😂", "😱", "👏", "💀");

    private final AppUserRepository users;
    private final MatchRepository matches;
    private final PlayerRepository players;
    private final MatchCommentRepository comments;
    private final MatchReactionRepository reactions;
    private final ChatMessageRepository chat;
    private final ChatReactionRepository chatReactions;
    private final PushSubscriptionRepository subscriptions;
    private final Notifier notifier;

    public SocialController(AppUserRepository users,
                            MatchRepository matches,
                            PlayerRepository players,
                            MatchCommentRepository comments,
                            MatchReactionRepository reactions,
                            ChatMessageRepository chat,
                            ChatReactionRepository chatReactions,
                            PushSubscriptionRepository subscriptions,
                            Notifier notifier) {
        this.users = users;
        this.matches = matches;
        this.players = players;
        this.comments = comments;
        this.reactions = reactions;
        this.chat = chat;
        this.chatReactions = chatReactions;
        this.subscriptions = subscriptions;
        this.notifier = notifier;
    }

    @Get("/me")
    public AppUser me(Authentication authentication) {
        return currentUser(authentication);
    }

    @Put("/me")
    public AppUser updateMe(Authentication authentication, @Body Settings settings) {

        var user = currentUser(authentication);

        if (settings.playerId() != null) {
            players.findById(settings.playerId())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_REQUEST, "Unknown player"));
            users.findByPlayerId(settings.playerId())
                .filter(other -> !other.id().equals(user.id()))
                .ifPresent(other -> {
                    throw new HttpStatusException(HttpStatus.CONFLICT, other.name() + " is already linked to that player");
                });
        }

        return users.update(user.withSettings(settings.playerId(), settings.notifyMatches(), settings.notifyChat()));
    }

    @Get("/users")
    public List<UserView> listUsers() {
        return users.listOrderByName().stream().map(UserView::of).toList();
    }

    /** All comments and reactions; small enough for a group of friends to load in one go. */
    @Get("/social")
    public Social social() {
        // ponytail: everything at once; page per match if this grows past a few thousand rows
        return new Social(comments.listOrderByCreatedAt(), List.copyOf(reactions.findAll()));
    }

    @Status(HttpStatus.CREATED)
    @Post("/matches/{matchId}/comments")
    public MatchComment addComment(Authentication authentication, Long matchId, @Body @Valid Text text) {

        var user = currentUser(authentication);
        var match = matches.findById(matchId)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Match not found"));

        var comment = comments.save(new MatchComment(null, matchId, user.id(), text.body().trim(), null));
        notifyPlayers(match, user, "💬 " + user.name() + " on " + describe(match), comment.body());

        return comment;
    }

    @Status(HttpStatus.NO_CONTENT)
    @Delete("/comments/{id}")
    public void deleteComment(Authentication authentication, Long id) {

        var user = currentUser(authentication);
        var comment = comments.findById(id)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        if (!comment.userId().equals(user.id())) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "You can only delete your own comments");
        }

        comments.delete(comment);
    }

    /** Toggles the current user's reaction; returns whether it is now set. */
    @Post("/matches/{matchId}/reactions")
    public boolean toggleReaction(Authentication authentication, Long matchId, @Body @Valid Reaction reaction) {

        if (!EMOJIS.contains(reaction.emoji())) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Unsupported reaction");
        }
        var user = currentUser(authentication);
        var match = matches.findById(matchId)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Match not found"));

        var existing = reactions.findByMatchIdAndUserIdAndEmoji(matchId, user.id(), reaction.emoji());
        if (existing.isPresent()) {
            reactions.delete(existing.get());
            return false;
        }

        reactions.save(new MatchReaction(null, matchId, user.id(), reaction.emoji()));
        notifyPlayers(match, user, reaction.emoji() + " " + user.name() + " reacted", describe(match));

        return true;
    }

    /**
     * The latest 100 messages with their reactions, oldest first. With {@code after}, only newer messages:
     * a cheap poll for the unread badge while the chat is closed.
     */
    @Get("/chat")
    public List<ChatMessageView> listChat(@QueryValue @Nullable Long after) {

        var messages = after != null ? chat.findByIdGreaterThanOrderById(after) : chat.findTop100OrderByIdDesc().reversed();
        var reactions = messages.isEmpty()
            ? List.<ChatReaction>of()
            : chatReactions.findByMessageIdIn(messages.stream().map(ChatMessage::id).toList());

        return messages.stream()
            .map(m -> new ChatMessageView(m.id(), m.userId(), m.body(), m.createdAt(),
                reactions.stream().filter(r -> r.messageId().equals(m.id())).toList()))
            .toList();
    }

    /** Toggles the current user's reaction on a chat message; returns whether it is now set. */
    @Post("/chat/{messageId}/reactions")
    public boolean toggleChatReaction(Authentication authentication, Long messageId, @Body @Valid Reaction reaction) {

        if (!EMOJIS.contains(reaction.emoji())) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Unsupported reaction");
        }
        if (!chat.existsById(messageId)) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        var user = currentUser(authentication);

        var existing = chatReactions.findByMessageIdAndUserIdAndEmoji(messageId, user.id(), reaction.emoji());
        if (existing.isPresent()) {
            chatReactions.delete(existing.get());
            return false;
        }

        chatReactions.save(new ChatReaction(null, messageId, user.id(), reaction.emoji()));

        return true;
    }

    @Status(HttpStatus.CREATED)
    @Post("/chat")
    public ChatMessage sendChat(Authentication authentication, @Body @Valid Text text) {

        var user = currentUser(authentication);
        var message = chat.save(new ChatMessage(null, user.id(), text.body().trim(), null));

        var recipients = users.listOrderByName().stream()
            .filter(other -> other.notifyChat() && !other.id().equals(user.id()))
            .map(AppUser::id)
            .toList();
        notifier.notify(recipients, user.name(), message.body(), "/?chat=1");

        return message;
    }

    @Get("/push/key")
    public PushKey pushKey() {
        return new PushKey(notifier.publicKey());
    }

    @Status(HttpStatus.NO_CONTENT)
    @Post("/push/subscriptions")
    public void subscribe(Authentication authentication, @Body @Valid Subscription subscription) {

        var user = currentUser(authentication);

        // The same browser re-subscribing (or a new user on a shared device) replaces the old row
        subscriptions.findByEndpoint(subscription.endpoint()).ifPresent(subscriptions::delete);
        subscriptions.save(new PushSubscription(null, user.id(), subscription.endpoint(), subscription.keys().p256dh(), subscription.keys().auth()));
    }

    @Status(HttpStatus.NO_CONTENT)
    @Delete("/push/subscriptions")
    public void unsubscribe(@QueryValue String endpoint) {
        subscriptions.deleteByEndpoint(endpoint);
    }

    private AppUser currentUser(Authentication authentication) {
        return users.findById(Long.valueOf(authentication.getName()))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    /** Tells the users linked to the match's players, except whoever caused it. */
    private void notifyPlayers(Match match, AppUser actor, String title, String body) {

        var recipients = users.findByPlayerIdIn(List.of(match.homePlayerId(), match.awayPlayerId())).stream()
            .filter(user -> user.notifyMatches() && !user.id().equals(actor.id()))
            .map(AppUser::id)
            .toList();

        notifier.notify(recipients, title, body, "/?match=" + match.id());
    }

    private String describe(Match match) {

        var home = players.findById(match.homePlayerId()).map(Player::name).orElse("?");
        var away = players.findById(match.awayPlayerId()).map(Player::name).orElse("?");

        return home + " " + match.homeGoals() + "–" + match.awayGoals() + " " + away;
    }

    @Serdeable
    public record Settings(@Nullable Long playerId, boolean notifyMatches, boolean notifyChat) {
    }

    @Serdeable
    public record UserView(Long id, String name, @Nullable String pictureUrl, @Nullable Long playerId) {

        static UserView of(AppUser user) {
            return new UserView(user.id(), user.name(), user.pictureUrl(), user.playerId());
        }
    }

    @Serdeable
    public record ChatMessageView(Long id, Long userId, String body, Instant createdAt, List<ChatReaction> reactions) {
    }

    @Serdeable
    public record Social(List<MatchComment> comments, List<MatchReaction> reactions) {
    }

    @Serdeable
    public record Text(@NotBlank @Size(max = 1000) String body) {
    }

    @Serdeable
    public record Reaction(@NotBlank String emoji) {
    }

    @Serdeable
    public record PushKey(String publicKey) {
    }

    @Serdeable
    public record Subscription(@NotBlank String endpoint, @Valid Keys keys) {

        @Serdeable
        public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
        }
    }
}
