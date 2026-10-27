package no.hockey;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads match results off a photo (a whiteboard of results or a game scoreboard) using an OpenAI vision model.
 */
@Singleton
public class ScoreScanner {

    private static final String PROMPT = """
        This photo shows results from the Stiga table hockey game.
        Usually it is a whiteboard or paper with one match per line, written like "A - B : 2 - 3",
        where A and B are player names or abbreviations and the first score belongs to A.
        homeLabel is only the first player as written (e.g. "K"), awayLabel only the second (e.g. "Mf").
        It can also be a single game scoreboard; then return it as one match with empty labels.
        A board can have several columns and 50 or more lines. Transcribe every single line; do not stop early or summarize.
        Read the left column top to bottom first, then the next column, keeping each line's left-to-right order.
        Faint, half-erased lines count too. Skip only lines that are truly unreadable.""";
    // Import formats: "K,Mf,4,2" (comma/semicolon/tab, optional quotes) and the board style "K - Mf : 4 - 2"
    private static final Pattern SEPARATED = Pattern.compile("^\"?([^,;\\t\"]+)\"?[,;\\t]\"?([^,;\\t\"]+)\"?[,;\\t]\\s*(\\d+)\\s*[,;\\t]\\s*(\\d+)\\s*$");
    private static final Pattern BOARD = Pattern.compile("^(.+?)\\s*[-–]\\s*(.+?)\\s*:\\s*(\\d+)\\s*[-–]\\s*(\\d+)$");

    private static final Map<String, Object> SCHEMA = Map.of(
        "type", "object",
        "additionalProperties", false,
        "required", List.of("matches"),
        "properties", Map.of("matches", Map.of(
            "type", "array",
            "items", Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("homeLabel", "awayLabel", "homeGoals", "awayGoals"),
                "properties", Map.of(
                    "homeLabel", Map.of("type", "string"),
                    "awayLabel", Map.of("type", "string"),
                    "homeGoals", Map.of("type", "integer"),
                    "awayGoals", Map.of("type", "integer")
                )
            )
        ))
    );

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json;
    private final String apiKey;
    private final String model;

    public ScoreScanner(JsonMapper json,
                        @Value("${openai.api-key}") String apiKey,
                        @Value("${openai.model}") String model) {
        this.json = json;
        this.apiKey = apiKey;
        this.model = model;
    }

    public List<ScannedMatch> scan(byte[] image, String contentType, List<Player> players) throws IOException, InterruptedException {

        if (apiKey.isBlank()) {
            throw new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OPENAI_API_KEY is not configured");
        }

        var dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image);
        var body = Map.of(
            "model", model,
            "reasoning", Map.of("effort", "low"),
            "input", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "input_text", "text", PROMPT),
                Map.of("type", "input_image", "image_url", dataUrl, "detail", "high")
            ))),
            "text", Map.of("format", Map.of("type", "json_schema", "name", "score", "strict", true, "schema", SCHEMA))
        );

        // A full board takes gpt-5-mini around 30 seconds; stay under the proxy's 120 s timeout
        var request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
            .timeout(Duration.ofSeconds(110))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
            .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request failed: " + response.body());
        }

        var text = json.readValue(response.body(), OpenAiResponse.class).output().stream()
            .filter(output -> "message".equals(output.type()) && output.content() != null)
            .flatMap(output -> output.content().stream())
            .filter(content -> "output_text".equals(content.type()))
            .map(Content::text)
            .findFirst()
            .orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned no answer"));

        return json.readValue(text, AiResult.class).matches().stream()
            .map(ScoreScanner::splitMergedLabels)
            .map(m -> toScanned(m, players))
            .toList();
    }

    /**
     * Matches typed out by hand or by another tool, one per line: "K,Mf,4,2" (comma, semicolon or tab separated)
     * or the board format "K - Mf : 4 - 2". Blank lines, # comments and a header row are ignored;
     * any other line that doesn't parse is returned so the user can see what was skipped.
     */
    public static ImportResult importText(String text, List<Player> players) {

        var matches = new ArrayList<ScannedMatch>();
        var skipped = new ArrayList<String>();

        for (var line : text.lines().map(String::strip).toList()) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            var parsed = parseLine(line);
            if (parsed != null) {
                matches.add(toScanned(parsed, players));
            } else if (line.matches(".*\\d.*")) {
                skipped.add(line);
            }
            // lines without any digit are headers ("home,away,...") or headings: ignore
        }

        return new ImportResult(matches, skipped);
    }

    static @Nullable AiMatch parseLine(String line) {

        for (var pattern : List.of(SEPARATED, BOARD)) {
            var m = pattern.matcher(line);
            if (m.matches()) {
                return new AiMatch(m.group(1).strip(), m.group(2).strip(), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            }
        }

        return null;
    }

    private static ScannedMatch toScanned(AiMatch m, List<Player> players) {
        return new ScannedMatch(m.homeLabel(), m.awayLabel(), resolve(m.homeLabel(), players), resolve(m.awayLabel(), players), m.homeGoals(), m.awayGoals());
    }

    /**
     * The model sometimes returns "K-Mf" as the first label and leaves the second empty; split it back into two players.
     */
    static AiMatch splitMergedLabels(AiMatch match) {

        if (!match.awayLabel().isBlank()) {
            return match;
        }

        var parts = match.homeLabel().split("\\s*[-–]\\s*", 2);
        if (parts.length < 2) {
            return match;
        }

        return new AiMatch(parts[0].trim(), parts[1].trim(), match.homeGoals(), match.awayGoals());
    }

    /**
     * The player whose shorthand or name is the label, else the only player whose name starts with it ("Kri" → Kristian).
     * Deliberately not left to the model: its name guesses varied between runs and were confidently wrong.
     */
    static @Nullable Long resolve(String label, List<Player> players) {

        var written = label.trim();
        if (written.isEmpty()) {
            return null;
        }

        var exact = players.stream()
            .filter(p -> p.shortName().equalsIgnoreCase(written) || p.name().equalsIgnoreCase(written))
            .findFirst();
        if (exact.isPresent()) {
            return exact.get().id();
        }

        var candidates = players.stream().filter(p -> p.name().toLowerCase().startsWith(written.toLowerCase())).toList();

        return candidates.size() == 1 ? candidates.getFirst().id() : null;
    }

    @Serdeable
    public record ScannedMatch(String homeLabel,
                               String awayLabel,
                               @Nullable Long homePlayerId,
                               @Nullable Long awayPlayerId,
                               int homeGoals,
                               int awayGoals) {
    }

    @Serdeable
    public record ImportResult(List<ScannedMatch> matches, List<String> skipped) {
    }

    @Serdeable
    record AiResult(List<AiMatch> matches) {
    }

    @Serdeable
    record AiMatch(String homeLabel, String awayLabel, int homeGoals, int awayGoals) {
    }

    @Serdeable
    record OpenAiResponse(List<Output> output) {
    }

    @Serdeable
    record Output(String type, @Nullable List<Content> content) {
    }

    @Serdeable
    record Content(String type, @Nullable String text) {
    }
}
