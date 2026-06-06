package controllers.amateur;

import entities.User;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.function.Consumer;

public class SidebarAmateurController {

    private static final String DEFAULT_ROLE = "Amateur d'art";
    private static final String DEFAULT_BIO = "Aucune biographie disponible.";
    private static final String DEFAULT_VILLE = "-";
    private static final String DEFAULT_DATE = "-";
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).localizedBy(Locale.FRANCE);

    @FXML
    private Button feedButton;

    @FXML
    private Button favorisButton;

    @FXML
    private Button evenementsButton;

    @FXML
    private Button bibliothequeButton;

    @FXML
    private Button reclamationsButton;

    @FXML
    private Label nameLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label bioLabel;

    @FXML
    private Label villeValueLabel;

    @FXML
    private Label dateNaissanceValueLabel;

    @FXML
    private ImageView profileImageView;

    @FXML
    private Label avatarFallbackLabel;

    private Consumer<String> navigationHandler;

    @FXML
    public void initialize() {
        installCircularClip();
        applyDefaultProfile();
    }

    private void installCircularClip() {
        Circle clip = new Circle(21, 21, 21);
        profileImageView.setClip(clip);
    }

    public void setNavigationHandler(Consumer<String> navigationHandler) {
        this.navigationHandler = navigationHandler;
    }

    public void setActiveItem(String route) {
        List<Button> allButtons = Arrays.asList(feedButton, favorisButton, evenementsButton, bibliothequeButton, reclamationsButton);
        for (Button button : allButtons) {
            button.getStyleClass().remove("active");
        }

        if (route.startsWith("feed")) {
            feedButton.getStyleClass().add("active");
        } else if (route.startsWith("favoris")) {
            favorisButton.getStyleClass().add("active");
        } else if ("evenements".equals(route) || "event-detail".equals(route)) {
            evenementsButton.getStyleClass().add("active");
        } else if ("bibliotheque".equals(route) || "book-reader".equals(route)) {
            bibliothequeButton.getStyleClass().add("active");
        } else if ("reclamations".equals(route) || "reclamation-detail".equals(route)) {
            reclamationsButton.getStyleClass().add("active");
        }
    }

    public void setUser(User user) {
        if (user == null) {
            applyDefaultProfile();
            return;
        }

        String fullName = buildFullName(user.getPrenom(), user.getNom());
        nameLabel.setText(fullName.isEmpty() ? "Utilisateur" : fullName);
        roleLabel.setText(resolveRoleLabel(user.getRole()));
        bioLabel.setText(nonBlank(user.getBiographie(), DEFAULT_BIO));
        villeValueLabel.setText(nonBlank(user.getVille(), DEFAULT_VILLE));
        dateNaissanceValueLabel.setText(formatDate(user.getDateNaissance()));

        String imagePath = pickProfileImage(user);
        if (imagePath == null) {
            clearProfileImage();
            return;
        }

        try {
            Image image = new Image(toImageUrl(imagePath), true);
            if (image.isError()) {
                clearProfileImage();
                return;
            }
            profileImageView.setImage(image);
            profileImageView.setVisible(true);
            avatarFallbackLabel.setVisible(false);
            avatarFallbackLabel.setManaged(false);
        } catch (IllegalArgumentException ex) {
            clearProfileImage();
        }
    }

    private void applyDefaultProfile() {
        nameLabel.setText("Utilisateur");
        roleLabel.setText(DEFAULT_ROLE);
        bioLabel.setText(DEFAULT_BIO);
        villeValueLabel.setText(DEFAULT_VILLE);
        dateNaissanceValueLabel.setText(DEFAULT_DATE);
        clearProfileImage();
    }

    private void clearProfileImage() {
        profileImageView.setImage(null);
        profileImageView.setVisible(false);
        avatarFallbackLabel.setVisible(true);
        avatarFallbackLabel.setManaged(true);
    }

    private String pickProfileImage(User user) {
        if (!isBlank(user.getPhotoProfil())) {
            return user.getPhotoProfil().trim();
        }
        if (!isBlank(user.getPhotoReferencePath())) {
            return user.getPhotoReferencePath().trim();
        }
        return null;
    }

    private String toImageUrl(String imagePath) {
        if (imagePath.startsWith("file:") || imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }
        return new File(imagePath).toURI().toString();
    }

    private String resolveRoleLabel(String role) {
        if (isBlank(role)) {
            return DEFAULT_ROLE;
        }
        if ("amateur".equalsIgnoreCase(role.trim())) {
            return "Amateur d'art";
        }
        if ("artiste".equalsIgnoreCase(role.trim())) {
            return "Artiste";
        }
        if ("admin".equalsIgnoreCase(role.trim())) {
            return "Admin";
        }
        return role.trim();
    }

    private String buildFullName(String prenom, String nom) {
        String firstName = nonBlank(prenom, "");
        String lastName = nonBlank(nom, "");
        return (firstName + " " + lastName).trim();
    }

    private String formatDate(LocalDate date) {
        return date == null ? DEFAULT_DATE : BIRTH_DATE_FORMATTER.format(date);
    }

    private String nonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @FXML
    private void onFeedClick() {
        navigate("feed");
    }

    @FXML
    private void onFavorisClick() {
        navigate("favoris");
    }

    @FXML
    private void onEvenementsClick() {
        navigate("evenements");
    }

    @FXML
    private void onBibliothequeClick() {
        navigate("bibliotheque");
    }

    @FXML
    private void onReclamationsClick() {
        navigate("reclamations");
    }

    @FXML
    private void onEditProfileClick() {
        navigate("edit-profile");
    }

    @FXML
    private void onLogoutClick() {
        utils.SessionManager.clearSession();
        controllers.MainFX.switchToAuthLandingView();
    }

    private void navigate(String route) {
        if (navigationHandler != null) {
            navigationHandler.accept(route);
        }
    }
}


