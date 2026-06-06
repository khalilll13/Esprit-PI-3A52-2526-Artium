package controllers;

import entities.Evenement;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.RangeSlider;
import services.EvenementService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLDataException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class EvenementsController {

    @FXML
    private TextField rechercheField;

    @FXML
    private ComboBox<String> filtreComboBox;

    @FXML
    private GridPane evenementsListContainer;

    @FXML
    private Label emptyStateLabel;

    @FXML
    private RangeSlider capaciteRangeSlider;

    @FXML
    private RangeSlider prixRangeSlider;

    @FXML
    private Label capaciteRangeLabel;

    @FXML
    private Label prixRangeLabel;

    private final EvenementService evenementService = new EvenementService();
    private final List<Evenement> allEvenements = new ArrayList<>();

    @FXML
    public void initialize() {
        filtreComboBox.getItems().addAll(
                "Titre (A-Z)",
                "Titre (Z-A)",
                "Date debut (plus recente)",
                "Date debut (plus ancienne)",
                "Capacite (croissante)",
                "Capacite (decroissante)",
                "Prix (croissant)",
                "Prix (decroissant)"
        );
        filtreComboBox.setValue("Titre (A-Z)");

        configureRangeSliders();

        rechercheField.textProperty().addListener((observable, oldValue, newValue) -> applySearchAndFilter());
        filtreComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySearchAndFilter());
        capaciteRangeSlider.lowValueProperty().addListener((observable, oldValue, newValue) -> applySearchAndFilter());
        capaciteRangeSlider.highValueProperty().addListener((observable, oldValue, newValue) -> applySearchAndFilter());
        prixRangeSlider.lowValueProperty().addListener((observable, oldValue, newValue) -> applySearchAndFilter());
        prixRangeSlider.highValueProperty().addListener((observable, oldValue, newValue) -> applySearchAndFilter());

        refreshEvenements();
    }

    private void refreshEvenements() {
        try {
            allEvenements.clear();
            allEvenements.addAll(evenementService.getAll());
            applySearchAndFilter();
        } catch (SQLDataException e) {
            showError("Chargement impossible", e.getMessage());
        }
    }

    private void applySearchAndFilter() {
        String search = rechercheField.getText() == null ? "" : rechercheField.getText().trim().toLowerCase();
        int capaciteMin = (int) Math.round(capaciteRangeSlider.getLowValue());
        int capaciteMax = (int) Math.round(capaciteRangeSlider.getHighValue());
        double prixMin = prixRangeSlider.getLowValue();
        double prixMax = prixRangeSlider.getHighValue();

        updateRangeLabels(capaciteMin, capaciteMax, prixMin, prixMax);

        List<Evenement> filtered = allEvenements.stream()
                .filter(evenement -> matchesSearch(evenement, search))
                .filter(evenement -> matchesCapacity(evenement, capaciteMin, capaciteMax))
                .filter(evenement -> matchesPrice(evenement, prixMin, prixMax))
                .sorted(buildComparator(filtreComboBox.getValue()))
                .collect(Collectors.toList());

        renderCards(filtered);
    }

    private boolean matchesSearch(Evenement evenement, String search) {
        if (search.isEmpty()) {
            return true;
        }

        return contains(evenement.getTitre(), search)
                || contains(evenement.getDescription(), search)
                || contains(evenement.getId() == null ? null : String.valueOf(evenement.getId()), search);
    }

    private Comparator<Evenement> buildComparator(String selectedFilter) {
        if (selectedFilter == null) {
            return Comparator.comparing((Evenement evenement) -> evenement.getId() == null ? 0 : evenement.getId(), Comparator.reverseOrder());
        }

        return switch (selectedFilter) {
            case "ID (A-Z)" -> Comparator.comparing((Evenement evenement) -> evenement.getId() == null ? 0 : evenement.getId());
            case "ID (Z-A)" -> Comparator.comparing((Evenement evenement) -> evenement.getId() == null ? 0 : evenement.getId()).reversed();
            case "Titre (A-Z)" -> Comparator.comparing(evenement -> safe(evenement.getTitre()));
            case "Titre (Z-A)" -> Comparator.comparing((Evenement evenement) -> safe(evenement.getTitre())).reversed();
            case "Date debut (plus recente)" -> Comparator.comparing(EvenementsController::safeDateTime, Comparator.reverseOrder());
            case "Date debut (plus ancienne)" -> Comparator.comparing(EvenementsController::safeDateTime);
            case "Capacite (croissante)" -> Comparator.comparing(evenement -> evenement.getCapaciteMax() == null ? 0 : evenement.getCapaciteMax());
            case "Capacite (decroissante)" -> Comparator.comparing((Evenement evenement) -> evenement.getCapaciteMax() == null ? 0 : evenement.getCapaciteMax()).reversed();
            case "Prix (croissant)" -> Comparator.comparing(evenement -> evenement.getPrixTicket() == null ? 0D : evenement.getPrixTicket());
            case "Prix (decroissant)" -> Comparator.comparing((Evenement evenement) -> evenement.getPrixTicket() == null ? 0D : evenement.getPrixTicket()).reversed();
            default -> Comparator.comparing((Evenement evenement) -> evenement.getId() == null ? 0 : evenement.getId()).reversed();
        };
    }

    private void configureRangeSliders() {
        capaciteRangeSlider.setMin(0);
        capaciteRangeSlider.setMax(1000);
        capaciteRangeSlider.setLowValue(0);
        capaciteRangeSlider.setHighValue(1000);
        capaciteRangeSlider.setMajorTickUnit(100);
        capaciteRangeSlider.setMinorTickCount(0);
        capaciteRangeSlider.setSnapToTicks(false);

        prixRangeSlider.setMin(0);
        prixRangeSlider.setMax(500);
        prixRangeSlider.setLowValue(0);
        prixRangeSlider.setHighValue(500);
        prixRangeSlider.setMajorTickUnit(50);
        prixRangeSlider.setMinorTickCount(0);
        prixRangeSlider.setSnapToTicks(false);

        updateRangeLabels(0, 1000, 0, 500);
    }

    private void updateRangeLabels(int capaciteMin, int capaciteMax, double prixMin, double prixMax) {
        capaciteRangeLabel.setText("Capacite: " + capaciteMin + " - " + capaciteMax);
        prixRangeLabel.setText("Prix: " + formatPrice(prixMin) + " - " + formatPrice(prixMax) + " DT");
    }

    private String formatPrice(double value) {
        long rounded = Math.round(value);
        return String.valueOf(rounded);
    }

    private boolean matchesCapacity(Evenement evenement, int min, int max) {
        int capacite = evenement.getCapaciteMax() == null ? 0 : evenement.getCapaciteMax();
        return capacite >= min && capacite <= max;
    }

    private boolean matchesPrice(Evenement evenement, double min, double max) {
        double prix = evenement.getPrixTicket() == null ? 0D : evenement.getPrixTicket();
        return prix >= min && prix <= max;
    }

    private void renderCards(List<Evenement> evenements) {
        evenementsListContainer.getChildren().clear();

        for (int index = 0; index < evenements.size(); index++) {
            Evenement evenement = evenements.get(index);
            try {
                FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/views/components/evenement-card.fxml")));
                Parent card = loader.load();
                EvenementCardController cardController = loader.getController();
                cardController.setData(evenement, this::handleDelete);

                int column = index % 3;
                int row = index / 3;
                evenementsListContainer.add(card, column, row);
            } catch (IOException e) {
                showError("Affichage impossible", "Erreur pendant le rendu d'une carte evenement.");
                return;
            }
        }

        boolean empty = evenements.isEmpty();
        emptyStateLabel.setVisible(empty);
        emptyStateLabel.setManaged(empty);
    }

    private void handleDelete(Evenement evenementToDelete) {
        ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "", deleteButton, cancelButton);
        confirmAlert.setTitle("Confirmer la suppression");
        confirmAlert.setHeaderText("Supprimer l'evenement: " + safe(evenementToDelete.getTitre()) + " ?");
        confirmAlert.setContentText("Cette action est irreversible.");

        applyAlertTheme(confirmAlert);
        styleAlertButton(confirmAlert, cancelButton, "secondary-action-button");
        styleAlertButton(confirmAlert, deleteButton, "danger-action-button");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != deleteButton) {
            return;
        }

        try {
            evenementService.delete(evenementToDelete);
            refreshEvenements();
        } catch (SQLDataException e) {
            showError("Suppression impossible", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(title);
        alert.setContentText(message);
        applyAlertTheme(alert);
        alert.showAndWait();
    }

    private void applyAlertTheme(Alert alert) {
        URL stylesheet = getClass().getResource("/views/styles/dashboardevent.css");
        if (stylesheet != null) {
            alert.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        alert.getDialogPane().getStyleClass().add("app-alert");
    }

    private void styleAlertButton(Alert alert, ButtonType buttonType, String styleClass) {
        Node node = alert.getDialogPane().lookupButton(buttonType);
        if (node instanceof Button button) {
            button.getStyleClass().add(styleClass);
            button.setMinWidth(110);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    private static LocalDateTime safeDateTime(Evenement evenement) {
        return evenement.getDateDebut() == null ? LocalDateTime.MIN : evenement.getDateDebut();
    }
}


