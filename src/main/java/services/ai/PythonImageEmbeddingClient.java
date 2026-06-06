package services.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PythonImageEmbeddingClient {

    private static final String DEFAULT_API_URL = "https://goggles-drown-skinhead.ngrok-free.dev/embed";

    private static final String EMBEDDING_KEY = "\"embedding\"";

    private final HttpClient httpClient;
    private final String apiUrl;

    public PythonImageEmbeddingClient() {
        this(System.getProperty("artium.embedding.api.url", DEFAULT_API_URL));
    }

    public PythonImageEmbeddingClient(String apiUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.apiUrl = apiUrl;
    }

    public String generateImageEmbeddingJson(String imagePath) throws IOException, InterruptedException {
        if (imagePath == null || imagePath.isBlank()) {
            throw new IOException("Image path is required to generate embedding.");
        }

        Path path = Path.of(imagePath);
        if (!Files.exists(path)) {
            throw new IOException("Image file not found: " + imagePath);
        }

        String boundary = "----ArtiumBoundary" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(path, boundary);

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Embedding API returned status " + response.statusCode() + ": " + response.body());
        }

        return extractEmbeddingArray(response.body());
    }

    private byte[] buildMultipartBody(Path imagePath, String boundary) throws IOException {
        String filename = imagePath.getFileName().toString();
        String contentType = Files.probeContentType(imagePath);
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        byte[] fileBytes = Files.readAllBytes(imagePath);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(fileBytes);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private String extractEmbeddingArray(String responseBody) throws IOException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IOException("Embedding API returned an empty body.");
        }

        int keyIndex = responseBody.indexOf(EMBEDDING_KEY);
        if (keyIndex < 0) {
            throw new IOException("Embedding key not found in API response.");
        }

        int colonIndex = responseBody.indexOf(':', keyIndex);
        int arrayStart = responseBody.indexOf('[', colonIndex);
        if (colonIndex < 0 || arrayStart < 0) {
            throw new IOException("Embedding array start not found in API response.");
        }

        int depth = 0;
        for (int i = arrayStart; i < responseBody.length(); i++) {
            char current = responseBody.charAt(i);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return responseBody.substring(arrayStart, i + 1);
                }
            }
        }

        throw new IOException("Embedding array end not found in API response.");
    }
}

