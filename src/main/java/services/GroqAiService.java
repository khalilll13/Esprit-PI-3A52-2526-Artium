package services;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import utils.EnvLoader;

public class GroqAiService {

    private static final String GROQ_API_KEY;
    private static final String GROQ_URL;
    private static final String MODEL;

    static {
        GROQ_API_KEY = EnvLoader.get("GROQ_API_KEY");
        GROQ_URL     = EnvLoader.get("GROQ_API_URL");
        MODEL        = EnvLoader.get("GROQ_MODEL");
    }

    public static class AiCommand {
        public String action;
        public String target;
        public String argument;
        public String response;
        public String rawText;
    }

    public static class AiReport {
        public String title;
        public String executiveSummary;
        public List<String> insights = new ArrayList<>();
        public List<String> alerts = new ArrayList<>();
        public List<String> recommendedActions = new ArrayList<>();
    }

    public AiCommand analyze(String spokenText, String contextData) {

        String systemPrompt = """
            Tu es l'assistant IA d'une application JavaFX de gestion d'utilisateurs appelée Artium.
            L'application gère des utilisateurs avec les rôles : admin, artiste, amateur.
            
            Quand l'utilisateur parle, tu dois comprendre son intention et répondre UNIQUEMENT
            en JSON valide avec cette structure exacte, sans aucun texte avant ou après :
            {
              "action": "navigate|search|create|delete|block|activate|stats|report|unknown",
              "target": "users|artistes|amateurs|dashboard|reclamations",
              "argument": "valeur extraite ou chaine vide",
              "response": "réponse courte et naturelle en français"
            }
            
            Règles :
            - "action" doit être exactement l'un de : navigate, search, create, delete, block, activate, stats, report, unknown
            - "target" doit être exactement l'un de : users, artistes, amateurs, dashboard, reclamations
            - "argument" contient le nom/id/email mentionné, sinon chaine vide
            - "response" est une phrase courte confirmant l'action en français
            
            Exemples :
            "montre les artistes" → {"action":"navigate","target":"artistes","argument":"","response":"Affichage de la liste des artistes."}
            "combien d'utilisateurs on a" → {"action":"stats","target":"users","argument":"","response":"Voici les statistiques des utilisateurs."}
            "cherche Ahmed" → {"action":"search","target":"users","argument":"Ahmed","response":"Recherche de Ahmed en cours."}
            "bloque l'utilisateur Ahmed" → {"action":"block","target":"users","argument":"Ahmed","response":"Blocage du compte Ahmed."}
            "crée un artiste nommé Sara" → {"action":"create","target":"artistes","argument":"Sara","response":"Ouverture du formulaire pour Sara."}
            "affiche les réclamations" → {"action":"navigate","target":"reclamations","argument":"","response":"Navigation vers les réclamations."}
            "supprime Ahmed" → {"action":"delete","target":"users","argument":"Ahmed","response":"Suppression du compte Ahmed."}
            "genere un rapport des utilisateurs actifs" → {"action":"report","target":"users","argument":"actifs ce mois","response":"Génération du rapport des utilisateurs actifs."}
            
            Contexte actuel : %s
            
            IMPORTANT : Réponds UNIQUEMENT avec le JSON. Aucun mot avant ou après.
            """.formatted(contextData);

        try {
            // ── Construire le body JSON ───────────────────────────────────────
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("temperature", 0.1);
            body.put("max_tokens", 150);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", spokenText));
            body.put("messages", messages);

            // ── Appel HTTP vers Groq ──────────────────────────────────────────
            URL               url  = new URL(GROQ_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type",  "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + GROQ_API_KEY);

            byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            // ── Lire la réponse ───────────────────────────────────────────────
            int responseCode = conn.getResponseCode();

            InputStream is = (responseCode == 200)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            String rawResponse = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            // ── Bloquer si ce n'est pas un 200 ───────────────────────────────
            if (responseCode != 200) {
                System.err.println("=== GROQ API ERROR ===");
                System.err.println("HTTP Code : " + responseCode);
                System.err.println("Body      : " + rawResponse);
                System.err.println("======================");

                String userMessage;
                if (responseCode == 401) {
                    userMessage = "Clé API invalide ou expirée. Générez-en une nouvelle sur console.groq.com";
                } else if (responseCode == 429) {
                    userMessage = "Limite de requêtes atteinte. Réessayez dans quelques secondes.";
                } else if (responseCode == 400) {
                    userMessage = "Requête invalide (400). Vérifiez le modèle ou le prompt.";
                } else if (responseCode == 503) {
                    userMessage = "Service Groq temporairement indisponible. Réessayez plus tard.";
                } else {
                    userMessage = "Erreur API Groq (" + responseCode + "). Consultez la console.";
                }

                AiCommand fallback = new AiCommand();
                fallback.action   = "unknown";
                fallback.response = userMessage;
                fallback.rawText  = spokenText;
                return fallback;
            }

            // ── Parser le JSON de Groq (uniquement si 200) ───────────────────
            JSONObject groqResponse = new JSONObject(rawResponse);
            String     content      = groqResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();

            // Nettoyer si LLaMA ajoute des ```json ... ```
            if (content.startsWith("```")) {
                content = content.replaceAll("```json", "")
                        .replaceAll("```", "")
                        .trim();
            }

            // ── Extraire uniquement le bloc JSON { ... } ──────────────────────
            int start = content.indexOf('{');
            int end   = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            } else {
                throw new Exception("Pas de JSON dans la réponse : " + content);
            }

            // ── Construire le résultat ────────────────────────────────────────
            JSONObject parsed = new JSONObject(content);
            AiCommand  cmd    = new AiCommand();
            cmd.action    = parsed.optString("action",   "unknown");
            cmd.target    = parsed.optString("target",   "users");
            cmd.argument  = parsed.optString("argument", "");
            cmd.response  = parsed.optString("response", "Commande reçue.");
            cmd.rawText   = spokenText;
            return cmd;

        } catch (Exception e) {
            e.printStackTrace();
            AiCommand fallback = new AiCommand();
            fallback.action   = "unknown";
            fallback.response = "Erreur inattendue : " + e.getMessage();
            fallback.rawText  = spokenText;
            return fallback;
        }
    }

    public AiReport generateUserReport(String reportTopic, String contextData) {
        String systemPrompt = """
            Tu es un assistant de reporting pour une application JavaFX de gestion d'utilisateurs.

            Reponds UNIQUEMENT avec un JSON valide, sans texte avant/apres, avec cette structure :
            {
              "title": "titre court du rapport",
              "executiveSummary": "resume executif en francais (2-3 phrases)",
              "insights": ["insight 1", "insight 2", "insight 3"],
              "alerts": ["alerte 1", "alerte 2"],
              "recommendedActions": ["action 1", "action 2", "action 3"]
            }

            Contraintes :
            - JSON strict
            - max 3 insights
            - max 2 alertes
            - max 3 actions
            - francais professionnel, concis

            Contexte data (JSON): %s
            """.formatted(contextData == null ? "{}" : contextData);

        try {
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("temperature", 0.2);
            body.put("max_tokens", 450);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", reportTopic == null ? "Generer le rapport." : reportTopic));
            body.put("messages", messages);

            URL               url  = new URL(GROQ_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("Content-Type",  "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + GROQ_API_KEY);

            byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
            String rawResponse = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            if (responseCode != 200) {
                throw new Exception("API Groq error " + responseCode + " : " + rawResponse);
            }

            JSONObject groqResponse = new JSONObject(rawResponse);
            String content = groqResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();

            if (content.startsWith("```")) {
                content = content.replaceAll("```json", "")
                        .replaceAll("```", "")
                        .trim();
            }

            int start = content.indexOf('{');
            int end   = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            } else {
                throw new Exception("Pas de JSON dans la reponse : " + content);
            }

            JSONObject parsed = new JSONObject(content);
            AiReport report = new AiReport();
            report.title = parsed.optString("title", "Rapport utilisateurs");
            report.executiveSummary = parsed.optString("executiveSummary", "Resume non disponible.");
            report.insights = toStringList(parsed.optJSONArray("insights"));
            report.alerts = toStringList(parsed.optJSONArray("alerts"));
            report.recommendedActions = toStringList(parsed.optJSONArray("recommendedActions"));
            return report;
        } catch (Exception e) {
            throw new RuntimeException("Generation du rapport IA impossible: " + e.getMessage(), e);
        }
    }

    private List<String> toStringList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }
}