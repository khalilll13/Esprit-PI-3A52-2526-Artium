package services;

import entities.Musique;
import utils.LanguageDetector;
import utils.AudioAnalyzer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import utils.TranscriptionService;
import utils.EnvLoader;

public class OpenRouterLyricsService {
    private static final String DEFAULT_MODEL = "llama-3.1-8b-instant";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_CONFIG_PATH = "config/openrouter.properties";
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

    private final HttpClient httpClient;

    public OpenRouterLyricsService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    public OpenRouterLyricsService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String generateLyrics(Musique track) {
        if (track != null && track.getTitre() != null && !track.getTitre().isBlank()) {
            String webLyrics = searchWebForLyrics(track.getTitre(), track.getDescription());
            if (webLyrics != null && !webLyrics.isBlank()) {
                System.out.println("Paroles trouvées sur lrclib.net !");
                return webLyrics.trim();
            }
        }

        System.out.println("Paroles non trouvées sur le web, utilisation de l'IA (Groq/OpenRouter)...");
        String apiKey = resolveApiKey();
        String model = resolveModel();
        String prompt = buildPrompt(track);
        String payload = buildRequestBody(model, prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "http://localhost")
                .header("X-Title", "Esprit PIDEV Artium")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La génération des paroles a été interrompue.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de contacter OpenRouter.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(extractErrorMessage(response.body(), response.statusCode()));
        }

        String content = extractContent(response.body());
        if (content == null || content.isBlank()) {
            String responseBody = response.body();
            System.err.println("API RAW RESPONSE: " + responseBody);
            throw new IllegalStateException("L'IA n'a pas renvoyé de paroles. Réponse: " + (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody));
        }
        return content.trim();
    }

    private String resolveApiKey() {
        String apiKey = System.getProperty("groq.apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = EnvLoader.get("GROQ_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = EnvLoader.get("OPENROUTER_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = EnvLoader.get("groq.apiKey");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = EnvLoader.get("openrouter.apiKey");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Clé API manquante. Ajoutez GROQ_API_KEY ou OPENROUTER_API_KEY dans le fichier .env.");
        }
        return apiKey.trim();
    }

    private String resolveModel() {
        String model = System.getProperty("groq.model");
        if (model == null || model.isBlank()) {
            model = EnvLoader.get("GROQ_MODEL");
        }
        if (model == null || model.isBlank()) {
            model = EnvLoader.get("OPENROUTER_MODEL");
        }
        if (model == null || model.isBlank()) {
            model = EnvLoader.get("groq.model");
        }
        if (model == null || model.isBlank()) {
            model = EnvLoader.get("openrouter.model");
        }
        if (model == null || model.isBlank() || model.contains("free")) {
            model = DEFAULT_MODEL;
        }
        return model.trim();
    }

    private Properties loadConfig() {
        // Retourne des propriétés vides par compatibilité si nécessaire
        return new Properties();
    }

    private String buildPrompt(Musique track) {
        String title = safe(track != null ? track.getTitre() : null, "Morceau sans titre");
        String genre = safe(track != null ? track.getGenre() : null, "inconnu");
        String description = safe(track != null ? track.getDescription() : null, "Aucune description disponible.");
        String language = LanguageDetector.detectLanguage(track);

        return "You are an expert music lyrics generator and database. Your task is to provide lyrics for the song titled: \"" + title + "\".\n\n"
                + "Context: The genre is " + genre + " and theme is " + description + ".\n"
                + "Language: " + language + ".\n\n"
                + "CRITICAL INSTRUCTIONS:\n"
                + "1. First, try to provide the REAL, actual lyrics if this is a known song.\n"
                + "2. If you do not know the song, you MUST write high-quality ORIGINAL lyrics that fit the title and genre.\n"
                + "3. You must output ONLY the raw lyrics. NEVER include introductory text, apologies, metadata, or explanations.\n"
                + "4. NEVER say 'Here are the lyrics', 'I couldn't find', or 'I found a song'.\n"
                + "5. Start immediately with the first line of the song.\n\n"
                + "Lyrics:";
    }

    private String fetchFromLrcLib(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Artium-App/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                try {
                    if (body.trim().startsWith("[")) {
                        org.json.JSONArray array = new org.json.JSONArray(body);
                        for (int i = 0; i < array.length(); i++) {
                            org.json.JSONObject trackObj = array.getJSONObject(i);
                            if (trackObj.has("plainLyrics") && !trackObj.isNull("plainLyrics")) {
                                String lyrics = trackObj.getString("plainLyrics");
                                if (lyrics != null && !lyrics.isBlank()) {
                                    return lyrics;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("JSON parse error for lrclib: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Web search failed for URL " + url + ": " + e.getMessage());
        }
        return null;
    }

    private String searchWebForLyrics(String title, String description) {
        if (title == null || title.isBlank() || title.equalsIgnoreCase("Morceau sans titre")) {
            return null;
        }
        
        String encodedTitle = java.net.URLEncoder.encode(title.trim(), StandardCharsets.UTF_8);
        
        if (description != null && !description.isBlank() && description.length() <= 50 && !description.equalsIgnoreCase("Aucune description disponible.")) {
            String encodedDesc = java.net.URLEncoder.encode(description.trim(), StandardCharsets.UTF_8);
            String lyrics = fetchFromLrcLib("https://lrclib.net/api/search?track_name=" + encodedTitle + "&artist_name=" + encodedDesc);
            if (lyrics != null) return lyrics;
            
            lyrics = fetchFromLrcLib("https://lrclib.net/api/search?q=" + encodedTitle + "+" + encodedDesc);
            if (lyrics != null) return lyrics;
        }

        String lyrics = fetchFromLrcLib("https://lrclib.net/api/search?track_name=" + encodedTitle);
        if (lyrics != null) return lyrics;
        
        return fetchFromLrcLib("https://lrclib.net/api/search?q=" + encodedTitle);
    }

    private String buildRequestBody(String model, String prompt) {
        return "{" +
                "\"model\":\"" + jsonEscape(model) + "\"," +
                "\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"You are an expert music lyrics database. Provide only the lyrics without formatting or explanations.\"}," +
                "{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}" +
                "]," +
                "\"temperature\":0.5," +
                "\"max_tokens\":1000," +
                "\"top_p\":0.95," +
                "\"stream\":false" +
                "}";
    }

    private String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(responseBody);
            if (json.has("choices")) {
                org.json.JSONArray choices = json.getJSONArray("choices");
                if (choices.length() > 0) {
                    org.json.JSONObject choice = choices.getJSONObject(0);
                    if (choice.has("message")) {
                        org.json.JSONObject message = choice.getJSONObject("message");
                        if (message.has("content")) {
                            String content = message.getString("content");
                            if (content != null && !content.isBlank()) {
                                return content;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Groq response JSON: " + e.getMessage());
        }
        return null;
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(responseBody);
                if (json.has("error")) {
                    Object errObj = json.get("error");
                    if (errObj instanceof org.json.JSONObject) {
                        org.json.JSONObject errJson = (org.json.JSONObject) errObj;
                        if (errJson.has("message")) {
                            return errJson.getString("message");
                        }
                    } else if (errObj instanceof String) {
                        return (String) errObj;
                    }
                }
            } catch (Exception e) {
                // Continue to content fallback
            }

            String content = extractContent(responseBody);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        return "OpenRouter/Groq a retourné une erreur (HTTP " + statusCode + ").";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private String unescapeJson(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                builder.append(c);
                continue;
            }

            char next = value.charAt(++i);
            switch (next) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        String hex = value.substring(i + 1, i + 5);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException ex) {
                            builder.append('\\').append('u').append(hex);
                            i += 4;
                        }
                    } else {
                        builder.append('\\').append('u');
                    }
                }
                default -> builder.append(next);
            }
        }
        return builder.toString();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * Returns the verbatim transcript of the track's audio using local transcription (Whisper).
     * If transcription fails, returns null.
     */
    public String transcribeTrack(Musique track) {
        if (track == null) return null;
        String audio = track.getAudio();
        if (audio == null || audio.isBlank()) return null;
        return TranscriptionService.transcribe(audio);
    }
}
