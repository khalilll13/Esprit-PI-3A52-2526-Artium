package controllers;

import entities.Galerie;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class GalerieCardController {

    public interface CardActionHandler {
        void onEdit(Galerie galerie);

        void onDelete(Galerie galerie);
    }

    @FXML
    private Label nomLabel;

    @FXML
    private Label capaciteLabel;

    @FXML
    private Label adresseLabel;

    @FXML
    private Label localisationLabel;

    @FXML
    private Label idValueLabel;

    @FXML
    private Label descriptionValueLabel;

    @FXML
    private VBox detailsBox;

    @FXML
    private Button detailsButton;

    private Galerie galerie;
    private CardActionHandler actionHandler;
    private boolean detailsVisible;

    public void setData(Galerie galerie, CardActionHandler actionHandler) {
        this.galerie = galerie;
        this.actionHandler = actionHandler;

        nomLabel.setText(galerie.getNom());
        capaciteLabel.setText("Capacite max: " + galerie.getCapaciteMax());
        adresseLabel.setText("Adresse: " + galerie.getAdresse());
        localisationLabel.setText("Localisation: " + galerie.getLocalisation());
        idValueLabel.setText(galerie.getId() == null ? "-" : String.valueOf(galerie.getId()));
        descriptionValueLabel.setText(galerie.getDescription() == null || galerie.getDescription().isBlank()
                ? "Aucune description"
                : galerie.getDescription());

        setDetailsVisible(false);
    }

    @FXML
    private void onToggleDetails() {
        setDetailsVisible(!detailsVisible);
    }

    @FXML
    private void onEditClick() {
        if (actionHandler != null && galerie != null) {
            actionHandler.onEdit(galerie);
        }
    }

    @FXML
    private void onDeleteClick() {
        if (actionHandler != null && galerie != null) {
            actionHandler.onDelete(galerie);
        }
    }

    private void setDetailsVisible(boolean visible) {
        detailsVisible = visible;
        detailsBox.setManaged(visible);
        detailsBox.setVisible(visible);
        detailsButton.setText(visible ? "Masquer details" : "Voir details");
    }
}

