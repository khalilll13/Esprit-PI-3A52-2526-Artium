package controllers;

import services.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.sql.SQLDataException;
import java.util.regex.Pattern;

public class ForgotPasswordController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @FXML
    private TextField emailField;

    @FXML
    private TextField resetCodeField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label messageLabel;

    private final UserService userService = new UserService();

    @FXML
    private void onSendResetCode() {
        clearMessage();

        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (email.isEmpty()) {
            setMessage("Veuillez saisir votre adresse e-mail.", true);
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            setMessage("Veuillez saisir une adresse e-mail valide.", true);
            return;
        }

        try {
            userService.requestPasswordResetCode(email);
            setMessage("Un code de réinitialisation a été envoyé par e-mail.", false);
        } catch (SQLDataException e) {
            setMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onResetPassword() {
        clearMessage();

        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String resetCode = resetCodeField.getText() == null ? "" : resetCodeField.getText().trim();
        String newPassword = newPasswordField.getText() == null ? "" : newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (email.isEmpty() || resetCode.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            setMessage("Veuillez remplir tous les champs.", true);
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            setMessage("Veuillez saisir une adresse e-mail valide.", true);
            return;
        }
        if (newPassword.length() < 8) {
            setMessage("Le mot de passe doit contenir au moins 8 caracteres.", true);
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            setMessage("La confirmation du mot de passe est incorrecte.", true);
            return;
        }

        try {
            userService.resetPasswordWithCode(email, resetCode, newPassword);
            setMessage("Mot de passe mis a jour. Vous pouvez maintenant vous connecter.", false);
            resetCodeField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
        } catch (SQLDataException e) {
            setMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onBackToLogin() {
        MainFX.switchToLoginView();
    }

    private void setMessage(String message, boolean error) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }

    private void clearMessage() {
        messageLabel.setText("");
        messageLabel.getStyleClass().removeAll("error", "success");
    }
}

