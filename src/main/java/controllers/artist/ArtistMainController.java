package controllers.artist;

import controllers.MainFX;
import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class ArtistMainController {

    @FXML
    private BorderPane rootPane;

    @FXML
    private StackPane artistContentArea;

    @FXML
    private NavbarArtisteController navbarIncludeController;

    @FXML
    private ProfileHeaderController profileHeaderIncludeController;

    @FXML
    private SidebarArtisteController sidebarArtisteIncludeController;

    private boolean darkTheme;

    @FXML
    public void initialize() {
        profileHeaderIncludeController.setNavigationHandler(this::onNavigate);

        User connectedUser = MainFX.getAuthenticatedUser();
        if (connectedUser != null && sidebarArtisteIncludeController != null) {
            sidebarArtisteIncludeController.setUser(connectedUser);
        }
        if (connectedUser != null && profileHeaderIncludeController != null) {
            profileHeaderIncludeController.setUser(connectedUser);
        }

        navbarIncludeController.setActionHandler(this::applyTheme);

        onNavigate("oeuvres");
    }

    private void onNavigate(String route) {
        if ("edit-profile".equals(route)) {
            showEditProfilePopup();
            return;
        }
        profileHeaderIncludeController.setActiveTab(route);
        loadArtistView(resolveRoute(route));
    }

    private String resolveRoute(String route) {
        switch (route) {
            case "collections":
                return "/views/artist/Collections.fxml";
            case "oeuvres":
                return "/views/artist/MesOeuvres.fxml";
            case "musiques":
                return "/views/artist/Musiques.fxml";
            case "bibliotheque":
                return "/views/artist/Bibliotheque.fxml";
            case "evenements":
                return "/views/artist/Evenements.fxml";
            case "reclamations":
                return "/views/artist/Reclamations.fxml";
            case "statistiques":
                return "/views/artist/Statistiques.fxml";
            default:
                return "/views/artist/Collections.fxml";
        }
    }

    private void applyTheme(boolean darkModeEnabled) {
        darkTheme = darkModeEnabled;
        if (darkTheme) {
            if (!rootPane.getStyleClass().contains("dark-mode")) {
                rootPane.getStyleClass().add("dark-mode");
            }
        } else {
            rootPane.getStyleClass().remove("dark-mode");
        }
    }

    private void loadArtistView(String fxmlPath) {
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(fxmlPath), "FXML not found: " + fxmlPath);
            FXMLLoader loader = new FXMLLoader(resource);
            Node page = loader.load();
            artistContentArea.getChildren().setAll(page);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load artist page: " + fxmlPath, e);
        }
    }

    private void showEditProfilePopup() {
        try {
            URL resource = Objects.requireNonNull(getClass().getResource("/views/artist/EditProfileArtiste.fxml"));
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Modifier Profil");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(root, 600, 750));
            dialogStage.setResizable(false);

            EditProfileArtisteController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setOnProfileUpdated(() -> {
                profileHeaderIncludeController.setUser(MainFX.getAuthenticatedUser());
                sidebarArtisteIncludeController.setUser(MainFX.getAuthenticatedUser());
            });

            dialogStage.showAndWait();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load edit profile popup", e);
        }
    }
}