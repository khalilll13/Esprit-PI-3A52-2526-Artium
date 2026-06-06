package controllers.artist;

import controllers.MainFX;
import entities.User;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.net.URL;

import java.io.File;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;

public class ProfileHeaderController {

    public interface NavigationHandler {
        void onNavigate(String route);
    }

    @FXML
    private Label fullNameLabel;

    @FXML
    private Label metaLabel;

    @FXML
    private ImageView profileImageView;

    @FXML
    private Label avatarLabelFallback;

    @FXML
    private StackPane coverPane;

    @FXML
    private ImageView coverBannerView;

    @FXML
    private Button collectionsTabButton;

    @FXML
    private Button bibliothequeTabButton;

    @FXML
    private Button contentTabButton;

    @FXML
    private Button musiquesTabButton;

    @FXML
    private Button evenementsTabButton;

    @FXML
    private Button reclamationsTabButton;

    @FXML
    private Button statistiquesTabButton;

    private NavigationHandler navigationHandler;
    private String specialite = "Sculpteur";
    private String dynamicRoute = "oeuvres";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).localizedBy(Locale.FRANCE);
    private static final String COVER_IMAGE_PATH = "/views/images/avatar_2.png";

    @FXML
    public void initialize() {
        installCircularClip();
        setupCoverBanner();
        User connectedUser = MainFX.getAuthenticatedUser();
        if (connectedUser != null) {
            setUser(connectedUser);
        } else {
            applyDefaultProfile();
        }
        updateDynamicTab();
    }

    public void setUser(User user) {
        if (user == null) {
            applyDefaultProfile();
            return;
        }

        String fullName = buildFullName(user.getPrenom(), user.getNom());
        fullNameLabel.setText(fullName.isEmpty() ? "Artiste" : fullName);

        String specialite = user.getSpecialite() != null ? user.getSpecialite().trim() : "Artiste";
        this.specialite = specialite;
        updateDynamicTab();

        String ville = user.getVille() != null ? user.getVille().trim() : "-";
        String dateInscription = user.getDateInscription() != null
            ? DATE_FORMATTER.format(user.getDateInscription())
            : "-";

        metaLabel.setText(specialite + "  -  " + ville + "  -  Inscrit le " + dateInscription);

        String imagePath = pickProfileImage(user);
        if (imagePath != null) {
            try {
                Image image = new Image(toImageUrl(imagePath), false); // chargement synchrone
                if (!image.isError()) {
                    profileImageView.setImage(image);
                    profileImageView.setVisible(true);
                    avatarLabelFallback.setVisible(false);
                    avatarLabelFallback.setManaged(false);
                    return;
                }
            } catch (IllegalArgumentException ex) {
                // Image invalide, utiliser le fallback
            }
        }
        clearProfileImage();
    }

    private void applyDefaultProfile() {
        fullNameLabel.setText("Artiste");
        metaLabel.setText("Artiste  -  -  Inscrit le -");
        specialite = "Artiste";
        updateDynamicTab();
        clearProfileImage();
    }

    private void clearProfileImage() {
        profileImageView.setImage(null);
        profileImageView.setVisible(false);
        avatarLabelFallback.setVisible(true);
        avatarLabelFallback.setManaged(true);
    }

    private void installCircularClip() {
        Circle clip = new Circle(40, 40, 40);
        profileImageView.setClip(clip);
    }

    private void setupCoverBanner() {
        if (coverPane == null || coverBannerView == null) {
            return;
        }

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(coverPane.widthProperty());
        clip.heightProperty().bind(coverPane.heightProperty());
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        coverPane.setClip(clip);

        URL coverUrl = getClass().getResource(COVER_IMAGE_PATH);
        if (coverUrl == null) {
            return;
        }

        Image coverImage = new Image(coverUrl.toExternalForm(), true);
        coverBannerView.setImage(coverImage);
        coverBannerView.setSmooth(true);

        Runnable refit = () -> applyCoverFit(coverImage);
        coverImage.progressProperty().addListener((obs, oldProgress, progress) -> {
            if (progress.doubleValue() >= 1.0 && !coverImage.isError()) {
                refit.run();
            }
        });
        coverPane.widthProperty().addListener((obs, oldW, newW) -> refit.run());
        coverPane.heightProperty().addListener((obs, oldH, newH) -> refit.run());
    }

    private void applyCoverFit(Image image) {
        if (coverPane == null || coverBannerView == null || image == null) {
            return;
        }
        double imgW = image.getWidth();
        double imgH = image.getHeight();
        double paneW = coverPane.getWidth();
        double paneH = coverPane.getHeight();
        if (imgW <= 0 || imgH <= 0 || paneW <= 0 || paneH <= 0) {
            return;
        }

        double scale = Math.max(paneW / imgW, paneH / imgH);
        double fitW = imgW * scale;
        double fitH = imgH * scale;

        coverBannerView.setFitWidth(fitW);
        coverBannerView.setFitHeight(fitH);
        coverBannerView.setPreserveRatio(true);
        coverBannerView.setLayoutX((paneW - fitW) / 2.0);
        coverBannerView.setLayoutY((paneH - fitH) / 2.0);
    }

    private String pickProfileImage(User user) {
        if (user.getPhotoProfil() != null && !user.getPhotoProfil().trim().isEmpty()) {
            return user.getPhotoProfil().trim();
        }
        if (user.getPhotoReferencePath() != null && !user.getPhotoReferencePath().trim().isEmpty()) {
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

    private String buildFullName(String prenom, String nom) {
        String firstName = prenom != null ? prenom.trim() : "";
        String lastName = nom != null ? nom.trim() : "";
        return (firstName + " " + lastName).trim();
    }

    public void setNavigationHandler(NavigationHandler navigationHandler) {
        this.navigationHandler = navigationHandler;
    }

    public String getDefaultRoute() {
        return dynamicRoute;
    }

    public void setActiveTab(String route) {
        List<Button> tabs = Arrays.asList(
                collectionsTabButton,
                bibliothequeTabButton,
                contentTabButton,
                musiquesTabButton,
                evenementsTabButton,
                reclamationsTabButton,
                statistiquesTabButton
        );
        for (Button tab : tabs) {
            tab.getStyleClass().remove("active");
        }

        if ("collections".equals(route)) {
            collectionsTabButton.getStyleClass().add("active");
        } else if ("bibliotheque".equals(route)) {
            if ("bibliotheque".equals(dynamicRoute)) {
                contentTabButton.getStyleClass().add("active");
            } else {
                bibliothequeTabButton.getStyleClass().add("active");
            }
        } else if ("musiques".equals(route)) {
            if ("musiques".equals(dynamicRoute)) {
                contentTabButton.getStyleClass().add("active");
            } else {
                musiquesTabButton.getStyleClass().add("active");
            }
        } else if (dynamicRoute.equals(route)) {
            contentTabButton.getStyleClass().add("active");
        } else if ("evenements".equals(route)) {
            evenementsTabButton.getStyleClass().add("active");
        } else if ("reclamations".equals(route)) {
            reclamationsTabButton.getStyleClass().add("active");
        } else if ("statistiques".equals(route)) {
            statistiquesTabButton.getStyleClass().add("active");
        }
    }

    @FXML
    private void onCollectionsClick() {
        navigate("collections");
    }

    @FXML
    private void onBibliothequeClick() {
        navigate("bibliotheque");
    }

    @FXML
    private void onDynamicContentClick() {
        navigate(dynamicRoute);
    }

    @FXML
    private void onMusiquesClick() {
        navigate("musiques");
    }

    @FXML
    private void onEvenementsClick() {
        navigate("evenements");
    }

    @FXML
    private void onReclamationsClick() {
        navigate("reclamations");
    }

    @FXML
    private void onStatistiquesClick() {
        navigate("statistiques");
    }

    @FXML
    private void onEditProfileClick() {
        navigate("edit-profile");
    }

    private void navigate(String route) {
        if (navigationHandler != null) {
            navigationHandler.onNavigate(route);
        }
    }

    private void updateDynamicTab() {
        if (isMusicienSpecialite()) {
            dynamicRoute = "musiques";
            contentTabButton.setText("Musiques");
            bibliothequeTabButton.setVisible(false);
            bibliothequeTabButton.setManaged(false);
            musiquesTabButton.setVisible(false);
            musiquesTabButton.setManaged(false);
        } else if (isAuteurSpecialite()) {
            dynamicRoute = "bibliotheque";
            contentTabButton.setText("Bibliotheque");
            bibliothequeTabButton.setVisible(false);
            bibliothequeTabButton.setManaged(false);
            musiquesTabButton.setVisible(false);
            musiquesTabButton.setManaged(false);
        } else {
            dynamicRoute = "oeuvres";
            contentTabButton.setText("Mes Oeuvres");
            bibliothequeTabButton.setVisible(false);
            bibliothequeTabButton.setManaged(false);
            musiquesTabButton.setVisible(false);
            musiquesTabButton.setManaged(false);
        }
    }

    private boolean isMusicienSpecialite() {
        String key = normalizedSpecialiteKey();
        return key.equals("musicien") || key.equals("muscien") || key.equals("musicienne");
    }

    private boolean isAuteurSpecialite() {
        String key = normalizedSpecialiteKey();
        return key.equals("auteur") || key.equals("autheur") || key.equals("auteure");
    }

    private String normalizedSpecialiteKey() {
        String raw = specialite == null ? "" : specialite.trim().toLowerCase(Locale.ROOT);
        String noAccent = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccent.replaceAll("[^a-z]", "");
    }
}

