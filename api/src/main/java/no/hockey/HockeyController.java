package no.hockey;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.validation.Valid;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api")
public class HockeyController {

    private final PlayerRepository players;
    private final MatchRepository matches;
    private final ScoreScanner scanner;

    public HockeyController(PlayerRepository players, MatchRepository matches, ScoreScanner scanner) {
        this.players = players;
        this.matches = matches;
        this.scanner = scanner;
    }

    @Get("/players")
    public List<Player> listPlayers() {
        return players.listOrderByName();
    }

    @Status(HttpStatus.CREATED)
    @Post("/players")
    public Player createPlayer(@Body @Valid Player player) {
        return players.save(unique(null, player, true));
    }

    @Put("/players/{id}")
    public Player updatePlayer(Long id, @Body @Valid Player player) {

        var existing = players.findById(id)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        return players.update(unique(id, player, player.active() == null ? existing.active() : player.active()));
    }

    @Status(HttpStatus.NO_CONTENT)
    @Delete("/players/{id}")
    public void deletePlayer(Long id) {

        if (matches.existsByHomePlayerIdOrAwayPlayerId(id, id)) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "Players with recorded matches cannot be removed");
        }

        players.deleteById(id);
    }

    @Get("/matches")
    public List<Match> listMatches() {
        return matches.listOrderByPlayedAtDesc();
    }

    @Status(HttpStatus.CREATED)
    @Post("/matches")
    public Match createMatch(@Body @Valid Match match) {

        return matches.save(withDefaults(null, match));
    }

    @Transactional
    @Status(HttpStatus.CREATED)
    @Post("/matches/batch")
    public List<Match> createMatches(@Body List<@Valid Match> batch) {
        return batch.stream().map(match -> matches.save(withDefaults(null, match))).toList();
    }

    @Put("/matches/{id}")
    public Match updateMatch(Long id, @Body @Valid Match match) {

        if (!matches.existsById(id)) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Match not found");
        }

        return matches.update(withDefaults(id, match));
    }

    @Status(HttpStatus.NO_CONTENT)
    @Delete("/matches/{id}")
    public void deleteMatch(Long id) {
        matches.deleteById(id);
    }

    @Post(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA)
    public List<ScoreScanner.ScannedMatch> scan(CompletedFileUpload image) throws IOException, InterruptedException {

        var contentType = image.getContentType().map(MediaType::toString).orElse(MediaType.IMAGE_JPEG);

        var active = players.listOrderByName().stream().filter(Player::active).toList();

        return scanner.scan(image.getBytes(), contentType, active);
    }

    /** Matches as CSV or board-style text, e.g. transcribed by another AI for a very full board. */
    @Post(value = "/import", consumes = MediaType.TEXT_PLAIN)
    public ScoreScanner.ImportResult importMatches(@Body String text) {

        var active = players.listOrderByName().stream().filter(Player::active).toList();

        return ScoreScanner.importText(text, active);
    }

    private Player unique(Long id, Player player, Boolean active) {

        var name = player.name().trim();
        var shortName = player.shortName().trim();

        for (var other : players.listOrderByName()) {
            if (other.id().equals(id)) {
                continue;
            }
            if (other.name().equalsIgnoreCase(name)) {
                throw new HttpStatusException(HttpStatus.CONFLICT, "A player named " + name + " already exists");
            }
            if (other.shortName().equalsIgnoreCase(shortName)) {
                throw new HttpStatusException(HttpStatus.CONFLICT, "Shorthand " + shortName + " is already used by " + other.name());
            }
        }

        return new Player(id, name, shortName, active);
    }

    private Match withDefaults(Long id, Match match) {

        if (match.homePlayerId().equals(match.awayPlayerId())) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "A player cannot play against themselves");
        }

        var playedAt = match.playedAt() == null ? Instant.now() : match.playedAt();

        return new Match(id, playedAt, match.homePlayerId(), match.awayPlayerId(), match.homeGoals(), match.awayGoals());
    }
}
