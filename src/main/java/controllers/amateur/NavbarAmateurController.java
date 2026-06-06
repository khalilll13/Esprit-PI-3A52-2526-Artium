package controllers.amateur;

import controllers.MainFX;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import services.NotificationService;
import utils.SessionManager;
import entities.User;
import entities.Notification;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.stage.Stage;
import utils.UserSession;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class NavbarAmateurController {

    private static final DateTimeFormatter NOTIFICATION_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @FXML
    private Button anchorButton;

    @FXML
    private MenuButton notificationsButton;

    @FXML
    private MenuButton oeuvresButton;

    @FXML
    private Button bibliothequeButton;

    @FXML
    private Button musiqueButton;

    @FXML
    private MenuButton userMenuButton;

    private final NotificationService notificationService = new NotificationService();
    private Consumer<String> navigationHandler;
    private Consumer<Boolean> themeHandler;
    private String currentRoute = "feed";
    private String oeuvreSectionContext = "feed";

    @FXML
    public void initialize() {
        if (notificationsButton != null) {
            notificationsButton.setOnShowing(event -> populateNotificationsMenu());
            updateNotificationCount();
        }
        if (bibliothequeButton != null) {
            bibliothequeButton.setOnAction(event -> navigate("bibliotheque"));
        }
        if (musiqueButton != null) {
            musiqueButton.setOnAction(event -> navigate("musique"));
        }
    }

    public void setNavigationHandler(Consumer<String> navigationHandler) {
        this.navigationHandler = navigationHandler;
    }

    public void setThemeHandler(Consumer<Boolean> themeHandler) {
        this.themeHandler = themeHandler;
    }

    public void setUser(User user) {
        if (userMenuButton == null) {
            return;
        }
        if (user == null) {
            userMenuButton.setText("Compte");
            return;
        }

        String prenom = user.getPrenom() == null ? "" : user.getPrenom().trim();
        String nom = user.getNom() == null ? "" : user.getNom().trim();
        String fullName = (prenom + " " + nom).trim();
        userMenuButton.setText(fullName.isEmpty() ? "Compte" : fullName);
    }

    public void setActiveRoute(String route) {
        currentRoute = route == null ? "feed" : route;
        oeuvreSectionContext = currentRoute.startsWith("favoris") ? "favoris" : "feed";
        oeuvresButton.getStyleClass().remove("active");
        oeuvresButton.getStyleClass().remove("active-feed");
        oeuvresButton.getStyleClass().remove("active-favoris");
        bibliothequeButton.getStyleClass().remove("active");
        musiqueButton.getStyleClass().remove("active");

        if (currentRoute.startsWith("feed") || currentRoute.startsWith("favoris")) {
            oeuvresButton.getStyleClass().add("active");
            oeuvresButton.getStyleClass().add("favoris".equals(oeuvreSectionContext) ? "active-favoris" : "active-feed");
        } else if ("bibliotheque".equals(currentRoute) || "book-reader".equals(currentRoute)) {
            bibliothequeButton.getStyleClass().add("active");
        } else if ("musique".equals(currentRoute)) {
            musiqueButton.getStyleClass().add("active");
        }
    }

    @FXML
    private void onFeedClick() {
        navigate(resolveOeuvreRoute("feed", "favoris"));
    }

    @FXML
    private void onFeedPeinturesClick() {
        navigate(resolveOeuvreRoute("feed-peintures", "favoris-peintures"));
    }

    @FXML
    private void onFeedSculpturesClick() {
        navigate(resolveOeuvreRoute("feed-sculptures", "favoris-sculptures"));
    }

    @FXML
    private void onFeedPhotosClick() {
        navigate(resolveOeuvreRoute("feed-photos", "favoris-photos"));
    }

    @FXML
    private void onFeedRecommendationsClick() {
        // Recommendations always open the feed context so the Fil d'actualite section stays active.
        navigate("feed-recommandations");
    }

    @FXML
    private void onBibliothequeClick() {
        navigate("bibliotheque");
    }

    @FXML
    private void onMusiqueClick() {
        navigate("musique");
    }

    @FXML
    private void onThemeLight() {
        if (themeHandler != null) {
            themeHandler.accept(false);
        }
    }

    @FXML
    private void onThemeDark() {
        if (themeHandler != null) {
            themeHandler.accept(true);
        }
    }

    @FXML
    private void onNotificationsClick() {
        populateNotificationsMenu();
    }

    private void updateNotificationCount() {
        if (notificationsButton == null) return;
        Integer userId = UserSession.getCurrentUserId();
        if (userId == null) {
            notificationsButton.setText("");
        } else {
            int count = notificationService.countUnreadNotifications(userId);
            notificationsButton.setText(count == 0 ? "" : String.valueOf(count));
        }
    }

    private void populateNotificationsMenu() {
        if (notificationsButton == null) {
            return;
        }

        Integer userId = UserSession.getCurrentUserId();
        notificationsButton.getItems().clear();

        // Add header
        Label headerLabel = new Label("Notifications");
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #111827; -fx-padding: 4px 8px;");
        javafx.scene.control.MenuItem headerItem = new javafx.scene.control.MenuItem();
        headerItem.setGraphic(headerLabel);
        headerItem.setStyle("-fx-background-color: transparent;");
        notificationsButton.getItems().add(headerItem);

        javafx.scene.control.SeparatorMenuItem separator = new javafx.scene.control.SeparatorMenuItem();
        notificationsButton.getItems().add(separator);

        if (userId == null) {
            notificationsButton.setText("");
            javafx.scene.control.CustomMenuItem emptyItem = new javafx.scene.control.CustomMenuItem(createEmptyNode("Aucune session utilisateur active."), false);
            emptyItem.setStyle("-fx-background-color: transparent;");
            notificationsButton.getItems().add(emptyItem);
        } else {
            List<Notification> notifications = notificationService.getUnreadNotifications(userId);
            notificationsButton.setText(notifications.isEmpty() ? "" : String.valueOf(notifications.size()));

            if (notifications.isEmpty()) {
                javafx.scene.control.CustomMenuItem emptyItem = new javafx.scene.control.CustomMenuItem(createEmptyNode("Aucune notification pour le moment."), false);
                emptyItem.setStyle("-fx-background-color: transparent;");
                notificationsButton.getItems().add(emptyItem);
            } else {
                for (Notification notification : notifications) {
                    javafx.scene.control.CustomMenuItem notifItem = new javafx.scene.control.CustomMenuItem(createNotificationNode(notification), false);
                    notifItem.setStyle("-fx-background-color: transparent;");
                    notifItem.setHideOnClick(false); // Manually handle hiding
                    notificationsButton.getItems().add(notifItem);
                }
            }
        }
    }

    private javafx.scene.Node createEmptyNode(String text) {
        Label emptyLabel = new Label(text);
        emptyLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 14px; -fx-font-style: italic;");
        HBox wrapper = new HBox(emptyLabel);
        wrapper.setStyle("-fx-padding: 24px 20px; -fx-alignment: center;");
        wrapper.setPrefWidth(320);
        return wrapper;
    }

    @FXML
    private void onSwitchToAdminView() {
        MainFX.switchToAdminView(resolveCurrentUser());
    }

    private javafx.scene.Node createNotificationNode(Notification notification) {
        String title = resolveTitle(notification);
        Label titleLabel = new Label(title);
        Label messageLabel = new Label(resolveMessage(notification));
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);
        Label timeLabel = new Label(resolveTime(notification));

        VBox content = new VBox(6, titleLabel, messageLabel, timeLabel);
        content.setPrefWidth(320);

        if (title.toLowerCase().contains("annul")) {
            content.setStyle("-fx-background-color: #fff7ed; -fx-border-color: #fed7aa; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 14px; -fx-cursor: hand;");
            titleLabel.setStyle("-fx-text-fill: #c2410c; -fx-font-size: 15px; -fx-font-weight: 800;");
            messageLabel.setStyle("-fx-text-fill: #ea580c; -fx-font-size: 13px;");
            timeLabel.setStyle("-fx-text-fill: #f97316; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else if (title.toLowerCase().contains("réclamation") || title.toLowerCase().contains("reclamation")) {
            content.setStyle("-fx-background-color: #f0fdfa; -fx-border-color: #ccfbf1; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 14px; -fx-cursor: hand;");
            titleLabel.setStyle("-fx-text-fill: #0f766e; -fx-font-size: 15px; -fx-font-weight: 800;");
            messageLabel.setStyle("-fx-text-fill: #115e59; -fx-font-size: 13px;");
            timeLabel.setStyle("-fx-text-fill: #14b8a6; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            content.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 14px; -fx-cursor: hand;");
            titleLabel.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 15px; -fx-font-weight: 800;");
            messageLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");
            timeLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        }

        content.setOnMouseClicked(e -> {
            notification.setRead(true);
            
            // Navigate to the correct section based on the notification type
            if (title.toLowerCase().contains("annul")) {
                navigate("evenements");
            } else if (title.toLowerCase().contains("réclamation") || title.toLowerCase().contains("reclamation")) {
                navigate("reclamations");
            }
            
            // Refresh the notifications list (this will remove it since it's now marked as read)
            populateNotificationsMenu();
            updateNotificationCount();
            
            // Close the dropdown menu after clicking
            if (notificationsButton != null) {
                notificationsButton.hide();
            }
        });

        return content;
    }

    private String resolveTitle(Notification notification) {
        if (notification.getTitle() == null || notification.getTitle().isBlank()) {
            return "Notification";
        }
        return notification.getTitle().trim();
    }

    private String resolveMessage(Notification notification) {
        if (notification.getMessage() == null || notification.getMessage().isBlank()) {
            return "Vous avez une nouvelle mise a jour.";
        }
        return notification.getMessage().trim();
    }

    private String resolveTime(Notification notification) {
        if (notification.getCreatedAt() == null) {
            return "Maintenant";
        }
        return NOTIFICATION_TIME_FORMATTER.format(notification.getCreatedAt());
    }

    @FXML
    private void onSwitchToArtistView() {
        MainFX.switchToArtistView(resolveCurrentUser());
    }

    @FXML
    private void onSwitchToAmateurView() {
        MainFX.switchToAmateurView(resolveCurrentUser());
    }

    @FXML
    private void onLogoutClick() {
        // Effacer la session persistante
        SessionManager.clearSession();
        // Rediriger vers la page d'authentification
        MainFX.switchToLoginView();
    }

    private void navigate(String route) {
        if (navigationHandler != null) {
            navigationHandler.accept(route);
        }
    }

    private String resolveOeuvreRoute(String feedRoute, String favorisRoute) {
        if ("favoris".equals(oeuvreSectionContext)) {
            return favorisRoute;
        }
        return feedRoute;
    }

    User resolveCurrentUser() {
        User user = MainFX.getAuthenticatedUser();
        if (user == null) {
            user = SessionManager.getCurrentUser();
        }
        return user;
    }
    /*private User switchScene(String fxmlPath, String stylesheetPath, String title) {
        if (notificationsButton == null || notificationsButton.getScene() == null) {
            return;
        }

        Stage stage = (Stage) notificationsButton.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            URL stylesheet = Objects.requireNonNull(getClass().getResource(stylesheetPath), "Missing stylesheet");
            scene.getStylesheets().add(stylesheet.toExternalForm());
            stage.setTitle(title);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to switch scene: " + fxmlPath, e);
        }
    }*/
}


