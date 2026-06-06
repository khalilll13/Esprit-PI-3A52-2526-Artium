package services;

import entities.Reclamation;
import entities.Reponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import utils.EnvLoader;

public class OpenRouterReclamationReplyService {

    private static final Logger LOGGER =
            Logger.getLogger(OpenRouterReclamationReplyService.class.getName());

    // ✅ OpenRouter endpoint
    private static final String API_URL =
            "https://openrouter.ai/api/v1/chat/completions";

    // 🔑 Chargé depuis le fichier .env
    private static final String API_KEY =
            EnvLoader.get("OPENROUTER_RECLAMATION_API_KEY");

    private final HttpClient httpClient;

    public OpenRouterReclamationReplyService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    // ================= MAIN =================
    public String generateReplySuggestion(Reclamation reclamation, List<Reponse> history) {

        if (reclamation == null || reclamation.getTexte() == null || reclamation.getTexte().isBlank()) {
            return fallback();
        }

        try {
            String prompt = buildPrompt(reclamation);

            String jsonBody = buildPayload(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "http://localhost")
                    .header("X-Title", "ReclamationApp")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            LOGGER.info("Status: " + response.statusCode());
            LOGGER.info("Response: " + response.body());

            if (response.statusCode() != 200) {
                return fallback();
            }

            String result = extract(response.body());

            if (result == null || result.isBlank()) {
                return fallback();
            }

            return clean(result);

        } catch (IOException | InterruptedException e) {
            LOGGER.severe("OpenRouter error: " + e.getMessage());
            return fallback();
        }
    }

    // ================= PROMPT =================
    private String buildPrompt(Reclamation r) {
        return """
                Tu es un assistant client professionnel.
                Réponds en français, de manière courte, empathique et claire.
                Limite ta réponse à 300 caractères maximum.

                Contexte:
                - Type de réclamation: """ + (r.getType() != null ? r.getType() : "générale") + """

                - Statut actuel: """ + (r.getStatut() != null ? r.getStatut() : "nouvelle") + """

                Message du client:
                """ + r.getTexte() + """

                Générez une réponse unique et adaptée au problème spécifique:
                """;
    }

    // ================= JSON BODY =================
    private String buildPayload(String prompt) {
        return "{"
                + "\"model\": \"openai/gpt-3.5-turbo\","
                + "\"messages\": ["
                + "{"
                + "\"role\": \"system\","
                + "\"content\": \"Tu es un assistant professionnel pour service client.\""
                + "},"
                + "{"
                + "\"role\": \"user\","
                + "\"content\": \"" + escape(prompt) + "\""
                + "}"
                + "],"
                + "\"temperature\": 0.7,"
                + "\"max_tokens\": 300"
                + "}";
    }

    // ================= RESPONSE PARSING =================
    private String extract(String json) {
        try {
            int index = json.indexOf("\"content\"");
            if (index == -1) return "";

            int start = json.indexOf("\"", index + 10);
            int end = json.indexOf("\"", start + 1);

            return json.substring(start + 1, end);
        } catch (Exception e) {
            return "";
        }
    }

    // ================= CLEAN =================
    private String clean(String text) {
        return text.replace("\\n", " ").trim();
    }

    // ================= FALLBACK =================
    private String fallback() {
        return "Merci pour votre message. Nous avons bien reçu votre réclamation et nous la traitons dans les plus brefs délais.";
    }

    // ================= ESCAPE =================
    private String escape(String text) {
        return text.replace("\"", "\\\"");
    }
}