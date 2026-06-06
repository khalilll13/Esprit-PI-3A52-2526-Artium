package controllers.pages.reclamations;

import entities.Reclamation;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.text.Normalizer;


public class ReclamationCardController {

    public interface CardActionHandler {
        void onReply(Reclamation reclamation);
        void onDelete(Reclamation reclamation);
        void onArchive(Reclamation reclamation);
    }

    @FXML private Label texteLabel;
    @FXML private Label dateLabel;
    @FXML private Label userLabel;
    @FXML private Label typeLabel;
    @FXML private Label statutLabel;
    @FXML private Button dotsButton;

    private Reclamation reclamation;
    private CardActionHandler actionHandler;

    public void setData(Reclamation reclamation, String userText, String dateText, CardActionHandler actionHandler) {
        this.reclamation = reclamation;
        this.actionHandler = actionHandler;

        texteLabel.setText(truncate(reclamation.getTexte(), 55));
        dateLabel.setText(dateText == null ? "" : dateText);
        userLabel.setText(userText == null ? "-" : userText);
        typeLabel.setText(reclamation.getType() == null ? "" : reclamation.getType());
        String statut = reclamation.getStatut() == null ? "" : reclamation.getStatut();
        statutLabel.setText(statut);
        applyStatutBadgeStyle(statut);

        initMenu();
    }

    private void applyStatutBadgeStyle(String statut) {
        if (statutLabel == null) return;

        statutLabel.getStyleClass().removeAll("traite", "nontraite");
        String s = statut == null ? "" : normalize(statut);

        // IMPORTANT: "non traite" contient le mot "traite" => on detecte d'abord le NON.
        boolean isNon = s.contains("non")
                || s.contains("en cours")
                || s.contains("pending")
                || s.contains("Non traitée")
                || s.contains("nontraite");
        boolean isTraite = !isNon && (s.contains("traite") || s.contains("resolu") || s.contains("resolved") || s.contains("done"));

        if (isTraite) {
            statutLabel.getStyleClass().add("traite");
        } else {
            statutLabel.getStyleClass().add("nontraite");
        }
    }

    private static String normalize(String value) {
        String s = value == null ? "" : value;
        s = s.toLowerCase().replace("_", " ").replace("-", " ");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", ""); // remove accents
        while (s.contains("  ")) s = s.replace("  ", " ");
        return s.trim();
    }

    private void initMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("reclamation-actions-menu");

        MenuItem reply = new MenuItem("Repondre");
        reply.getStyleClass().add("reclamation-actions-reply");

        MenuItem archive = new MenuItem("Archiver");
        archive.getStyleClass().add("reclamation-actions-archive");

        MenuItem delete = new MenuItem("Supprimer");
        delete.getStyleClass().add("reclamation-actions-delete");

        // "Archiver" option is only available if status is Traitée
        boolean isTraite = false;
        if (reclamation != null && reclamation.getStatut() != null) {
            String s = normalize(reclamation.getStatut());
            boolean isNon = s.contains("non") || s.contains("en cours") || s.contains("pending") || s.contains("nontraite");
            if (!isNon && (s.contains("traite") || s.contains("resolu") || s.contains("resolved") || s.contains("done"))) {
                isTraite = true;
            }
        }

        if (isTraite && !Boolean.TRUE.equals(reclamation.getIsArchived())) {
            menu.getItems().addAll(reply, archive, delete);
        } else {
            menu.getItems().addAll(reply, delete);
        }

        dotsButton.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                menu.show(dotsButton, e.getScreenX(), e.getScreenY());
            }
        });

        reply.setOnAction(e -> {
            if (actionHandler != null && reclamation != null) actionHandler.onReply(reclamation);
        });

        archive.setOnAction(e -> {
            if (actionHandler != null && reclamation != null) actionHandler.onArchive(reclamation);
        });

        delete.setOnAction(e -> {
            if (actionHandler != null && reclamation != null) actionHandler.onDelete(reclamation);
        });
    }

    public void openReplyDialog(Reclamation reclamation) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/pages/reclamation_reply_dialog.fxml"));
            Parent root = loader.load();

            ReclamationReplyDialogController controller = loader.getController();
            controller.setReclamation(reclamation);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Repondre a la reclamation #" + reclamation.getId());

            Scene scene = new Scene(root);
            // Important: appliquer explicitement le CSS du dialog + le theme global
            URL dialogCss = getClass().getResource("/views/styles/reclamation-reply-dialog.css");
            if (dialogCss != null) scene.getStylesheets().add(dialogCss.toExternalForm());

            URL appCss = getClass().getResource("/views/styles/dashboardreclam.css");
            if (appCss != null) scene.getStylesheets().add(appCss.toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Erreur");
            a.setHeaderText("Ouverture impossible");
            a.setContentText(ex.getMessage());
            a.showAndWait();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 3)) + "...";
    }
}

