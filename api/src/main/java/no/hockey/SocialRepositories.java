package no.hockey;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// ponytail: the social repositories live together; split per file if they grow real logic
final class SocialRepositories {

    private SocialRepositories() {
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    interface AppUserRepository extends CrudRepository<AppUser, Long> {

        Optional<AppUser> findByGoogleSubject(String googleSubject);

        Optional<AppUser> findByPlayerId(Long playerId);

        List<AppUser> findByPlayerIdIn(Collection<Long> playerIds);

        List<AppUser> listOrderByName();
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    interface MatchCommentRepository extends CrudRepository<MatchComment, Long> {

        List<MatchComment> listOrderByCreatedAt();
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    interface MatchReactionRepository extends CrudRepository<MatchReaction, Long> {

        Optional<MatchReaction> findByMatchIdAndUserIdAndEmoji(Long matchId, Long userId, String emoji);
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    interface ChatMessageRepository extends CrudRepository<ChatMessage, Long> {

        List<ChatMessage> findTop100OrderByIdDesc();

        List<ChatMessage> findByIdGreaterThanOrderById(Long id);
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    interface PushSubscriptionRepository extends CrudRepository<PushSubscription, Long> {

        List<PushSubscription> findByUserIdIn(Collection<Long> userIds);

        Optional<PushSubscription> findByEndpoint(String endpoint);

        void deleteByEndpoint(String endpoint);
    }
}
