package utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GoogleAuthService {

    private static final String REDIRECT_URI = "http://localhost:9876/callback";
    private static final int PORT = 9876;

    // Chargement sécurisé depuis le fichier .env
    private static final String CLIENT_ID;
    private static final String CLIENT_SECRET;

    static {
        CLIENT_ID     = EnvLoader.get("GOOGLE_CLIENT_ID");
        CLIENT_SECRET = EnvLoader.get("GOOGLE_CLIENT_SECRET");
    }

    public record GoogleUser(String email, String name, String googleId) {}

    public static void signIn(Consumer<GoogleUser> onSuccess, Consumer<String> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Construire l'URL Google Login
                String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?"
                        + "client_id="     + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                        + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                        + "&response_type=code"
                        + "&scope="        + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)
                        + "&access_type=offline";

                // 2. Ouvrir le navigateur système
                Desktop.getDesktop().browse(new URI(authUrl));

                // 3. Serveur local pour recevoir le callback Google
                CompletableFuture<String> codeFuture = new CompletableFuture<>();
                HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
                server.createContext("/callback", exchange -> {
                    String query = exchange.getRequestURI().getQuery();
                    String code  = extractParam(query, "code");
                    String error = extractParam(query, "error");

                    String html = code != null
                            ? "<html><body style='font-family:sans-serif;text-align:center;padding:60px'><h2>✅ Connexion réussie !</h2><p>Vous pouvez fermer cette fenêtre.</p></body></html>"
                            : "<html><body style='font-family:sans-serif;text-align:center;padding:60px'><h2>❌ Erreur</h2><p>Veuillez réessayer.</p></body></html>";

                    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.getResponseBody().close();
                    server.stop(0);

                    if (code != null) codeFuture.complete(code);
                    else codeFuture.completeExceptionally(new Exception(error));
                });
                server.start();

                // 4. Attendre le code (timeout 2 minutes)
                String code = codeFuture.get(120, TimeUnit.SECONDS);

                // 5. Échanger le code contre un token
                String tokenJson = exchangeCodeForToken(code);
                JsonObject tokenObj = JsonParser.parseString(tokenJson).getAsJsonObject();
                String accessToken = tokenObj.get("access_token").getAsString();

                // 6. Récupérer les infos utilisateur
                String userJson = fetchUserInfo(accessToken);
                JsonObject userObj = JsonParser.parseString(userJson).getAsJsonObject();

                GoogleUser googleUser = new GoogleUser(
                        userObj.get("email").getAsString(),
                        userObj.get("name").getAsString(),
                        userObj.get("sub").getAsString()
                );

                // 7. Retourner sur le thread JavaFX
                javafx.application.Platform.runLater(() -> onSuccess.accept(googleUser));

            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        onError.accept("Erreur Google : " + e.getMessage()));
            }
        });
    }

    private static String exchangeCodeForToken(String code) throws Exception {
        String body = "code="          + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id="            + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                + "&client_secret="        + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8)
                + "&redirect_uri="         + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String fetchUserInfo(String accessToken) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String extractParam(String query, String param) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(param)) {
                try { return URLDecoder.decode(kv[1], StandardCharsets.UTF_8); }
                catch (Exception e) { return kv[1]; }
            }
        }
        return null;
    }
}
