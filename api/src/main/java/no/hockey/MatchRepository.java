package no.hockey;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface MatchRepository extends CrudRepository<Match, Long> {

    List<Match> listOrderByPlayedAtDesc();

    boolean existsByHomePlayerIdOrAwayPlayerId(Long homePlayerId, Long awayPlayerId);
}
