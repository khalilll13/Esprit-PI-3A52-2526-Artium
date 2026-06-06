package controllers;

import entities.User;
import services.UserService;
import utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.io.File;
import java.util.List;

public class NavbarController {

    public interface ActionHandler {
        void onToggleSidebar();
        void onThemeSelected(boolean darkMode);
    }

    @FXML private Button notificationsButton;
    @FXML private MenuButton userMenuButton;
    @FXML private ImageView navbarAvatarImageView;
    @FXML private TextField globalSearchField;

    private ActionHandler actionHandler;
    private ContextMenu searchPopup;
    private UserService userService;

    @FXML
    public void initialize() {
        installCircularClip();
        
        // Initialiser le moteur de recherche
        userService = new UserService();
        searchPopup = new ContextMenu();
        searchPopup.getStyleClass().add("global-search-popup");

        if (globalSearchField != null) {
            globalSearchField.textProperty().addListener((obs, oldV, newV) -> {
                if (newV == null || newV.trim().length() < 2) {
                    searchPopup.hide();
                    return;
                }
                performSearch(newV.trim());
            });
        }
    }

    private void performSearch(String query) {
        searchPopup.getItems().clear();
        String q = query.toLowerCase();

        // 1. Rechercher dans les utilisateurs
        try {
            List<User> allUsers = userService.getAll();
            int count = 0;
            for (User u : allUsers) {
                String nom = u.getNom() == null ? "" : u.getNom().toLowerCase();
                String prenom = u.getPrenom() == null ? "" : u.getPrenom().toLowerCase();
                String role = u.getRole() == null ? "" : u.getRole();
                
                if (nom.contains(q) || prenom.contains(q)) {
                    String icon = role.equalsIgnoreCase("Artiste") ? "🎨" : "👤";
                    MenuItem item = new MenuItem(icon + " " + role + " : " + u.getPrenom() + " " + u.getNom());
                    item.setOnAction(e -> navigateToSuggested(u));
                    searchPopup.getItems().add(item);
                    count++;
                    if (count >= 5) break; // Limiter les résultats
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur de recherche globale: " + e.getMessage());
        }

        // 2. Simulation pour les futurs modules (Œuvres, Galeries, Événements)
        if (q.contains("art") || q.contains("oeuv") || q.contains("paint")) {
            MenuItem item = new MenuItem("🖼️ Œuvre suggérée : " + query + " (Module Bientôt Disponible)");
            item.setOnAction(e -> System.out.println("Redirection vers Œuvres..."));
            searchPopup.getItems().add(item);
        }
        
        if (q.contains("gal") || q.contains("expo")) {
            MenuItem item = new MenuItem("🏛️ Galerie suggérée : " + query + " (Module Bientôt Disponible)");
            item.setOnAction(e -> System.out.println("Redirection vers Galeries..."));
            searchPopup.getItems().add(item);
        }

        if (searchPopup.getItems().isEmpty()) {
            MenuItem empty = new MenuItem("Aucun résultat trouvé.");
            empty.setDisable(true);
            searchPopup.getItems().add(empty);
        }

        if (!searchPopup.isShowing()) {
            searchPopup.show(globalSearchField, javafx.geometry.Side.BOTTOM, 0, 5);
        }
    }

    private void navigateToSuggested(User u) {
        // Redirection intelligente selon le rôle de l'utilisateur trouvé
        if ("Artiste".equalsIgnoreCase(u.getRole())) {
            MainController.getInstance().navigateTo("artistes");
        } else if ("Amateur".equalsIgnoreCase(u.getRole())) {
            MainController.getInstance().navigateTo("amateurs");
        } else {
            MainController.getInstance().navigateTo("profile");
        }
        
        // Optionnel : On pourrait ici informer le controleur cible de sélectionner cet utilisateur spécifique,
        // mais pour l'instant on redirige simplement vers la bonne page.
        globalSearchField.clear();
        searchPopup.hide();
    }

    public void setActionHandler(ActionHandler actionHandler) {
        this.actionHandler = actionHandler;
    }

    public void setUser(User user) {
        if (user == null || navbarAvatarImageView == null) return;

        String prenom = user.getPrenom() == null ? "" : user.getPrenom().trim();
        String nom = user.getNom() == null ? "" : user.getNom().trim();
        String fullName = (prenom + " " + nom).trim();
        if (userMenuButton != null) {
            userMenuButton.setText(fullName.isEmpty() ? "Compte" : fullName);
        }

        String imagePath = pickProfileImage(user);
        if (imagePath != null) {
            try {
                Image image = new Image(toImageUrl(imagePath), 64, 64, false, true);
                if (!image.isError()) {
                    navbarAvatarImageView.setImage(image);
                }
            } catch (IllegalArgumentException ex) {}
        }
    }

    private void installCircularClip() {
        Circle clip = new Circle(17);
        clip.setCenterX(17);
        clip.setCenterY(17);
        navbarAvatarImageView.setClip(clip);
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

    @FXML
    private void onSidebarToggle() {
        if (actionHandler != null) actionHandler.onToggleSidebar();
    }

    @FXML
    private void onThemeLight() {
        if (actionHandler != null) actionHandler.onThemeSelected(false);
    }

    @FXML
    private void onThemeDark() {
        if (actionHandler != null) actionHandler.onThemeSelected(true);
    }

    @FXML
    private void onNotificationsClick() {
        notificationsButton.setText("!!");
    }

    @FXML
    private void onProfileClick() {
        MainController.getInstance().navigateTo("profile");
    }

    @FXML
    private void onLogoutClick() {
        SessionManager.clearSession();
        MainFX.switchToLoginView();
    }
}
