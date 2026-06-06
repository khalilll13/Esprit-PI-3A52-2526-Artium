package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;

public class SidebarController {

    public interface NavigationHandler {
        void onNavigate(String route);
    }

    @FXML
    private VBox sidebarRoot;

    @FXML
    private Button dashboardButton;

    @FXML
    private Button usersGroupButton;

    @FXML
    private VBox usersSubMenu;

    @FXML
    private Button artistesButton;

    @FXML
    private Button amateursButton;

    @FXML
    private Button oeuvresButton;

    @FXML
    private Button livresButton;

    @FXML
    private Button musiquesButton;

    @FXML
    private Button eventsGroupButton;

    @FXML
    private VBox eventsSubMenu;

    @FXML
    private Button evenementsButton;

    @FXML
    private Button galeriesButton;

    @FXML
    private Button reclamationsButton;

    @FXML
    private Region sidebarSpacer;

    private NavigationHandler navigationHandler;
    private boolean usersExpanded = true;
    private boolean eventsExpanded = true;

    @FXML
    public void initialize() {
        setSubMenuVisibility(usersSubMenu, usersExpanded);
        setSubMenuVisibility(eventsSubMenu, eventsExpanded);
    }

    public void setNavigationHandler(NavigationHandler navigationHandler) {
        this.navigationHandler = navigationHandler;
    }

    public void setActiveItem(String route) {
        List<Button> allButtons = Arrays.asList(
                dashboardButton, artistesButton, amateursButton, oeuvresButton,
                livresButton, musiquesButton, evenementsButton, galeriesButton, reclamationsButton
        );
        for (Button button : allButtons) {
            button.getStyleClass().remove("active");
        }

        switch (route) {
            case "dashboard":
                dashboardButton.getStyleClass().add("active");
                break;
            case "artistes":
                artistesButton.getStyleClass().add("active");
                break;
            case "amateurs":
                amateursButton.getStyleClass().add("active");
                break;
            case "oeuvres":
                oeuvresButton.getStyleClass().add("active");
                break;
            case "livres":
                livresButton.getStyleClass().add("active");
                break;
            case "musiques":
                musiquesButton.getStyleClass().add("active");
                break;
            case "evenements":
                evenementsButton.getStyleClass().add("active");
                break;
            case "galeries":
                galeriesButton.getStyleClass().add("active");
                break;
            case "reclamations":
                reclamationsButton.getStyleClass().add("active");
                break;
            default:
                break;
        }
    }

    public void setCollapsed(boolean collapsed) {
        if (collapsed) {
            sidebarRoot.getStyleClass().add("collapsed");
            sidebarRoot.setPrefWidth(64);
            sidebarRoot.setMinWidth(64);
            sidebarRoot.setMaxWidth(64);
            usersSubMenu.setVisible(false);
            usersSubMenu.setManaged(false);
            eventsSubMenu.setVisible(false);
            eventsSubMenu.setManaged(false);
            sidebarSpacer.setVisible(false);
            sidebarSpacer.setManaged(false);
        } else {
            sidebarRoot.getStyleClass().remove("collapsed");
            sidebarRoot.setPrefWidth(260);
            sidebarRoot.setMinWidth(260);
            sidebarRoot.setMaxWidth(260);
            setSubMenuVisibility(usersSubMenu, usersExpanded);
            setSubMenuVisibility(eventsSubMenu, eventsExpanded);
            sidebarSpacer.setVisible(true);
            sidebarSpacer.setManaged(true);
        }
    }

    @FXML
    private void onDashboardClick() {
        navigate("dashboard");
    }

    @FXML
    private void onUsersGroupClick() {
        usersExpanded = !usersExpanded;
        setSubMenuVisibility(usersSubMenu, usersExpanded);
    }

    @FXML
    private void onArtistesClick() {
        navigate("artistes");
    }

    @FXML
    private void onAmateursClick() {
        navigate("amateurs");
    }

    @FXML
    private void onOeuvresClick() {
        navigate("oeuvres");
    }

    @FXML
    private void onLivresClick() {
        navigate("livres");
    }

    @FXML
    private void onMusiquesClick() {
        navigate("musiques");
    }

    @FXML
    private void onEventsGroupClick() {
        eventsExpanded = !eventsExpanded;
        setSubMenuVisibility(eventsSubMenu, eventsExpanded);
    }

    @FXML
    private void onEvenementsClick() {
        navigate("evenements");
    }

    @FXML
    private void onGaleriesClick() {
        navigate("galeries");
    }

    @FXML
    private void onReclamationsClick() {
        navigate("reclamations");
    }

    private void navigate(String route) {
        if (navigationHandler != null) {
            navigationHandler.onNavigate(route);
        }
    }

    @FXML
    private void onLogoutClick() {
        utils.SessionManager.clearSession();
        controllers.MainFX.switchToAuthLandingView();
    }

    private void setSubMenuVisibility(VBox subMenu, boolean visible) {
        subMenu.setVisible(visible);
        subMenu.setManaged(visible);
    }
}

