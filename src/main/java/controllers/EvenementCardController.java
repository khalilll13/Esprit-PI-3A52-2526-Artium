package controllers;

import entities.Evenement;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class EvenementCardController {

    public interface CardActionHandler {
        void onDelete(Evenement evenement);
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private Label titreLabel;

    @FXML
    private Label typeLabel;

    @FXML
    private Label statutLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label dateDebutLabel;

    @FXML
    private Label dateFinLabel;

    @FXML
    private Label dateCreationLabel;

    @FXML
    private Label capaciteLabel;

    @FXML
    private Label prixLabel;

    @FXML
    private Label relationLabel;

    @FXML
    private VBox detailsBox;

    @FXML
    private Button detailsButton;

    private Evenement evenement;
    private CardActionHandler actionHandler;
    private boolean detailsVisible;

    public void setData(Evenement evenement, CardActionHandler actionHandler) {
        this.evenement = evenement;
        this.actionHandler = actionHandler;

        titreLabel.setText(textOrDefault(evenement.getTitre(), "Evenement sans titre"));
        typeLabel.setText("Type: " + textOrDefault(evenement.getType(), "Non precise"));
        statutLabel.setText("Statut: " + textOrDefault(evenement.getStatut(), "Non precise"));
        descriptionLabel.setText(textOrDefault(evenement.getDescription(), "Aucune description"));
        dateDebutLabel.setText("Debut: " + formatDateTime(evenement.getDateDebut()));
        dateFinLabel.setText("Fin: " + formatDateTime(evenement.getDateFin()));
        dateCreationLabel.setText("Creation: " + formatDate(evenement.getDateCreation()));
        capaciteLabel.setText("Capacite max: " + formatNumber(evenement.getCapaciteMax()));
        prixLabel.setText("Prix ticket: " + formatPrice(evenement.getPrixTicket()));
        relationLabel.setText("Galerie ID: " + formatNumber(evenement.getGalerieId()) + "  |  Artiste ID: " + formatNumber(evenement.getArtisteId()));

        setDetailsVisible(false);
    }

    @FXML
    private void onToggleDetails() {
        setDetailsVisible(!detailsVisible);
    }

    @FXML
    private void onDeleteClick() {
        if (actionHandler != null && evenement != null) {
            actionHandler.onDelete(evenement);
        }
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME_FORMATTER.format(value);
    }

    private String formatDate(LocalDate value) {
        return value == null ? "-" : DATE_FORMATTER.format(value);
    }

    private String formatNumber(Number value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String formatPrice(Double value) {
        return value == null ? "-" : String.format("%.2f", value);
    }

    private void setDetailsVisible(boolean visible) {
        detailsVisible = visible;
        detailsBox.setManaged(visible);
        detailsBox.setVisible(visible);
        detailsButton.setText(visible ? "Masquer details" : "Voir details");
    }
}



