package controllers;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import utils.CardAnimator;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class MainController {

    private static MainController instance;
    public static MainController getInstance() { return instance; }

    @FXML private BorderPane        rootPane;
    @FXML private StackPane         contentArea;
    @FXML private SidebarController sidebarIncludeController;
    @FXML private NavbarController  navbarIncludeController;

    private boolean sidebarCollapsed;
    private boolean darkTheme;
    private Object  currentController;

    @FXML
    public void initialize() {
        instance = this;
        sidebarIncludeController.setNavigationHandler(this::onNavigate);

        User connectedUser = MainFX.getAuthenticatedUser();
        if (connectedUser != null) navbarIncludeController.setUser(connectedUser);

        navbarIncludeController.setActionHandler(new NavbarController.ActionHandler() {
            @Override public void onToggleSidebar()             { toggleSidebar(); }
            @Override public void onThemeSelected(boolean dark) { applyTheme(dark); }
        });

        onNavigate("dashboard");
    }

    public void navigateTo(String route) { onNavigate(route); }

    private void onNavigate(String route) {
        sidebarIncludeController.setActiveItem(route);
        switch (route) {
            case "dashboard"    -> loadPage("/views/pages/dashboard.fxml");
            case "artistes"     -> loadPage("/views/pages/artistes.fxml");
            case "amateurs"     -> loadPage("/views/pages/amateurs.fxml");
            case "oeuvres"      -> loadPage("/views/pages/oeuvres.fxml");
            case "livres"       -> loadPage("/views/pages/livres.fxml");
            case "musiques"     -> loadPage("/views/pages/musiques.fxml");
            case "evenements"   -> loadPage("/views/pages/evenements.fxml");
            case "galeries"     -> loadPage("/views/pages/galeries.fxml");
            case "reclamations" -> loadPage("/views/pages/reclamations.fxml");
            case "profile"      -> loadPage("/views/pages/profile.fxml");
            case "editProfile"  -> loadPage("/views/pages/editProfile.fxml");
            default             -> loadPage("/views/pages/dashboard.fxml");
        }
    }

    public void loadPage(String fxmlPath) {
        try {
            URL        resource = Objects.requireNonNull(
                    getClass().getResource(fxmlPath), "FXML not found: " + fxmlPath);
            FXMLLoader loader   = new FXMLLoader(resource);
            Node       page     = loader.load();
            currentController   = loader.getController();
            contentArea.getChildren().setAll(page);

            // Petite animation à chaque ouverture de page
            CardAnimator.animatePageTransition(page);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load page: " + fxmlPath, e);
        }
    }

    // ── Helpers vocaux ────────────────────────────────────────────────────────

    private BaseUsersBackofficeController getBackofficeController() {
        if (currentController instanceof BaseUsersBackofficeController c) return c;
        return null;
    }

    public void triggerSearch(String argument) {
        BaseUsersBackofficeController c = getBackofficeController();
        if (c != null) c.setSearchQuery(argument);
    }

    public void triggerCreate(String argument) {
        BaseUsersBackofficeController c = getBackofficeController();
        if (c != null) c.openCreateForm(argument);
    }

    public void triggerDelete(String argument) {
        BaseUsersBackofficeController c = getBackofficeController();
        if (c != null) c.deleteUserByName(argument);
    }

    public void triggerBlock(String argument) {
        BaseUsersBackofficeController c = getBackofficeController();
        if (c != null) c.blockUserByName(argument);
    }

    public void triggerActivate(String argument) {
        BaseUsersBackofficeController c = getBackofficeController();
        if (c != null) c.activateUserByName(argument);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        sidebarIncludeController.setCollapsed(sidebarCollapsed);
    }

    private void applyTheme(boolean darkModeEnabled) {
        darkTheme = darkModeEnabled;
        if (darkTheme) rootPane.getStyleClass().add("dark-mode");
        else           rootPane.getStyleClass().remove("dark-mode");
    }
}