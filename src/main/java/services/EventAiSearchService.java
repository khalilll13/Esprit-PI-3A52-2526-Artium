package services;

import entities.Evenement;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import utils.EnvLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EventAiSearchService {

    private static final String DEFAULT_HOST = "http://localhost:11434";
    private static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text:latest";
    private static final String DEFAULT_CONFIG_FILE = "config/ollama.local.properties";

    private static final String HOST_PROPERTY = "ollama.host";
    private static final String EMBEDDING_MODEL_PROPERTY = "ollama.embedding.model";
    private static final String CONFIG_PATH_PROPERTY = "ollama.configPath";

    private static final String HOST_ENV = "OLLAMA_HOST";
    private static final String EMBEDDING_MODEL_ENV = "OLLAMA_EMBEDDING_MODEL";
    private static final String CONFIG_PATH_ENV = "OLLAMA_CONFIG_PATH";

    private final HttpClient httpClient;
    private final SearchConfig config;
    private final Map<String, double[]> embeddingCache = new ConcurrentHashMap<>();
    private final Map<String, double[]> eventEmbeddingCache = new ConcurrentHashMap<>();

    public EventAiSearchService() {
        this.httpClient = HttpClient.newBuilder().build();
        this.config = loadConfig();
    }

    public List<RankedEvent> rankEvents(List<Evenement> events, String query) {
        List<Evenement> safeEvents = events == null ? List.of() : events;
        String normalizedQuery = normalize(query);

        if (safeEvents.isEmpty()) {
            return List.of();
        }

        if (normalizedQuery.isBlank()) {
            return safeEvents.stream()
                    .map(event -> new RankedEvent(event, Double.NaN))
                    .sorted(defaultComparator())
                    .collect(Collectors.toList());
        }

        double[] queryEmbedding = getEmbeddingOrNull(normalizedQuery);
        List<RankedEvent> ranked = new ArrayList<>(safeEvents.size());

        for (Evenement event : safeEvents) {
            double score = queryEmbedding == null
                    ? heuristicScore(normalizedQuery, event)
                    : scoreAgainstEmbedding(normalizedQuery, queryEmbedding, event);
            ranked.add(new RankedEvent(event, score));
        }

        ranked.sort(Comparator
                .comparingDouble(RankedEvent::score).reversed()
                .thenComparing(rankedEvent -> safeDateKey(rankedEvent.event()), Comparator.reverseOrder())
                .thenComparing(rankedEvent -> safeText(rankedEvent.event().getTitre())));

        return ranked;
    }

    private double scoreAgainstEmbedding(String query, double[] queryEmbedding, Evenement event) {
        double[] eventEmbedding = getEventEmbedding(event);
        if (queryEmbedding == null || eventEmbedding == null) {
            return heuristicScore(query, event);
        }

        double cosine = cosineSimilarity(queryEmbedding, eventEmbedding);
        if (Double.isNaN(cosine) || Double.isInfinite(cosine)) {
            return heuristicScore(query, event);
        }

        double normalized = Math.max(0d, (cosine + 1d) / 2d);
        return roundScore(normalized * 10d);
    }

    private double[] getEventEmbedding(Evenement event) {
        if (event == null) {
            return null;
        }

        String cacheKey = event.getId() == null
                ? buildEventText(event)
                : event.getId() + "::" + buildEventText(event);

        return eventEmbeddingCache.computeIfAbsent(cacheKey, key -> getEmbeddingOrNull(buildEventText(event)));
    }

    private double[] getEmbeddingOrNull(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return null;
        }

        double[] cached = embeddingCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        try {
            double[] embedding = fetchEmbedding(normalized);
            if (embedding != null && embedding.length > 0) {
                embeddingCache.put(normalized, embedding);
            }
            return embedding;
        } catch (Exception e) {
            return null;
        }
    }

    private double[] fetchEmbedding(String input) throws IOException, InterruptedException {
        String payload = "{\"model\":\"" + escapeJson(config.embeddingModel()) + "\",\"prompt\":\"" + escapeJson(input) + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.host() + "/api/embeddings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }

        return parseEmbedding(response.body());
    }

    private double[] parseEmbedding(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        int keyIndex = body.indexOf("\"embedding\"");
        if (keyIndex < 0) {
            return null;
        }

        int arrayStart = body.indexOf('[', keyIndex);
        int arrayEnd = findMatchingBracket(body, arrayStart);
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            return null;
        }

        String rawArray = body.substring(arrayStart + 1, arrayEnd).trim();
        if (rawArray.isBlank()) {
            return null;
        }

        String[] parts = rawArray.split(",");
        double[] embedding = new double[parts.length];
        int count = 0;

        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }

            try {
                embedding[count++] = Double.parseDouble(token);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return count == embedding.length ? embedding : Arrays.copyOf(embedding, count);
    }

    private int findMatchingBracket(String text, int openIndex) {
        if (openIndex < 0 || openIndex >= text.length() || text.charAt(openIndex) != '[') {
            return -1;
        }

        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private double heuristicScore(String query, Evenement event) {
        String eventText = buildEventText(event);
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return 0d;
        }

        Set<String> eventTokens = tokenize(eventText);
        if (eventTokens.isEmpty()) {
            return 0d;
        }

        long overlap = queryTokens.stream().filter(eventTokens::contains).count();
        double ratio = (double) overlap / (double) Math.max(queryTokens.size(), 1);
        double titleBonus = fuzzyContains(safeText(event == null ? null : event.getTitre()), query) ? 0.25d : 0d;
        double typeBonus = fuzzyContains(safeText(event == null ? null : event.getType()), query) ? 0.15d : 0d;
        return roundScore(Math.min(1d, ratio + titleBonus + typeBonus) * 10d);
    }

    private boolean fuzzyContains(String source, String query) {
        return !source.isBlank() && !query.isBlank() && source.contains(query);
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }

        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9à-ÿ]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return Set.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String buildEventText(Evenement event) {
        if (event == null) {
            return "";
        }

        return String.join(" ",
                safeText(event.getTitre()),
                safeText(event.getDescription()),
                safeText(event.getType()),
                safeText(event.getStatut()));
    }

    private double cosineSimilarity(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return Double.NaN;
        }

        int size = Math.min(left.length, right.length);
        double dot = 0d;
        double leftNorm = 0d;
        double rightNorm = 0d;

        for (int i = 0; i < size; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0d || rightNorm == 0d) {
            return Double.NaN;
        }

        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private Comparator<RankedEvent> defaultComparator() {
        return Comparator
                .comparing((RankedEvent rankedEvent) -> safeDateKey(rankedEvent.event()), Comparator.reverseOrder())
                .thenComparing(rankedEvent -> safeText(rankedEvent.event().getTitre()));
    }

    private String safeDateKey(Evenement event) {
        if (event == null) {
            return "";
        }
        if (event.getDateDebut() != null) {
            return event.getDateDebut().toString();
        }
        if (event.getDateCreation() != null) {
            return event.getDateCreation().toString();
        }
        return event.getId() == null ? "" : String.format(Locale.ROOT, "%010d", event.getId());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private double roundScore(double value) {
        return Math.max(0d, Math.min(10d, Math.round(value * 10d) / 10d));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
    }

    private SearchConfig loadConfig() {
        String host = firstNonBlank(
                System.getProperty(HOST_PROPERTY),
                EnvLoader.get("OLLAMA_HOST"),
                System.getenv(HOST_ENV),
                DEFAULT_HOST);

        String embeddingModel = firstNonBlank(
                System.getProperty(EMBEDDING_MODEL_PROPERTY),
                EnvLoader.get("OLLAMA_EMBEDDING_MODEL"),
                System.getenv(EMBEDDING_MODEL_ENV),
                DEFAULT_EMBEDDING_MODEL);

        return new SearchConfig(host, embeddingModel);
    }

    private Path resolveConfigPath() {
        String systemPath = System.getProperty(CONFIG_PATH_PROPERTY);
        if (systemPath != null && !systemPath.isBlank()) {
            return Path.of(systemPath);
        }

        String envPath = System.getenv(CONFIG_PATH_ENV);
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath);
        }

        return Path.of(DEFAULT_CONFIG_FILE);
    }

    @SafeVarargs
    private <T> T firstNonBlank(T... values) {
        for (T value : values) {
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return value;
            }
        }
        return values.length == 0 ? null : values[values.length - 1];
    }

    public record RankedEvent(Evenement event, double scoreOutOf10) {
        public double score() {
            return scoreOutOf10;
        }
    }

    private record SearchConfig(String host, String embeddingModel) {
        private SearchConfig {
            host = Objects.requireNonNullElse(host, DEFAULT_HOST).trim();
            embeddingModel = Objects.requireNonNullElse(embeddingModel, DEFAULT_EMBEDDING_MODEL).trim();
            if (host.isBlank()) {
                host = DEFAULT_HOST;
            }
            if (embeddingModel.isBlank()) {
                embeddingModel = DEFAULT_EMBEDDING_MODEL;
            }
        }
    }
}


