package services;

import org.json.JSONArray;
import org.json.JSONObject;
import utils.EnvLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class BookAutoFillService {
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";
    private static final String API_KEY = EnvLoader.get("GEMINI_API_KEY");

    public static class BookData {
        public String title;
        public String author;
        public String category;
        public String description;

        @Override
        public String toString() {
            return "BookData{" +
                    "title='" + title + '\'' +
                    ", author='" + author + '\'' +
                    ", category='" + category + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }

    public CompletableFuture<BookData> extractBookData(String pdfText) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure latest key is loaded
                utils.EnvLoader.loadEnv(true);
                String currentApiKey = utils.EnvLoader.get("GEMINI_API_KEY");

                if (currentApiKey == null || currentApiKey.isEmpty()) {
                    throw new RuntimeException("GEMINI_API_KEY not found in .env file");
                }

                // Try multiple model aliases to avoid 404s, including the latest ones from documentation
                String[] models = {"gemini-3-flash-preview", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-flash-latest", "gemini-pro"};
                Exception lastEx = null;

                for (String model : models) {
                    try {
                        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
                        return callGemini(pdfText, url, currentApiKey);
                    } catch (Exception e) {
                        lastEx = e;
                        if (!e.getMessage().contains("404")) break;
                        System.err.println("Model " + model + " failed with 404, trying next...");
                    }
                }
                throw lastEx;

            } catch (Exception e) {
                throw new RuntimeException("Auto-fill failed: " + e.getMessage(), e);
            }
        });
    }

    private BookData callGemini(String pdfText, String url, String apiKey) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        String prompt = "Extract the following information from the provided book text and return it ONLY as a JSON object with keys: 'title', 'author', 'category', 'description'.\n\nText:\n" + pdfText;

        JSONObject jsonBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject contentObj = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject partObj = new JSONObject();
        partObj.put("text", prompt);
        parts.put(partObj);
        contentObj.put("parts", parts);
        contents.put(contentObj);
        jsonBody.put("contents", contents);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API Error (" + response.statusCode() + "): " + response.body());
        }

        JSONObject jsonResponse = new JSONObject(response.body());
        String responseText = jsonResponse.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");

        // Clean response if Gemini adds markdown code blocks
        responseText = responseText.replaceAll("```json", "").replaceAll("```", "").trim();

        JSONObject bookJson = new JSONObject(responseText);
        BookData data = new BookData();
        data.title = bookJson.optString("title", "");
        data.author = bookJson.optString("author", "");
        data.category = bookJson.optString("category", "");
        data.description = bookJson.optString("description", "");

        return data;
    }
}
