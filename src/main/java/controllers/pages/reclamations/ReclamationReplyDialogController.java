package controllers.pages.reclamations;

import javafx.application.Platform;
import services.ReclamationService;
import services.ReponseService;
import services.NotificationService;
import services.OpenRouterReclamationReplyService;
import entities.Reclamation;
import entities.Reponse;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.image.BufferedImage;

public class ReclamationReplyDialogController {

    private static final Logger LOGGER = Logger.getLogger(ReclamationReplyDialogController.class.getName());

    @FXML
    private Label reclamationIdLabel;
    @FXML
    private TextArea reclamationTexteArea;
    @FXML
    private ListView<Reponse> historyList;

    @FXML
    private VBox replyFieldsBox;
    @FXML
    private Label validationErrorLabel;

    @FXML
    private Button saveBtn;
    @FXML
    private Button aiSuggestBtn;
    @FXML
    private Button updateBtn;
    @FXML
    private Button deleteBtn;
    @FXML
    private Button cancelBtn;

    private final ReponseService reponseService = new ReponseService();
    private final ReclamationService reclamationService = new ReclamationService();
    private final NotificationService notificationService = new NotificationService();
    private final OpenRouterReclamationReplyService aiReplyService = new OpenRouterReclamationReplyService();
    private Reclamation reclamation;
    private Reponse selectedForEdit;
    private List<Reponse> loadedHistory = new ArrayList<>();

    private boolean readOnly = false;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MIN_REPONSE_LEN = 10;
    private static final int MAX_REPONSE_LEN = 500;

    private static boolean isBlankOrTooShort(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty())
            return true;
        // on compte les caractères hors espaces pour éviter " "
        String noSpaces = v.replaceAll("\\s+", "");
        return noSpaces.length() < MIN_REPONSE_LEN;
    }

    private static boolean isTooLong(String value) {
        String v = value == null ? "" : value.trim();
        return v.length() > MAX_REPONSE_LEN;
    }

    @FXML
    public void initialize() {
        reclamationTexteArea.setEditable(false);
        reclamationTexteArea.setWrapText(true);
        clearValidationError();

        historyList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Reponse item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String d = item.getDateReponse() != null ? item.getDateReponse().format(DATE_FMT) : "";
                    setText("[" + d + "] " + (item.getContenu() == null ? "" : item.getContenu()));
                }
            }
        });

        historyList.setOnMouseClicked(e -> {
            if (readOnly)
                return;
            if (e.getClickCount() == 2 && historyList.getSelectionModel().getSelectedItem() != null) {
                selectedForEdit = historyList.getSelectionModel().getSelectedItem();
                fillEditField(selectedForEdit);
            }
        });

        addReplyField();
        updateBtn.setDisable(true);
        deleteBtn.setDisable(true);
        if (aiSuggestBtn != null) {
            aiSuggestBtn.setDisable(false);
        }
    }

    public void setReclamation(Reclamation reclamation) {
        this.reclamation = reclamation;
        reclamationIdLabel.setText(reclamation.getId() != null ? String.valueOf(reclamation.getId()) : "-");
        reclamationTexteArea.setText(reclamation.getTexte() != null ? reclamation.getTexte() : "");
        loadHistory();
        requestAiSuggestion(false);
    }

    /**
     * Mode lecture seule: l'utilisateur voit uniquement les réponses existantes.
     * - cache la zone de saisie
     * - désactive les actions d'édition
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;

        if (replyFieldsBox != null) {
            replyFieldsBox.setManaged(!readOnly);
            replyFieldsBox.setVisible(!readOnly);
        }
        if (saveBtn != null)
            saveBtn.setManaged(!readOnly);
        if (saveBtn != null)
            saveBtn.setVisible(!readOnly);

        if (aiSuggestBtn != null)
            aiSuggestBtn.setManaged(!readOnly);
        if (aiSuggestBtn != null)
            aiSuggestBtn.setVisible(!readOnly);
        if (aiSuggestBtn != null)
            aiSuggestBtn.setDisable(readOnly);

        if (updateBtn != null)
            updateBtn.setManaged(!readOnly);
        if (updateBtn != null)
            updateBtn.setVisible(!readOnly);
        if (updateBtn != null)
            updateBtn.setDisable(true);

        if (deleteBtn != null)
            deleteBtn.setManaged(!readOnly);
        if (deleteBtn != null)
            deleteBtn.setVisible(!readOnly);
        if (deleteBtn != null)
            deleteBtn.setDisable(true);

        if (reclamationTexteArea != null)
            reclamationTexteArea.setEditable(false);
    }

    @FXML
    private void onGenerateAiSuggestion() {
        requestAiSuggestion(true);
    }

    private void addReplyField() {
        TextArea ta = new TextArea();
        ta.setPromptText("Votre reponse...");
        ta.setWrapText(true);
        ta.setPrefRowCount(3);
        ta.textProperty().addListener((obs, ov, nv) -> clearValidationError());

        VBox wrapper = new VBox(6);
        wrapper.getChildren().add(ta);
        replyFieldsBox.getChildren().add(wrapper);
    }

    private void fillEditField(Reponse rep) {
        if (rep == null)
            return;
        clearValidationError();

        if (replyFieldsBox.getChildren().isEmpty()) {
            addReplyField();
        }

        VBox wrapper = (VBox) replyFieldsBox.getChildren().get(0);
        TextArea ta = null;
        for (var child : wrapper.getChildren()) {
            if (child instanceof TextArea t) {
                ta = t;
                break;
            }
        }

        if (ta != null) {
            ta.setText(rep.getContenu() != null ? rep.getContenu() : "");
            ta.requestFocus();
        }

        updateBtn.setDisable(false);
        deleteBtn.setDisable(false);
    }

    private void loadHistory() {
        if (reclamation == null || reclamation.getId() == null)
            return;
        try {
            List<Reponse> reps = reponseService.getByReclamationId(reclamation.getId());
            loadedHistory = reps == null ? new ArrayList<>() : new ArrayList<>(reps);
            historyList.getItems().setAll(reps);
        } catch (Exception e) {
            loadedHistory = new ArrayList<>();
            historyList.getItems().clear();
        }
    }

    private void requestAiSuggestion(boolean overwriteExisting) {
        if (readOnly || reclamation == null || reclamation.getId() == null) {
            return;
        }

        TextArea target = getPrimaryReplyField();
        if (!overwriteExisting && target != null && target.getText() != null && !target.getText().trim().isEmpty()) {
            return;
        }

        List<Reponse> historySnapshot = new ArrayList<>(loadedHistory);
        Thread worker = new Thread(() -> {
            try {
                String suggestion = aiReplyService.generateReplySuggestion(reclamation, historySnapshot);
                if (suggestion == null || suggestion.isBlank()) {
                    return;
                }

                Platform.runLater(() -> {
                    if (readOnly) {
                        return;
                    }

                    TextArea replyField = getPrimaryReplyField();
                    if (replyField != null && (overwriteExisting || replyField.getText() == null
                            || replyField.getText().trim().isEmpty())) {
                        replyField.setText(suggestion);
                        replyField.positionCaret(suggestion.length());
                        clearValidationError();
                    }
                });
            } catch (Exception e) {
            }
        }, "hf-reclamation-reply-suggestion");
        worker.setDaemon(true);
        worker.start();
    }

    private TextArea getPrimaryReplyField() {
        if (replyFieldsBox == null || replyFieldsBox.getChildren().isEmpty()) {
            return null;
        }

        for (var node : replyFieldsBox.getChildren()) {
            if (node instanceof VBox wrapper) {
                for (var child : wrapper.getChildren()) {
                    if (child instanceof TextArea textArea) {
                        return textArea;
                    }
                }
            }
        }

        return null;
    }

    private void markReclamationTraite() {
        if (reclamation == null || reclamation.getId() == null)
            return;
        try {
            // Update only statut to avoid failing because of other columns
            reclamationService.updateStatutById(reclamation.getId(), "Traitée");
            reclamation.setStatut("Traitée");
        } catch (Exception ignored) {
        }
    }

    private void syncStatutWithReponses() {
        if (reclamation == null || reclamation.getId() == null)
            return;
        try {
            List<Reponse> reps = reponseService.getByReclamationId(reclamation.getId());
            boolean hasReps = reps != null && !reps.isEmpty();

            String target = hasReps ? "Traitée" : "Non traitée";
            reclamationService.updateStatutById(reclamation.getId(), target);
            reclamation.setStatut(target);
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void onSave() {
        if (reclamation == null || reclamation.getId() == null) {
            showError("Reclamation invalide", "Aucun ID.");
            return;
        }

        List<String> contenus = new ArrayList<>();
        for (var node : replyFieldsBox.getChildren()) {
            if (node instanceof VBox v) {
                for (var child : v.getChildren()) {
                    if (child instanceof TextArea ta) {
                        String txt = ta.getText() == null ? "" : ta.getText();
                        // Ne pas ajouter les réponses vides (espaces) dans la liste
                        if (!txt.trim().isEmpty())
                            contenus.add(txt.trim());
                    }
                }
            }
        }

        for (String c : contenus) {
            if (isBlankOrTooShort(c)) {
                showValidationError("La reponse ne peut pas etre vide et doit contenir au moins " + MIN_REPONSE_LEN
                        + " caracteres.");
                return;
            }
            if (isTooLong(c)) {
                showValidationError("La reponse ne peut pas depasser " + MAX_REPONSE_LEN + " caracteres.");
                return;
            }
        }

        if (contenus.isEmpty()) {
            showValidationError("Veuillez saisir une reponse avant d'enregistrer.");
            return;
        }

        try {
            for (String c : contenus) {
                Reponse r = new Reponse();
                r.setReclamationId(reclamation.getId());
                r.setContenu(c);
                r.setDateReponse(LocalDate.now());
                reponseService.add(r);

            }

            markReclamationTraite();
            clearValidationError();

            // Envoyer une notification in-app à l'utilisateur
            if (reclamation.getUserId() != null) {
                notificationService.sendReclamationReplyNotice(reclamation.getUserId(), reclamation.getId());
            }

            // Envoyer une notification push à l'utilisateur
            sendPushNotification(reclamation);

            close();
        } catch (Exception e) {
            showError("Enregistrement impossible", e.getMessage());
        }
    }

    @FXML
    private void onUpdate() {
        if (selectedForEdit == null || selectedForEdit.getId() == null) {
            showError("Edition", "Veuillez selectionner une reponse a modifier (double clic).");
            return;
        }

        if (replyFieldsBox.getChildren().isEmpty()) {
            showError("Edition", "Aucun champ de saisie.");
            return;
        }

        VBox wrapper = (VBox) replyFieldsBox.getChildren().get(0);
        TextArea ta = null;
        for (var child : wrapper.getChildren()) {
            if (child instanceof TextArea t) {
                ta = t;
                break;
            }
        }
        if (ta == null) {
            showError("Edition", "Champ introuvable.");
            return;
        }

        String newText = ta.getText() == null ? "" : ta.getText();
        if (isBlankOrTooShort(newText)) {
            showValidationError(
                    "La reponse ne peut pas etre vide et doit contenir au moins " + MIN_REPONSE_LEN + " caracteres.");
            return;
        }
        if (isTooLong(newText)) {
            showValidationError("La reponse ne peut pas depasser " + MAX_REPONSE_LEN + " caracteres.");
            return;
        }

        try {
            selectedForEdit.setContenu(newText.trim());
            selectedForEdit.setDateReponse(LocalDate.now());
            reponseService.update(selectedForEdit);
            loadHistory();

            markReclamationTraite();

            // Envoyer une notification in-app à l'utilisateur
            if (reclamation.getUserId() != null) {
                notificationService.sendReclamationReplyNotice(reclamation.getUserId(), reclamation.getId());
            }

            // Envoyer une notification push à l'utilisateur
            sendPushNotification(reclamation);

            selectedForEdit = null;
            updateBtn.setDisable(true);
            ta.clear();
            clearValidationError();
        } catch (Exception e) {
            showError("Modification impossible", e.getMessage());
        }
    }

    private void showValidationError(String message) {
        if (validationErrorLabel == null) {
            showError("Validation", message);
            return;
        }
        validationErrorLabel.setText(message);
        validationErrorLabel.setVisible(true);
        validationErrorLabel.setManaged(true);
    }

    private void clearValidationError() {
        if (validationErrorLabel == null) {
            return;
        }
        validationErrorLabel.setText("");
        validationErrorLabel.setVisible(false);
        validationErrorLabel.setManaged(false);
    }

    @FXML
    private void onDeleteReponse() {
        Reponse selected = historyList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getId() == null) {
            showError("Suppression", "Veuillez selectionner une reponse a supprimer.");
            return;
        }

        ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "", deleteButton, cancelButton);
        confirm.setTitle("Confirmer");
        confirm.setHeaderText("Supprimer cette reponse ?");
        confirm.setContentText("Cette action est irreversible.");

        if (confirm.showAndWait().orElse(cancelButton) != deleteButton)
            return;

        try {
            reponseService.deleteById(selected.getId());
            loadHistory();

            syncStatutWithReponses();

            historyList.getSelectionModel().clearSelection();

            // reset edit mode
            selectedForEdit = null;
            updateBtn.setDisable(true);
            deleteBtn.setDisable(true);
        } catch (Exception e) {
            showError("Suppression impossible", e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void close() {
        Stage stage = (Stage) cancelBtn.getScene().getWindow();
        stage.close();
    }

    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.showAndWait();
    }

    private void sendPushNotification(Reclamation reclamation) {
        if (SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                java.awt.Image image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                TrayIcon trayIcon = new TrayIcon(image, "Notification Artium");
                trayIcon.setImageAutoSize(true);
                trayIcon.setToolTip("Notification");
                tray.add(trayIcon);

                entities.User user = null;
                try {
                    user = new services.JdbcUserService().getById(reclamation.getUserId());
                } catch (Exception ex) {
                }

                String roleText = "";
                String userName = "";
                if (user != null) {
                    String role = user.getRole() != null ? user.getRole().toLowerCase() : "";
                    userName = (user.getNom() + " " + user.getPrenom()).trim();
                    if (role.contains("artiste")) {
                        roleText = "l'artiste :";
                    } else {
                        roleText = "l'amateur :";
                    }
                }

                String message = "reponse a la reclamation de " + roleText + userName;
                trayIcon.displayMessage("Réponse à votre réclamation", message, MessageType.INFO);

                final entities.User finalUser = user;
                trayIcon.addActionListener(e -> {
                    if (finalUser != null) {
                        javafx.application.Platform.runLater(() -> {
                            String role = finalUser.getRole() != null ? finalUser.getRole().toLowerCase() : "";
                            utils.SessionManager.setCurrentUser(finalUser);
                            if (role.contains("artiste")) {
                                controllers.MainFX.switchToArtistView(finalUser);
                            } else {
                                controllers.MainFX.switchToAmateurView(finalUser);
                            }
                        });
                    }
                });

                // Retirer l'icône après l'affichage pour éviter l'encombrement
                new Thread(() -> {
                    try {
                        Thread.sleep(8000);
                        tray.remove(trayIcon);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }).start();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erreur lors de l'envoi de la notification push", e);
            }
        }
    }
}
