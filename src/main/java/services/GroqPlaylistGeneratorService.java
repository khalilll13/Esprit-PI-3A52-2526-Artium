package services;

import entities.Musique;
import entities.Playlist;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import utils.EnvLoader;

/**
 * Service to generate playlists using Groq AI API based on user prompts.
 * Exclusive to amateur users who want to discover music based on themes.
 */
public class GroqPlaylistGeneratorService {
    private static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "mixtral-8x7b-32768";
    private static final String DEFAULT_CONFIG_PATH = "config/openrouter.properties";
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

    private final HttpClient httpClient;
    private final MusiqueService musiqueService;
    private final PlaylistService playlistService;

    public GroqPlaylistGeneratorService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    public GroqPlaylistGeneratorService(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.musiqueService = new MusiqueService();
        this.playlistService = new PlaylistService();
    }

    /**
     * Generates a playlist based on a user prompt and saves it to the database.
     *
     * @param prompt User's request for playlist generation (e.g., "Upbeat songs for workout")
     * @param userId The ID of the amateur user
     * @return The created Playlist entity
     * @throws IllegalStateException if API communication fails
     */
    public Playlist generatePlaylistFromPrompt(String prompt, Integer userId) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Le prompt ne peut pas être vide.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("ID utilisateur requis pour créer une playlist.");
        }

        String apiKey = resolveGroqApiKey();
        String model = resolveModel();

        // Get streaming context - recent music tracks in system
        String contextPrompt = buildContextualPrompt(prompt);
        String payload = buildRequestBody(model, contextPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_ENDPOINT))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La génération de playlist a été interrompue.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de contacter Groq API.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(extractErrorMessage(response.body(), response.statusCode()));
        }

        String aiResponse = extractContent(response.body());
        if (aiResponse == null || aiResponse.isBlank()) {
            throw new IllegalStateException("Groq n'a pas renvoyé de recommandations valides.");
        }

        // Parse the AI response and create playlist
        return parseAIResponseAndCreatePlaylist(aiResponse, prompt, userId);
    }

    /**
     * Parses AI response and matches songs from the database to create a playlist.
     */
    private Playlist parseAIResponseAndCreatePlaylist(String aiResponse, String originalPrompt, Integer userId) {
        try {
            // Extract title and suggested song titles from AI response
            String[] lines = aiResponse.split("\n");
            String playlistName = generatePlaylistName(originalPrompt);
            List<String> suggestedTitles = new ArrayList<>();
            
            if (lines.length > 0) {
                String firstLine = lines[0].trim();
                firstLine = firstLine.replaceAll("^(\\*\\*.*?\\*\\*|#+)\\s*", "").replaceAll("[\"']", "");
                if (!firstLine.isBlank()) {
                    playlistName = "🎵 " + firstLine.substring(0, 1).toUpperCase() + firstLine.substring(1);
                }
                
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.matches("^\\d+\\.\\s+.*")) {
                        line = line.replaceFirst("^\\d+\\.\\s+", "");
                    }
                    line = line.replaceFirst("^[\\-\\*\u2022]\\s+", "");
                    if (!line.isBlank() && line.length() > 2) {
                        suggestedTitles.add(line);
                    }
                }
            }

            // Get all available songs
            List<Musique> allMusiques = musiqueService.getAll();

            // Match suggested titles with available songs
            List<Musique> selectedMusiques = matchSongsToSuggestions(suggestedTitles, allMusiques);

            // Always add songs by genre/mood keywords to enrich the playlist
            List<Musique> keywordMatches = selectSongsByKeywords(originalPrompt, allMusiques, selectedMusiques);
            selectedMusiques.addAll(keywordMatches);

            // Determine a random number of songs depending on available musics (e.g. between 5 and 15)
            if (selectedMusiques.size() > 3) {
                java.util.Collections.shuffle(selectedMusiques);
                int minSongs = Math.min(5, selectedMusiques.size());
                int maxSongs = Math.min(15, selectedMusiques.size());
                int randomCount = minSongs + new java.util.Random().nextInt(maxSongs - minSongs + 1);
                selectedMusiques = new ArrayList<>(selectedMusiques.subList(0, randomCount));
            }

            // Create playlist entity
            Playlist playlist = new Playlist();
            playlist.setNom(playlistName);
            playlist.setDescription("Playlist générée par IA basée sur: " + originalPrompt);
            playlist.setDateCreation(LocalDate.now());
            playlist.setUserId(userId);
            playlist.setMusiques(selectedMusiques);

            // Save to database
            playlistService.add(playlist);

            return playlist;

        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors de la création de la playlist: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy method: Extracts suggested song titles from AI response.
     */
    private List<String> extractSuggestedTitles(String aiResponse) {
        return new ArrayList<>(); // Unused, logic moved to parseAIResponseAndCreatePlaylist
    }

    /**
     * Matches suggested titles with available songs in the database.
     */
    private List<Musique> matchSongsToSuggestions(List<String> suggestedTitles, List<Musique> allMusiques) {
        List<Musique> matched = new ArrayList<>();

        for (String suggestion : suggestedTitles) {
            for (Musique musique : allMusiques) {
                if (musique.getTitre() != null &&
                    musique.getTitre().toLowerCase().contains(suggestion.toLowerCase()) &&
                    !matched.contains(musique)) {
                    matched.add(musique);
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * Selects all songs matching keyword matching on genre and description.
     */
    private List<Musique> selectSongsByKeywords(String prompt, List<Musique> allMusiques, List<Musique> alreadySelected) {
        List<Musique> matched = new ArrayList<>();
        String[] keywords = extractKeywords(prompt);

        for (Musique musique : allMusiques) {
            if (alreadySelected.contains(musique)) continue;

            String musicDescription = (musique.getDescription() != null ? musique.getDescription() + " " : "") +
                                     (musique.getGenre() != null ? musique.getGenre() : "");

            for (String keyword : keywords) {
                if (keyword.length() > 2 && musicDescription.toLowerCase().contains(keyword.toLowerCase())) {
                    matched.add(musique);
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * Extracts keywords from the user's prompt.
     */
    private String[] extractKeywords(String prompt) {
        // Remove common words and split
        String cleaned = prompt.toLowerCase()
                              .replaceAll("\\b(pour|une|des|le|la|les|et|ou|de|du|un)\\b", " ")
                              .replaceAll("[^a-zàâäëéèêùûüœæ0-9\\s]", "");
        return cleaned.split("\\s+");
    }

    /**
     * Generates a playlist name based on the prompt.
     */
    private String generatePlaylistName(String prompt) {
        // Limit to first 50 characters and capitalize
        String name = prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt;
        return "🎵 " + name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Builds a contextual prompt that includes information about available songs.
     */
    private String buildContextualPrompt(String userPrompt) {
        return "Tu es un expert en recommandation musicale. Un utilisateur te demande: \"" + userPrompt + "\"\n\n" +
               "Recommande une liste de chansons correspondant à cette demande.\n" +
               "RÈGLES IMPORTANTES :\n" +
               "1. La PREMIÈRE ligne de ta réponse DOIT être un titre court et accrocheur pour cette playlist (sans le mot 'Playlist', ex: 'Vibes d'Été').\n" +
               "2. Les lignes SUIVANTES doivent être UNIQUEMENT la liste des chansons (Titre - Artiste), une suggestion par ligne.\n" +
               "3. Ne fournis AUCUNE autre explication ou introduction.";
    }

    private String buildRequestBody(String model, String prompt) {
        return "{" +
                "\"model\":\"" + jsonEscape(model) + "\"," +
                "\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"Tu es un assistant de recommandation musicale spécialisé pour les mélomanes.\"}," +
                "{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}" +
                "]," +
                "\"temperature\":0.7," +
                "\"max_tokens\":500," +
                "\"top_p\":0.9," +
                "\"stream\":false" +
                "}";
    }

    private String resolveGroqApiKey() {
        String apiKey = System.getProperty("groq.apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = EnvLoader.get("GROQ_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = EnvLoader.get("groq.apiKey");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Clé Groq manquante. Ajoutez GROQ_API_KEY dans le fichier .env.");
        }
        return apiKey.trim();
    }

    private String resolveModel() {
        String model = System.getProperty("groq.model");
        if (model == null || model.isBlank()) {
            model = EnvLoader.get("GROQ_MODEL");
        }
        if (model == null || model.isBlank()) {
            model = EnvLoader.get("groq.model");
        }
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        return model.trim();
    }

    private Properties loadConfig() {
        return new Properties();
    }

    private String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        Matcher matcher = CONTENT_PATTERN.matcher(responseBody);
        while (matcher.find()) {
            String raw = matcher.group(1);
            String unescaped = unescapeJson(raw);
            if (unescaped != null && !unescaped.isBlank()) {
                return unescaped;
            }
        }
        return null;
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (responseBody != null && !responseBody.isBlank()) {
            Matcher messageMatcher = MESSAGE_PATTERN.matcher(responseBody);
            if (messageMatcher.find()) {
                String message = unescapeJson(messageMatcher.group(1));
                if (message != null && !message.isBlank()) {
                    return message;
                }
            }

            String content = extractContent(responseBody);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        return "Groq a retourné une erreur (HTTP " + statusCode + ").";
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
}

