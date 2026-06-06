package controllers;

import services.UserService;
import components.HumanVerificationPane;
import services.UserService;
import entities.User;
import utils.GoogleAuthService;        // ← nouveau
import utils.LoginRateLimiter;
import utils.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import utils.ArtisticBackground;
import utils.InputValidator;

import java.sql.SQLDataException;

public class ConnexionController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;
    @FXML private VBox loginCard;
    @FXML private StackPane landingShell;

    private final UserService userService = new UserService();
    private HumanVerificationPane captchaPane;
    private Timeline countdownTimer;
    private String blockedEmail;

    @FXML
    public void initialize() {
        if (landingShell != null) {
            ArtisticBackground.attach(landingShell, 15); // 15 touches de peinture
        }

        captchaPane = new HumanVerificationPane();
        loginCard.getChildren().add(3, captchaPane);

        emailField.textProperty().addListener((obs, oldV, newV) -> validateLiveInput());
        passwordField.textProperty().addListener((obs, oldV, newV) -> validateLiveInput());
    }

    @FXML
    private void onLogin() {
        clearMessage();
        stopCountdown();

        if (!captchaPane.isHumanVerified()) {
            setMessage("Veuillez compléter la vérification humaine.");
            return;
        }

        String email    = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        String localError = validateCredentials(email, password);
        if (localError != null) { setMessage(localError); return; }

        String rateError = LoginRateLimiter.check(email);
        if (rateError != null) {
            setMessage(rateError);
            startCountdown(email);
            return;
        }

        try {
            User user = userService.authenticate(email, password);
            LoginRateLimiter.resetOnSuccess(email);
            SessionManager.setCurrentUser(user);
            routeByRole(user);
        } catch (SQLDataException e) {
            LoginRateLimiter.recordFailure(email);
            String newRateError = LoginRateLimiter.check(email);
            if (newRateError != null) {
                setMessage(newRateError);
                startCountdown(email);
            } else {
                setMessage(e.getMessage());
            }
        }
    }

    // ← nouveau
    @FXML
    private void onGoogleLogin() {
        clearMessage();
        stopCountdown();
        setMessage("Ouverture de Google...");

        GoogleAuthService.signIn(
                googleUser -> {
                    try {
                        User user = userService.findOrCreateGoogleUser(
                                googleUser.email(),
                                googleUser.name(),
                                googleUser.googleId()
                        );
                        SessionManager.setCurrentUser(user);
                        routeByRole(user);
                    } catch (SQLDataException e) {
                        setMessage("Erreur : " + e.getMessage());
                    }
                },
                errorMsg -> setMessage(errorMsg)
        );
    }

    private void startCountdown(String email) {
        blockedEmail = email;
        countdownTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    String msg = LoginRateLimiter.check(blockedEmail);
                    if (msg != null) setMessage(msg);
                    else { clearMessage(); stopCountdown(); }
                })
        );
        countdownTimer.setCycleCount(Timeline.INDEFINITE);
        countdownTimer.play();
    }

    private void stopCountdown() {
        if (countdownTimer != null) { countdownTimer.stop(); countdownTimer = null; }
        blockedEmail = null;
    }

    @FXML private void onCreateAccount()  { MainFX.switchToRegistrationView(); }
    @FXML private void onBackToLanding()  { MainFX.switchToAuthLandingView(); stopCountdown(); }
    @FXML private void onForgotPassword() { MainFX.switchToForgotPasswordView(); stopCountdown(); }

    private void routeByRole(User user) {
        String role = user.getRole() == null ? "" : user.getRole().trim().toLowerCase();
        switch (role) {
            case "amateur"           -> MainFX.switchToAmateurView(user);
            case "artiste", "artist" -> MainFX.switchToArtistView(user);
            case "admin"             -> MainFX.switchToAdminView(user);
            default                  -> setMessage("Rôle non supporté : " + user.getRole());
        }
    }

    private void validateLiveInput() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (InputValidator.isBlank(email)) { clearMessage(); return; }
        if (!InputValidator.isValidEmail(email)) { setMessage("Format d'e-mail invalide."); return; }
        clearMessage();
    }

    private String validateCredentials(String email, String password) {
        if (InputValidator.isBlank(email) || InputValidator.isBlank(password))
            return "Veuillez saisir votre e-mail et votre mot de passe.";
        if (!InputValidator.isValidEmail(email))
            return "Format d'e-mail invalide.";
        return null;
    }

    private void setMessage(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add("error");
    }

    private void clearMessage() {
        messageLabel.setText("");
        messageLabel.getStyleClass().removeAll("error", "success");
    }
}