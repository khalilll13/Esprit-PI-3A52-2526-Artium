package controllers.amateur;

import entities.Evenement;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

public class EventCardController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm");
    private static final String BADGE_EXPOSITION_CLASS = "amateur-event-badge-exposition";
    private static final String BADGE_CONCERT_CLASS = "amateur-event-badge-concert";
    private static final String BADGE_SPECTACLE_CLASS = "amateur-event-badge-spectacle";
    private static final String BADGE_CONFERENCE_CLASS = "amateur-event-badge-conference";

    @FXML
    private Region coverImageRegion;

    @FXML
    private Label categoryBadgeLabel;

    @FXML
    private Label titleLabel;

    @FXML
    private Label dateLabel;

    @FXML
    private Label placeLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label priceLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label scoreLabel;

    private Evenement evenement;
    private Consumer<Evenement> detailHandler;
    private double currentScoreOutOf10 = Double.NaN;

    public void setData(Evenement evenement) {
        this.evenement = evenement;
        String category = textOrDefault(evenement.getType(), "Evenement");
        categoryBadgeLabel.setText(category);
        applyCategoryBadgeStyle(category);
        titleLabel.setText(textOrDefault(evenement.getTitre(), "Evenement sans titre"));
        dateLabel.setText(formatDateTime(evenement.getDateDebut()));
        placeLabel.setText(formatCapacity(evenement.getCapaciteMax()));
        String status = textOrDefault(evenement.getStatut(), "A venir");
        statusLabel.setText(status);
        applyStatusStyle(status);
        
        priceLabel.setText(formatPrice(evenement.getPrixTicket()));
        descriptionLabel.setText(textOrDefault(evenement.getDescription(), ""));
        scoreLabel.setText(formatScore(currentScoreOutOf10));
        applyImage(evenement.getImageCouverture());
    }

    public void setScore(double scoreOutOf10) {
        this.currentScoreOutOf10 = scoreOutOf10;
        if (scoreLabel != null) {
            if (!scoreLabel.getStyleClass().contains("amateur-event-score")) {
                scoreLabel.getStyleClass().add("amateur-event-score");
            }
            scoreLabel.setText(formatScore(scoreOutOf10));
        }
    }

    public void setDetailHandler(Consumer<Evenement> detailHandler) {
        this.detailHandler = detailHandler;
    }

    @FXML
    private void onCardClick() {
        if (detailHandler != null && evenement != null) {
            detailHandler.accept(evenement);
        }
    }

    private void applyImage(String imageSource) {
        if (imageSource == null || imageSource.isBlank()) {
            coverImageRegion.setStyle("-fx-background-color: #f3f4f6; -fx-background-radius: 16 16 0 0;");
            return;
        }
        try {
            String uri;
            if (imageSource.startsWith("http://") || imageSource.startsWith("https://") || imageSource.startsWith("file:")) {
                uri = imageSource;
            } else {
                uri = new java.io.File(imageSource).toURI().toString();
            }
            coverImageRegion.setStyle("-fx-background-image: url('" + uri + "'); -fx-background-size: cover; -fx-background-position: center; -fx-background-color: #f3f4f6; -fx-background-radius: 16 16 0 0;");
        } catch (Exception e) {
            coverImageRegion.setStyle("-fx-background-color: #f3f4f6; -fx-background-radius: 16 16 0 0;");
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "Date non definie" : DATE_FORMATTER.format(dateTime);
    }

    private String formatCapacity(Integer capacity) {
        return capacity == null ? "Capacite non definie" : capacity + " places";
    }

    private String formatPrice(Double price) {
        return price == null ? "Prix non defini" : String.format("%.0f TND", price);
    }

    private String formatScore(double scoreOutOf10) {
        if (Double.isNaN(scoreOutOf10) || scoreOutOf10 < 0) {
            return "Match IA: -";
        }
        return String.format(Locale.ROOT, "Match IA: %.1f/10", scoreOutOf10);
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void applyStatusStyle(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (normalized.contains("annul")) {
            statusLabel.setStyle("-fx-background-color: #ffedd5; -fx-text-fill: #ea580c; -fx-padding: 2px 8px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else if (normalized.contains("termin")) {
            statusLabel.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #059669; -fx-padding: 2px 8px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else {
            statusLabel.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #2563eb; -fx-padding: 2px 8px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;");
        }
    }

    private void applyCategoryBadgeStyle(String category) {
        categoryBadgeLabel.getStyleClass().removeAll(
                BADGE_EXPOSITION_CLASS,
                BADGE_CONCERT_CLASS,
                BADGE_SPECTACLE_CLASS,
                BADGE_CONFERENCE_CLASS
        );

        String normalized = category == null ? "" : category.trim().toLowerCase();
        String baseStyle = "-fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 20px; -fx-border-radius: 20px; -fx-font-size: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1); ";
        
        switch (normalized) {
            case "exposition":
                categoryBadgeLabel.setStyle(baseStyle + "-fx-background-color: rgba(255, 255, 255, 0.94); -fx-text-fill: #1f2d3d;");
                break;
            case "concert":
                categoryBadgeLabel.setStyle(baseStyle + "-fx-background-color: linear-gradient(to right, #1d4ed8, #2563eb); -fx-text-fill: white;");
                break;
            case "spectacle":
                categoryBadgeLabel.setStyle(baseStyle + "-fx-background-color: linear-gradient(to right, #5b21b6, #7c3aed); -fx-text-fill: white;");
                break;
            case "conference":
            case "conférence":
                categoryBadgeLabel.setStyle(baseStyle + "-fx-background-color: linear-gradient(to right, #0f766e, #14b8a6); -fx-text-fill: white;");
                break;
            default:
                categoryBadgeLabel.setStyle(baseStyle + "-fx-background-color: rgba(17, 24, 39, 0.75); -fx-text-fill: white;");
                break;
        }
    }
}


