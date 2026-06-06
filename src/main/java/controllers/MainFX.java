package controllers;

import services.UserService;
import entities.User;
import utils.SessionManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.sql.SQLDataException;

public class MainFX extends Application {

    private static Stage primaryStage;
    private static User authenticatedUser;
    private static final String GLOBAL_PLAYER_STYLESHEET = "/views/styles/global-player.css";

    public static void switchToAuthLandingView() {
        authenticatedUser = null;
        switchScene("/views/auth/Landing.fxml", "/views/styles/auth.css", "Artium");
    }

    public static void switchToLoginView() {
        authenticatedUser = null;
        switchScene("/views/auth/Connexion.fxml", "/views/styles/auth.css", "Artium | Connexion");
    }

    public static void switchToRegistrationView() {
        authenticatedUser = null;
        switchScene("/views/pages/inscription.fxml", "/views/styles/auth.css", "Artium | Inscription");
    }

    public static void switchToForgotPasswordView() {
        authenticatedUser = null;
        switchScene("/views/auth/ForgotPassword.fxml", "/views/styles/auth.css", "Artium | Mot de passe oublie");
    }

    public static void switchToArtistView() {
        authenticatedUser = null;
        switchScene("/views/artist/ArtistMain.fxml", "/views/styles/artist-theme.css", "Artist Dashboard");
    }

    public static void switchToArtistView(User user) {
        authenticatedUser = user;
        switchScene("/views/artist/ArtistMain.fxml", "/views/styles/artist-theme.css", "Artist Dashboard");
    }

    public static void switchToAdminView() {
        authenticatedUser = null;
        switchScene("/views/MainLayout.fxml", "/views/styles/dashboard.css", "Admin Dashboard");
    }

    public static void switchToAdminView(User user) {
        authenticatedUser = user;
        switchScene("/views/MainLayout.fxml", "/views/styles/dashboard.css", "Admin Dashboard");
    }

    public static void switchToAmateurView() {
        authenticatedUser = null;
        switchScene("/views/amateur/AmateurMain.fxml", "/views/styles/amateur-theme.css", "Amateur Dashboard");
    }

    public static void switchToAmateurView(User user) {
        authenticatedUser = user;
        switchScene("/views/amateur/AmateurMain.fxml", "/views/styles/amateur-theme.css", "Amateur Dashboard");
    }

    public static User getAuthenticatedUser() {
        return authenticatedUser;
    }

    private static void switchScene(String fxmlPath, String stylesheetPath, String title) {
        if (primaryStage == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(MainFX.class.getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            URL stylesheet = Objects.requireNonNull(MainFX.class.getResource(stylesheetPath), "Missing stylesheet");
            URL globalPlayerStylesheet = Objects.requireNonNull(
                MainFX.class.getResource(GLOBAL_PLAYER_STYLESHEET), "Missing global player stylesheet");
            scene.getStylesheets().setAll(
                stylesheet.toExternalForm(),
                globalPlayerStylesheet.toExternalForm());
            primaryStage.setTitle(title);
            primaryStage.setMaximized(true);
            primaryStage.show();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to switch scene: " + fxmlPath, e);
        }
    }

    public void start(Stage stage) {
        primaryStage = stage;
        // Taille minimale globale pour garder le layout lisible, sans figer la taille des scenes.
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(650);
        primaryStage.setMaximized(true);

        try {
            new UserService().ensureDefaultAdminAccount();
        } catch (SQLDataException e) {
            System.err.println("Impossible d'initialiser le compte admin par défaut: " + e.getMessage());
        }

        // Vérifier si une session persistante existe
        User sessionUser = SessionManager.getCurrentUser();
        if (sessionUser != null && sessionUser.getId() != null) {
            // Utilisateur connecté, rediriger vers le dashboard approprié
            authenticatedUser = sessionUser;
            redirectToUserDashboard(sessionUser);
        } else {
            // Aucune session, afficher la page d'accueil
            switchToAuthLandingView();
        }
    }

    private void redirectToUserDashboard(User user) {
        String role = user.getRole() == null ? "" : user.getRole().trim().toLowerCase();
        switch (role) {
            case "amateur":
                switchToAmateurView(user);
                break;
            case "artiste":
            case "artist":
                switchToArtistView(user);
                break;
            case "admin":
                switchToAdminView(user);
                break;
            default:
                switchToAuthLandingView();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
