package controllers;

import entities.Galerie;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import services.GalerieService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLDataException;
import java.util.*;
import java.util.stream.Collectors;

public class GaleriesController {

    @FXML
    private TextField rechercheField;

    @FXML
    private ComboBox<String> filtreComboBox;

    @FXML
    private GridPane galeriesListContainer;

    @FXML
    private Label emptyStateLabel;

    @FXML
    private Button addGalerieButton;

    private final GalerieService galerieService = new GalerieService();
    private final List<Galerie> allGaleries = new ArrayList<>();

    @FXML
    public void initialize() {
        filtreComboBox.getItems().addAll("ID (A-Z)", "ID (Z-A)", "Nom (A-Z)", "Nom (Z-A)", "Capacite (croissant)", "Capacite (decroissant)");
        filtreComboBox.setValue("ID (Z-A)");

        rechercheField.textProperty().addListener((observable, oldValue, newValue) -> applySearchAndFilter());
        filtreComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySearchAndFilter());

        refreshGaleries();
    }

    @FXML
    private void onAddGalerieClick() {
        Optional<Galerie> galerieResult = openFormDialog(null);
        if (galerieResult.isEmpty()) {
            return;
        }

        try {
            galerieService.add(galerieResult.get());
            refreshGaleries();
        } catch (SQLDataException e) {
            showError("Ajout impossible", e.getMessage());
        }
    }

    private void refreshGaleries() {
        try {
            allGaleries.clear();
            allGaleries.addAll(galerieService.getAll());
            applySearchAndFilter();
        } catch (SQLDataException e) {
            showError("Chargement impossible", e.getMessage());
        }
    }

    private void applySearchAndFilter() {
        String search = rechercheField.getText() == null ? "" : rechercheField.getText().trim().toLowerCase();

        List<Galerie> filtered = allGaleries.stream()
                .filter(galerie -> matchesSearch(galerie, search))
                .sorted(buildComparator(filtreComboBox.getValue()))
                .collect(Collectors.toList());

        renderCards(filtered);
    }

    private boolean matchesSearch(Galerie galerie, String search) {
        if (search.isEmpty()) {
            return true;
        }

        return contains(galerie.getNom(), search)
                || contains(galerie.getAdresse(), search)
                || contains(galerie.getId() == null ? null : String.valueOf(galerie.getId()), search);
    }

    private Comparator<Galerie> buildComparator(String selectedFilter) {
        if (selectedFilter == null) {
            return Comparator.comparing(galerie -> galerie.getId() == null ? 0 : galerie.getId(), Comparator.reverseOrder());
        }

        return switch (selectedFilter) {
            case "ID (A-Z)" -> Comparator.comparing(galerie -> galerie.getId() == null ? 0 : galerie.getId());
            case "Nom (A-Z)" -> Comparator.comparing(galerie -> safe(galerie.getNom()));
            case "Nom (Z-A)" -> Comparator.comparing((Galerie galerie) -> safe(galerie.getNom())).reversed();
            case "Capacite (croissant)" -> Comparator.comparing(galerie -> galerie.getCapaciteMax() == null ? 0 : galerie.getCapaciteMax());
            case "Capacite (decroissant)" -> Comparator.comparing((Galerie galerie) -> galerie.getCapaciteMax() == null ? 0 : galerie.getCapaciteMax()).reversed();
            case "ID (Z-A)" -> Comparator.comparing((Galerie galerie) -> galerie.getId() == null ? 0 : galerie.getId()).reversed();
            default -> Comparator.comparing((Galerie galerie) -> galerie.getId() == null ? 0 : galerie.getId()).reversed();
        };
    }

    private void renderCards(List<Galerie> galeries) {
        galeriesListContainer.getChildren().clear();

        for (int index = 0; index < galeries.size(); index++) {
            Galerie galerie = galeries.get(index);
            try {
                FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/views/components/galerie-card.fxml")));
                Parent card = loader.load();
                GalerieCardController cardController = loader.getController();
                cardController.setData(galerie, new GalerieCardController.CardActionHandler() {
                    @Override
                    public void onEdit(Galerie galerieToEdit) {
                        handleEdit(galerieToEdit);
                    }

                    @Override
                    public void onDelete(Galerie galerieToDelete) {
                        handleDelete(galerieToDelete);
                    }
                });
                int column = index % 3;
                int row = index / 3;
                galeriesListContainer.add(card, column, row);
            } catch (IOException e) {
                showError("Affichage impossible", "Erreur pendant le rendu d'une carte galerie.");
                return;
            }
        }

        boolean empty = galeries.isEmpty();
        emptyStateLabel.setVisible(empty);
        emptyStateLabel.setManaged(empty);
    }

    private void handleEdit(Galerie galerieToEdit) {
        Optional<Galerie> galerieResult = openFormDialog(galerieToEdit);
        if (galerieResult.isEmpty()) {
            return;
        }

        try {
            galerieService.update(galerieResult.get());
            refreshGaleries();
        } catch (SQLDataException e) {
            showError("Modification impossible", e.getMessage());
        }
    }

    private void handleDelete(Galerie galerieToDelete) {
        ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "", deleteButton, cancelButton);
        confirmAlert.setTitle("Confirmer la suppression");
        confirmAlert.setHeaderText("Supprimer la galerie: " + safe(galerieToDelete.getNom()) + " ?");
        confirmAlert.setContentText("Cette action est irreversible.");

        applyAlertTheme(confirmAlert);
        styleAlertButton(confirmAlert, cancelButton, "secondary-action-button");
        styleAlertButton(confirmAlert, deleteButton, "danger-action-button");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != deleteButton) {
            return;
        }

        try {
            galerieService.delete(galerieToDelete);
            refreshGaleries();
        } catch (SQLDataException e) {
            showError("Suppression impossible", e.getMessage());
        }
    }

    private Optional<Galerie> openFormDialog(Galerie galerieToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/views/pages/galerie-form.fxml")));
            Parent formRoot = loader.load();
            GalerieFormController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            if (addGalerieButton != null && addGalerieButton.getScene() != null) {
                stage.initOwner(addGalerieButton.getScene().getWindow());
            }
            stage.setTitle(galerieToEdit == null ? "Ajouter une galerie" : "Modifier la galerie");

            Scene scene = new Scene(formRoot);
            URL stylesheet = getClass().getResource("/views/styles/dashboard.css");
            if (stylesheet != null) {
                scene.getStylesheets().add(stylesheet.toExternalForm());
            }
            stage.setScene(scene);

            controller.setDialogStage(stage);
            controller.setGalerie(galerieToEdit);

            stage.showAndWait();
            return Optional.ofNullable(controller.getResultGalerie());
        } catch (IOException e) {
            showError("Ouverture impossible", "Le formulaire de galerie est introuvable.");
            return Optional.empty();
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(title);
        alert.setContentText(message);
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
}

