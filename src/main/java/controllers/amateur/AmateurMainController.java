package controllers.amateur;

import controllers.MainFX;
import entities.Livre;
import entities.User;
import entities.Evenement;
import entities.Ticket;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import services.EmailServiceEvent;

public class AmateurMainController {

    private static final String AMATEUR_STYLESHEET = "/views/styles/amateur-theme.css";

    @FXML
    private BorderPane rootPane;

    @FXML
    private StackPane amateurContentArea;

    @FXML
    private NavbarAmateurController navbarIncludeController;

    @FXML
    private SidebarAmateurController sidebarIncludeController;

    private Node originalTop;
    private Node originalBottom;
    private Node originalCenter;

    private Evenement selectedEvent;
    private Ticket pendingPurchasedTicket;

    @FXML
    public void initialize() {
        originalTop = rootPane.getTop();
        originalBottom = rootPane.getBottom();
        originalCenter = rootPane.getCenter();

        applyStylesheet();
        navbarIncludeController.setNavigationHandler(this::onNavigate);
        navbarIncludeController.setThemeHandler(this::applyTheme);

        User connectedUser = MainFX.getAuthenticatedUser();
        if (connectedUser != null) {
            sidebarIncludeController.setUser(connectedUser);
        }

        sidebarIncludeController.setNavigationHandler(this::onNavigate);

        onNavigate("feed");
    }

    private void onNavigate(String route) {
        navbarIncludeController.setActiveRoute(route);
        sidebarIncludeController.setActiveItem(route);
        Object controller = loadAmateurView(resolveRoute(route));
        configureLoadedController(controller, route);
    }

    public void openEventDetail(Evenement event) {
        this.selectedEvent = event;
        onNavigate("event-detail");
    }

    public void onTicketPurchased(Ticket ticket) {
        this.pendingPurchasedTicket = ticket;

        // Envoi de l'email en arrière-plan
        User connectedUser = MainFX.getAuthenticatedUser();
        if (connectedUser != null && connectedUser.getEmail() != null) {
            EmailServiceEvent emailService = new EmailServiceEvent();
            emailService.sendTicketEmailAsync(connectedUser.getEmail(), selectedEvent, ticket);
        }

        onNavigate("payment-success");
    }

    private String resolveRoute(String route) {
        return switch (route) {
            case "feed", "feed-peintures", "feed-sculptures", "feed-photos", "feed-recommandations",
                 "favoris", "favoris-peintures", "favoris-sculptures", "favoris-photos", "favoris-recommandations"
                    -> "/views/amateur/Feed.fxml";
            case "evenements" -> "/views/amateur/Evenements.fxml";
            case "event-detail" -> "/views/amateur/EventDetail.fxml";
            case "payment-form" -> "/views/amateur/PaymentForm.fxml";
            case "payment-success" -> "/views/amateur/PaymentSuccess.fxml";
            case "bibliotheque" -> "/views/amateur/Bibliotheque.fxml";
            case "book-reader" -> "/views/amateur/BookReader.fxml";
            case "musique" -> "/views/amateur/Musique.fxml";
            case "reclamations" -> "/views/amateur/Reclamations.fxml";
            case "reclamation-detail" -> "/views/amateur/ReclamationDetail.fxml";
            case "edit-profile" -> "/views/amateur/EditProfile.fxml";
            default -> "/views/amateur/Feed.fxml";
        };
    }

    private void applyTheme(boolean darkMode) {
        if (darkMode) {
            if (!rootPane.getStyleClass().contains("dark-mode")) {
                rootPane.getStyleClass().add("dark-mode");
            }
        } else {
            rootPane.getStyleClass().remove("dark-mode");
        }
    }

    private void applyStylesheet() {
        URL stylesheet = Objects.requireNonNull(getClass().getResource(AMATEUR_STYLESHEET), "Missing stylesheet: " + AMATEUR_STYLESHEET);
        String stylesheetUrl = stylesheet.toExternalForm();
        if (!rootPane.getStylesheets().contains(stylesheetUrl)) {
            rootPane.getStylesheets().add(stylesheetUrl);
        }
    }

    private Object loadAmateurView(String fxmlPath) {
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(fxmlPath), "FXML not found: " + fxmlPath);
            FXMLLoader loader = new FXMLLoader(resource);
            Node page = loader.load();

            Object controller = loader.getController();
            if (controller instanceof EditProfileController) {
                ((EditProfileController) controller).setOnProfileUpdated(() -> {
                    sidebarIncludeController.setUser(MainFX.getAuthenticatedUser());
                });
            }

            if (fxmlPath.endsWith("BookReader.fxml")) {
                rootPane.setTop(null);
                rootPane.setBottom(null);
                rootPane.setCenter(page);
            } else {
                if (rootPane.getCenter() != originalCenter) {
                    rootPane.setTop(originalTop);
                    rootPane.setBottom(originalBottom);
                    rootPane.setCenter(originalCenter);
                }
                amateurContentArea.getChildren().setAll(page);
            }

            return controller;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load amateur page: " + fxmlPath, e);
        }
    }

    private void configureLoadedController(Object controller, String route) {
        if (controller instanceof FeedController feedController) {
            feedController.setRouteFilter(route);
        } else if (controller instanceof EventsfrontController eventsController) {
            eventsController.setDetailNavigationHandler(this::openEventDetail);
        } else if (controller instanceof EventDetailController detailController) {
            detailController.setEvent(selectedEvent);
            detailController.setPurchaseHandler(this::onTicketPurchased);
            detailController.setPayHandler(ev -> {
                this.selectedEvent = ev;
                onNavigate("payment-form");
            });
            detailController.setBackHandler(() -> onNavigate("evenements"));
        } else if (controller instanceof PaymentFormController formController) {
            formController.setEvent(selectedEvent);
            formController.setSuccessHandler(this::onTicketPurchased);
            formController.setCancelHandler(() -> onNavigate("event-detail"));
        } else if (controller instanceof PaymentController paymentController) {
            paymentController.setTicket(pendingPurchasedTicket);
            paymentController.setBackToEventHandler(() -> onNavigate("event-detail"));
            paymentController.setBackToEventsHandler(() -> onNavigate("evenements"));
            pendingPurchasedTicket = null;
        } else if (controller instanceof BibliofrontController biblioController) {
            biblioController.setReaderNavigationHandler(livre -> {
                this.selectedLivre = livre;
                onNavigate("book-reader");
            });
        } else if (controller instanceof BookReaderController readerController) {
            if (selectedLivre != null) {
                readerController.setLivre(selectedLivre);
                readerController.setBackHandler(() -> onNavigate("bibliotheque"));
            }
            if (rootPane.getScene() != null && rootPane.getScene().getWindow() instanceof javafx.stage.Stage stage) {
                readerController.setStage(stage);
            }
        }
    }

    private Livre selectedLivre;
}


