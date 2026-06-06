package controllers;

import entities.User;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import services.JdbcUserService;
import services.UserService;
import services.UserServicee;
import utils.SessionManager;

import java.sql.SQLDataException;

public class LoginController {
    private final UserServicee userService = new JdbcUserService();

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private void onLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            showError("Connexion", "Veuillez remplir tous les champs.");
            return;
        }

        try {
            User user = userService.login(email, password);
            if (user != null) {
                if ("Activé".equalsIgnoreCase(user.getStatut())) {
                    SessionManager.setCurrentUser(user);
                    redirectToDashboard(user.getRole());
                } else {
                    showError("Connexion", "Votre compte est désactivé.");
                }
            } else {
                showError("Connexion", "Email ou mot de passe incorrect.");
            }
        } catch (SQLDataException e) {
            showError("Erreur système", e.getMessage());
        }
    }

    private void redirectToDashboard(String role) {
        if ("Artiste".equalsIgnoreCase(role)) {
            MainFX.switchToArtistView();
        } else if ("Amateur".equalsIgnoreCase(role)) {
            MainFX.switchToAmateurView();
        } else if ("Admin".equalsIgnoreCase(role)) {
            MainFX.switchToAdminView();
        } else {
            showError("Rôle inconnu", "Accès refusé.");
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
