package utils;

import entities.Musique;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class LanguageDetector {

    // Common words/patterns in different languages
    private static final Map<String, Pattern> LANGUAGE_PATTERNS = new HashMap<>();

    static {
        // French patterns
        LANGUAGE_PATTERNS.put("French", Pattern.compile(
            "\\b(je|tu|il|elle|nous|vous|ils|elles|que|qui|est|sont|avoir|être|pour|dans|avec|sans)\\b",
            Pattern.CASE_INSENSITIVE
        ));

        // Spanish patterns
        LANGUAGE_PATTERNS.put("Spanish", Pattern.compile(
            "\\b(yo|tú|él|ella|nosotros|vosotros|ellos|ellas|que|quien|es|son|para|con|sin|en)\\b",
            Pattern.CASE_INSENSITIVE
        ));

        // German patterns
        LANGUAGE_PATTERNS.put("German", Pattern.compile(
            "\\b(ich|du|er|sie|wir|ihr|das|der|die|nicht|und|oder|aber)\\b",
            Pattern.CASE_INSENSITIVE
        ));

        // Italian patterns
        LANGUAGE_PATTERNS.put("Italian", Pattern.compile(
            "\\b(io|tu|lui|lei|noi|voi|loro|che|chi|è|sono|per|con|senza|in)\\b",
            Pattern.CASE_INSENSITIVE
        ));

        // Portuguese patterns
        LANGUAGE_PATTERNS.put("Portuguese", Pattern.compile(
            "\\b(eu|tu|ele|ela|nós|vós|eles|elas|que|quem|é|são|para|com|sem|em)\\b",
            Pattern.CASE_INSENSITIVE
        ));
    }

    // Common genre-language mappings
    private static final Map<String, String> GENRE_LANGUAGE_MAP = new HashMap<>();

    static {
        // Genres typically associated with French
        GENRE_LANGUAGE_MAP.put("chanson", "French");
        GENRE_LANGUAGE_MAP.put("french pop", "French");
        GENRE_LANGUAGE_MAP.put("french hip hop", "French");
        GENRE_LANGUAGE_MAP.put("french rap", "French");

        // Spanish genres
        GENRE_LANGUAGE_MAP.put("reggaeton", "Spanish");
        GENRE_LANGUAGE_MAP.put("flamenco", "Spanish");
        GENRE_LANGUAGE_MAP.put("latin", "Spanish");
        GENRE_LANGUAGE_MAP.put("spanish hip hop", "Spanish");

        // German genres
        GENRE_LANGUAGE_MAP.put("deutsche", "German");
        GENRE_LANGUAGE_MAP.put("german hip hop", "German");

        // Italian genres
        GENRE_LANGUAGE_MAP.put("italian pop", "Italian");

        // Portuguese genres
        GENRE_LANGUAGE_MAP.put("bossa nova", "Portuguese");
        GENRE_LANGUAGE_MAP.put("samba", "Portuguese");
        GENRE_LANGUAGE_MAP.put("forró", "Portuguese");
    }

    /**
     * Detects the language of a song based on its metadata
     * @param track the Musique object containing title, description, and genre
     * @return the detected language code, defaults to "English" if not detected
     */
    public static String detectLanguage(Musique track) {
        if (track == null) {
            return "English";
        }

        // First, try to detect from genre
        String detectedFromGenre = detectFromGenre(track.getGenre());
        if (detectedFromGenre != null) {
            return detectedFromGenre;
        }

        // Then try to detect from title and description
        String textToAnalyze = buildTextToAnalyze(track.getTitre(), track.getDescription());
        String detectedFromText = detectFromTextPatterns(textToAnalyze);
        if (detectedFromText != null) {
            return detectedFromText;
        }

        // Default to English
        return "English";
    }

    private static String detectFromGenre(String genre) {
        if (genre == null || genre.isBlank()) {
            return null;
        }

        String genreLower = genre.toLowerCase();
        for (Map.Entry<String, String> entry : GENRE_LANGUAGE_MAP.entrySet()) {
            if (genreLower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String detectFromTextPatterns(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Map<String, Integer> scores = new HashMap<>();

        for (Map.Entry<String, Pattern> entry : LANGUAGE_PATTERNS.entrySet()) {
            int matches = countMatches(entry.getValue(), text);
            if (matches > 0) {
                scores.put(entry.getKey(), matches);
            }
        }

        // Return the language with the highest score
        if (!scores.isEmpty()) {
            return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }

        return null;
    }

    private static int countMatches(Pattern pattern, String text) {
        int count = 0;
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String buildTextToAnalyze(String titre, String description) {
        StringBuilder sb = new StringBuilder();
        if (titre != null && !titre.isBlank()) {
            sb.append(titre).append(" ");
        }
        if (description != null && !description.isBlank()) {
            sb.append(description).append(" ");
        }
        return sb.toString();
    }
}


