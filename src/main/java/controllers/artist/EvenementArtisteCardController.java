package controllers.artist;

import entities.Evenement;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.time.format.DateTimeFormatter;

public class EvenementArtisteCardController {

    public interface CardActionHandler {
        void onEdit(Evenement evenement);

        void onDelete(Evenement evenement);

        void onCancel(Evenement evenement);
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private ImageView coverImageView;

    @FXML
    private Label titreLabel;

    @FXML
    private Label dateLabel;

    @FXML
    private Label categoryLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label placesLabel;

    @FXML
    private Label descriptionLabel;

    private Evenement evenement;
    private CardActionHandler actionHandler;

    public void setData(Evenement evenement, CardActionHandler actionHandler) {
        this.evenement = evenement;
        this.actionHandler = actionHandler;

        titreLabel.setText(textOrDefault(evenement.getTitre(), "Evenement sans titre"));
        dateLabel.setText(evenement.getDateDebut() == null ? "Non définie" : DATE_FORMATTER.format(evenement.getDateDebut()));
        categoryLabel.setText(textOrDefault(evenement.getType(), "Type non précisé"));
        
        String statut = textOrDefault(evenement.getStatut(), "A venir");
        statusLabel.setText(statut);
        applyStatusStyle(statut);
        
        placesLabel.setText(evenement.getCapaciteMax() == null ? "-" : String.valueOf(evenement.getCapaciteMax()) + " places");
        descriptionLabel.setText(textOrDefault(evenement.getDescription(), "Aucune description"));
        applyImage(evenement.getImageCouverture());
    }

    @FXML
    private void onEditClick() {
        if (actionHandler != null && evenement != null) {
            actionHandler.onEdit(evenement);
        }
    }

    @FXML
    private void onCancelClick() {
        if (actionHandler != null && evenement != null) {
            actionHandler.onCancel(evenement);
        }
    }

    @FXML
    private void onDeleteClick() {
        if (actionHandler != null && evenement != null) {
            actionHandler.onDelete(evenement);
        }
    }

    private void applyStatusStyle(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (normalized.contains("annul")) {
            statusLabel.setStyle("-fx-background-color: #ffedd5; -fx-text-fill: #ea580c; -fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else if (normalized.contains("termin")) {
            statusLabel.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #059669; -fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else {
            statusLabel.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #2563eb; -fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;");
        }
    }

    private void applyImage(String imageSource) {
        if (imageSource == null || imageSource.isBlank()) {
            coverImageView.setImage(null);
            return;
        }

        try {
            Image image;
            if (imageSource.startsWith("http://") || imageSource.startsWith("https://") || imageSource.startsWith("file:")) {
                image = new Image(imageSource, true);
            } else {
                image = new Image(new File(imageSource).toURI().toString(), true);
            }
            if (image.isError()) {
                coverImageView.setImage(null);
                return;
            }
            coverImageView.setImage(image);
        } catch (Exception ignored) {
            coverImageView.setImage(null);
        }
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}


