package controllers.artist;

import services.OeuvreCollectionService;
import services.OeuvreService;
import entities.CollectionOeuvre;
import entities.Oeuvre;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Side;
import javafx.stage.Modality;
import javafx.stage.Stage;
import utils.UserSession;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CollectionsController {
    private static final int DESCRIPTION_MIN_LENGTH = 10;
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int THUMBNAIL_SIZE = 74;
    private static final String CHEVRON_UP = "M7.41 14.59 12 10l4.59 4.59L18 13.17l-6-6-6 6z";
    private static final String CHEVRON_DOWN = "M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z";


    @FXML
    private TextField searchField;

    @FXML
    private Button addCollectionButton;

    @FXML
    private VBox collectionsContainer;

    @FXML
    private Label emptyStateLabel;

    private final OeuvreCollectionService oeuvreCollectionService = new OeuvreCollectionService();
    private final OeuvreService oeuvreService = new OeuvreService();
    private final List<CollectionOeuvre> allCollections = new ArrayList<>();
    private final Map<Integer, Boolean> expandedByCollectionId = new HashMap<>();
    private final Map<Integer, List<Oeuvre>> oeuvresByCollectionId = new HashMap<>();

    private Integer artisteId;

    @FXML
    public void initialize() {
        artisteId = UserSession.getCurrentUserId();
        if (artisteId == null) {
            handleMissingSession();
            return;
        }
        applyIcons();
        loadCollections();
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter(newValue));
    }

    private void handleMissingSession() {
        collectionsContainer.getChildren().clear();
        emptyStateLabel.setText("Session utilisateur introuvable. Veuillez vous reconnecter.");
        emptyStateLabel.setVisible(true);
        if (addCollectionButton != null) {
            addCollectionButton.setDisable(true);
        }
    }

    private void applyIcons() {
        if (addCollectionButton != null) {
            addCollectionButton.setGraphic(createIcon("M19 11H13V5h-2v6H5v2h6v6h2v-6h6z", 0.72, "#ffffff"));
            addCollectionButton.setGraphicTextGap(6);
        }
    }

    private SVGPath createIcon(String path, double scale, String fillColor) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setScaleX(scale);
        icon.setScaleY(scale);
        icon.setStyle("-fx-fill: " + fillColor + ";");
        return icon;
    }

    @FXML
    private void onAddCollectionClick() {
        showCollectionPopup(null);
    }

    private void showEditCollectionPopup(CollectionOeuvre collection) {
        showCollectionPopup(collection);
    }

    private void showCollectionPopup(CollectionOeuvre collectionToEdit) {
        boolean editMode = collectionToEdit != null;
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.initOwner(searchField.getScene().getWindow());
        popupStage.setTitle(editMode ? "Modifier la collection" : "Ajouter une collection");

        Label headerLabel = new Label(editMode ? "Modifier la Collection" : "Ajouter une Collection");
        headerLabel.getStyleClass().add("popup-title");

        Label titreLabel = new Label("Titre");
        titreLabel.getStyleClass().add("popup-field-label");
        TextField titreField = new TextField();
        titreField.setPromptText("Entrez le titre");
        titreField.getStyleClass().add("popup-input");

        Label titreErrorLabel = new Label();
        titreErrorLabel.getStyleClass().add("popup-error");
        titreErrorLabel.setVisible(false);
        titreErrorLabel.setManaged(false);

        Label descriptionLabel = new Label("Description");
        descriptionLabel.getStyleClass().add("popup-field-label");
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Entrez la description");
        descriptionArea.getStyleClass().add("popup-input");
        descriptionArea.setPrefRowCount(4);

        if (editMode) {
            titreField.setText(collectionToEdit.getTitre() == null ? "" : collectionToEdit.getTitre());
            descriptionArea.setText(collectionToEdit.getDescription() == null ? "" : collectionToEdit.getDescription());
        }

        Label descriptionErrorLabel = new Label();
        descriptionErrorLabel.getStyleClass().add("popup-error");
        descriptionErrorLabel.setVisible(false);
        descriptionErrorLabel.setManaged(false);

        Label descriptionCounterLabel = new Label("0/" + DESCRIPTION_MAX_LENGTH);
        descriptionCounterLabel.getStyleClass().add("popup-counter");

        descriptionCounterLabel.setText((descriptionArea.getText() == null ? 0 : descriptionArea.getText().length()) + "/" + DESCRIPTION_MAX_LENGTH);

        HBox descriptionMetaRow = new HBox(8);
        descriptionMetaRow.setAlignment(Pos.CENTER_LEFT);
        Region descriptionMetaSpacer = new Region();
        HBox.setHgrow(descriptionMetaSpacer, Priority.ALWAYS);
        descriptionMetaRow.getChildren().addAll(descriptionErrorLabel, descriptionMetaSpacer, descriptionCounterLabel);

        titreField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (normalizeText(newValue).isEmpty()) {
                showFieldError(titreErrorLabel, titreField, "Le titre est obligatoire.");
            } else {
                clearFieldError(titreErrorLabel, titreField);
            }
        });

        descriptionArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > DESCRIPTION_MAX_LENGTH) {
                descriptionArea.setText(newValue.substring(0, DESCRIPTION_MAX_LENGTH));
                return;
            }
            int length = descriptionArea.getText() == null ? 0 : descriptionArea.getText().length();
            descriptionCounterLabel.setText(length + "/" + DESCRIPTION_MAX_LENGTH);
            descriptionCounterLabel.getStyleClass().remove("popup-counter-limit");
            if (length >= DESCRIPTION_MAX_LENGTH) {
                descriptionCounterLabel.getStyleClass().add("popup-counter-limit");
            }
            validateDescriptionField(descriptionArea, descriptionErrorLabel);
        });

        Button closeButton = new Button("Fermer");
        closeButton.getStyleClass().add("popup-close-button");
        closeButton.setGraphic(createIcon("M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z", 0.52, "#ffffff"));
        closeButton.setGraphicTextGap(6);
        closeButton.setOnAction(event -> popupStage.close());

        Button confirmButton = new Button(editMode ? "Enregistrer" : "Ajouter Collection");
        confirmButton.getStyleClass().add("popup-confirm-button");
        confirmButton.setGraphic(createIcon("M9 16.17 4.83 12 3.41 13.41 9 19l12-12-1.41-1.41z", 0.58, "#ffffff"));
        confirmButton.setGraphicTextGap(6);
        confirmButton.setOnAction(event -> {
            String titre = titreField.getText() == null ? "" : titreField.getText().trim();
            String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();

            clearFieldError(titreErrorLabel, titreField);
            clearFieldError(descriptionErrorLabel, descriptionArea);

            boolean hasErrors = false;

            hasErrors |= !validateTitreField(titreField, titreErrorLabel);

            if (!validateDescriptionField(descriptionArea, descriptionErrorLabel)) {
                hasErrors = true;
            }

            if (hasErrors) {
                return;
            }

            try {
                boolean alreadyExists = oeuvreCollectionService.existsByTitreAndArtisteId(titre, artisteId);
                boolean isSameCollectionTitle = editMode
                        && normalizeText(titre).equals(normalizeText(collectionToEdit.getTitre()));

                if (alreadyExists && !isSameCollectionTitle) {
                    showFieldError(titreErrorLabel, titreField, "Cette collection existe deja pour cet artiste.");
                    return;
                }

                if (editMode) {
                    collectionToEdit.setTitre(titre);
                    collectionToEdit.setDescription(description);
                    oeuvreCollectionService.update(collectionToEdit);
                } else {
                    CollectionOeuvre collection = new CollectionOeuvre();
                    collection.setTitre(titre);
                    collection.setDescription(description);
                    collection.setArtisteId(artisteId);
                    oeuvreCollectionService.add(collection);
                }

                popupStage.close();
                loadCollections();
                applyFilter(searchField.getText());
            } catch (SQLException e) {
                String message = e.getMessage() == null ? "Une erreur est survenue." : e.getMessage();
                String messageLower = message.toLowerCase();
                if (messageLower.contains("description")) {
                    showFieldError(descriptionErrorLabel, descriptionArea, message);
                } else {
                    showFieldError(titreErrorLabel, titreField, message);
                }
            }
        });

        HBox footer = new HBox(10, closeButton, confirmButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10,
                headerLabel,
                titreLabel,
                titreField,
                titreErrorLabel,
                descriptionLabel,
                descriptionArea,
                descriptionMetaRow,
                footer
        );
        root.setPadding(new Insets(16));
        root.getStyleClass().add("collection-popup");

        Scene scene = new Scene(root, 500, 410);
        scene.getStylesheets().addAll(searchField.getScene().getStylesheets());

        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private ContextMenu buildCollectionActionsMenu(CollectionOeuvre collection) {
        MenuItem editItem = new MenuItem("Modifier");
        editItem.getStyleClass().add("collection-menu-edit");
        editItem.setGraphic(createIcon("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z", 0.58, "#6b7280"));
        editItem.setOnAction(event -> showEditCollectionPopup(collection));

        MenuItem deleteItem = new MenuItem("Supprimer");
        deleteItem.getStyleClass().add("collection-menu-delete");
        deleteItem.setGraphic(createIcon("M6 7h12v2H6V7zm2 3h8v10H8V10zm3-6h2l1 1h4v2H6V5h4l1-1z", 0.58, "#dc3545"));
        deleteItem.setOnAction(event -> onDeleteCollection(collection));

        ContextMenu contextMenu = new ContextMenu(editItem, deleteItem);
        contextMenu.getStyleClass().add("collection-menu");
        return contextMenu;
    }

    private void onDeleteCollection(CollectionOeuvre collection) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(searchField.getScene().getWindow());
        confirmation.setTitle("Supprimer la collection");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Voulez-vous supprimer la collection '" + collection.getTitre() + "' ?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            oeuvreCollectionService.delete(collection);
            loadCollections();
            applyFilter(searchField.getText());
        } catch (SQLException e) {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.initOwner(searchField.getScene().getWindow());
            errorAlert.setTitle("Erreur");
            errorAlert.setHeaderText("Suppression impossible");
            errorAlert.setContentText(e.getMessage() == null ? "Une erreur est survenue." : e.getMessage());
            errorAlert.showAndWait();
        }
    }

    private boolean validateDescriptionField(TextArea descriptionArea, Label descriptionErrorLabel) {
        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();
        if (description.isEmpty()) {
            showFieldError(descriptionErrorLabel, descriptionArea, "La description est obligatoire.");
            return false;
        }
        if (description.length() < DESCRIPTION_MIN_LENGTH) {
            showFieldError(descriptionErrorLabel, descriptionArea, "La description doit contenir au moins " + DESCRIPTION_MIN_LENGTH + " caracteres.");
            return false;
        }
        if (description.length() > DESCRIPTION_MAX_LENGTH) {
            showFieldError(descriptionErrorLabel, descriptionArea, "La description ne doit pas depasser " + DESCRIPTION_MAX_LENGTH + " caracteres.");
            return false;
        }
        clearFieldError(descriptionErrorLabel, descriptionArea);
        return true;
    }

    private boolean validateTitreField(TextField titreField, Label titreErrorLabel) {
        String titre = titreField.getText() == null ? "" : titreField.getText().trim();
        if (titre.isEmpty()) {
            showFieldError(titreErrorLabel, titreField, "Le titre est obligatoire.");
            return false;
        }
        clearFieldError(titreErrorLabel, titreField);
        return true;
    }

    private void showFieldError(Label errorLabel, Control field, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        field.getStyleClass().remove("popup-input-valid");
        if (!field.getStyleClass().contains("popup-input-invalid")) {
            field.getStyleClass().add("popup-input-invalid");
        }
    }

    private void clearFieldError(Label errorLabel, Control field) {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        field.getStyleClass().remove("popup-input-invalid");
        if (!field.getStyleClass().contains("popup-input-valid")) {
            field.getStyleClass().add("popup-input-valid");
        }
    }

    private void loadCollections() {
        try {
            allCollections.clear();
            allCollections.addAll(oeuvreCollectionService.getCollectionsByArtisteId(artisteId));
            oeuvresByCollectionId.clear();
            renderCollections(allCollections);
        } catch (SQLException e) {
            emptyStateLabel.setText("Erreur lors du chargement des collections: " + e.getMessage());
            emptyStateLabel.setVisible(true);
            collectionsContainer.getChildren().clear();
        }
    }

    private void applyFilter(String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        if (query.isEmpty()) {
            renderCollections(allCollections);
            return;
        }

        List<CollectionOeuvre> filtered = new ArrayList<>();
        for (CollectionOeuvre collection : allCollections) {
            String titre = collection.getTitre() == null ? "" : collection.getTitre().toLowerCase();
            String description = collection.getDescription() == null ? "" : collection.getDescription().toLowerCase();
            if (titre.contains(query) || description.contains(query)) {
                filtered.add(collection);
            }
        }
        renderCollections(filtered);
    }

    private void toggleCollectionPreview(CollectionOeuvre collection, VBox previewBox, Button expandButton) {
        Integer collectionId = collection.getId();
        if (collectionId == null) {
            return;
        }

        boolean nextExpanded = !expandedByCollectionId.getOrDefault(collectionId, false);
        expandedByCollectionId.put(collectionId, nextExpanded);
        updateExpandButtonIcon(expandButton, nextExpanded);
        Separator divider = (Separator) previewBox.getProperties().get("collectionContentDivider");
        refreshCollectionPreview(collectionId, previewBox, divider, nextExpanded);
    }

    private void refreshCollectionPreview(Integer collectionId, VBox previewBox, Separator divider, boolean expanded) {
        if (divider != null) {
            divider.setManaged(expanded);
            divider.setVisible(expanded);
        }
        previewBox.getChildren().clear();
        previewBox.setManaged(expanded);
        previewBox.setVisible(expanded);

        if (!expanded || collectionId == null) {
            return;
        }

        List<Oeuvre> oeuvres = oeuvresByCollectionId.get(collectionId);
        if (oeuvres == null) {
            try {
                oeuvres = oeuvreService.getOeuvresByCollectionId(collectionId);
                oeuvresByCollectionId.put(collectionId, oeuvres);
            } catch (SQLException e) {
                Label errorLabel = new Label("Impossible de charger les images.");
                errorLabel.getStyleClass().add("collection-preview-empty");
                previewBox.getChildren().add(errorLabel);
                return;
            }
        }

        if (oeuvres.isEmpty()) {
            Label emptyLabel = new Label("Aucune oeuvre avec image.");
            emptyLabel.getStyleClass().add("collection-preview-empty");
            previewBox.getChildren().add(emptyLabel);
            return;
        }

        HBox thumbnailsRow = new HBox(10);
        thumbnailsRow.getStyleClass().add("collection-preview-row");
        for (Oeuvre oeuvre : oeuvres) {
            StackPane thumbWrapper = createDraggableThumbnail(oeuvre, collectionId);
            if (thumbWrapper == null) {
                continue;
            }
            thumbnailsRow.getChildren().add(thumbWrapper);
        }

        if (thumbnailsRow.getChildren().isEmpty()) {
            Label emptyLabel = new Label("Aucune miniature valide.");
            emptyLabel.getStyleClass().add("collection-preview-empty");
            previewBox.getChildren().add(emptyLabel);
            return;
        }

        previewBox.getChildren().add(thumbnailsRow);
    }

    private StackPane createDraggableThumbnail(Oeuvre oeuvre, Integer sourceCollectionId) {
        ImageView thumbnail = createThumbnailImageView(oeuvre == null ? null : oeuvre.getImage());
        if (thumbnail == null || oeuvre.getId() == null) {
            return null;
        }

        StackPane thumbWrapper = new StackPane(thumbnail);
        thumbWrapper.getStyleClass().add("collection-thumbnail-wrapper");
        thumbWrapper.setOnDragDetected(event -> {
            Dragboard dragboard = thumbWrapper.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(oeuvre.getId() + ":" + sourceCollectionId);
            dragboard.setContent(content);
            event.consume();
        });
        return thumbWrapper;
    }

    private void configureCollectionDropTarget(VBox collectionItem, CollectionOeuvre targetCollection) {
        Integer targetCollectionId = targetCollection == null ? null : targetCollection.getId();
        collectionItem.setOnDragOver(event -> {
            if (canAcceptMove(event.getDragboard(), targetCollectionId)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        collectionItem.setOnDragEntered(event -> setCollectionDropState(collectionItem, true));
        collectionItem.setOnDragExited(event -> setCollectionDropState(collectionItem, false));
        collectionItem.setOnDragDropped(event -> {
            boolean success = false;
            if (canAcceptMove(event.getDragboard(), targetCollectionId)) {
                success = moveDraggedOeuvre(event.getDragboard().getString(), targetCollectionId);
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private boolean canAcceptMove(Dragboard dragboard, Integer targetCollectionId) {
        return targetCollectionId != null && dragboard != null && dragboard.hasString();
    }

    private void setCollectionDropState(VBox collectionItem, boolean active) {
        collectionItem.getStyleClass().remove("collection-item-box-drop-over");
        if (active && !collectionItem.getStyleClass().contains("collection-item-box-drop-over")) {
            collectionItem.getStyleClass().add("collection-item-box-drop-over");
        }
    }

    private boolean moveDraggedOeuvre(String payload, Integer targetCollectionId) {
        if (payload == null || targetCollectionId == null) {
            return false;
        }

        String[] parts = payload.split(":");
        if (parts.length != 2) {
            return false;
        }

        try {
            int oeuvreId = Integer.parseInt(parts[0]);
            int sourceCollectionId = Integer.parseInt(parts[1]);
            if (sourceCollectionId == targetCollectionId) {
                return false;
            }

            Oeuvre oeuvre = oeuvreService.getOeuvreById(oeuvreId);
            if (oeuvre == null || oeuvre.getId() == null) {
                return false;
            }

            oeuvre.setCollectionId(targetCollectionId);
            oeuvreService.update(oeuvre);
            loadCollections();
            return true;
        } catch (Exception e) {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.initOwner(addCollectionButton.getScene().getWindow());
            errorAlert.setTitle("Erreur");
            errorAlert.setHeaderText("Déplacement impossible");
            errorAlert.setContentText(e.getMessage() == null ? "Une erreur est survenue." : e.getMessage());
            errorAlert.showAndWait();
            return false;
        }
    }

    private void updateExpandButtonIcon(Button button, boolean expanded) {
        button.setGraphic(createIcon(expanded ? CHEVRON_DOWN : CHEVRON_UP, 0.72, "#64748b"));
    }

    private ImageView createThumbnailImageView(String imageSource) {
        if (imageSource == null || imageSource.isBlank()) {
            return null;
        }

        try {
            Image image;
            if (imageSource.startsWith("http://") || imageSource.startsWith("https://") || imageSource.startsWith("file:")) {
                image = new Image(imageSource, true);
            } else {
                image = new Image(new File(imageSource).toURI().toString(), true);
            }
            if (image.isError()) {
                return null;
            }

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(THUMBNAIL_SIZE);
            imageView.setFitHeight(THUMBNAIL_SIZE);
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);
            return imageView;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void renderCollections(List<CollectionOeuvre> collections) {
        collectionsContainer.getChildren().clear();

        if (collections.isEmpty()) {
            emptyStateLabel.setText("Aucune collection trouvee pour cet artiste.");
            emptyStateLabel.setVisible(true);
            return;
        }

        emptyStateLabel.setVisible(false);

        for (CollectionOeuvre collection : collections) {

            VBox collectionItem = new VBox(10);
            collectionItem.getStyleClass().add("collection-item-box");

            HBox row = new HBox(10);
            row.getStyleClass().add("collection-row");

            VBox textBox = new VBox(5);
            Label titleLabel = new Label(collection.getTitre() == null ? "(Sans titre)" : collection.getTitre());
            titleLabel.getStyleClass().add("collection-title");

            String description = collection.getDescription();
            Label descriptionLabel = new Label(description == null || description.isBlank() ? "Aucune description." : description);
            descriptionLabel.getStyleClass().add("collection-description");
            descriptionLabel.setWrapText(true);

            textBox.getChildren().addAll(titleLabel, descriptionLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox actions = new HBox(6);
            actions.getStyleClass().add("collection-actions");

            Button menuButton = new Button("...");
            menuButton.getStyleClass().add("collection-menu-trigger");
            menuButton.setFocusTraversable(false);

            Button expandButton = new Button();
            expandButton.getStyleClass().add("collection-expand-trigger");
            expandButton.setFocusTraversable(false);

            Integer collectionId = collection.getId();
            boolean expanded = collectionId != null && expandedByCollectionId.getOrDefault(collectionId, false);
            updateExpandButtonIcon(expandButton, expanded);

            ContextMenu actionsMenu = buildCollectionActionsMenu(collection);
            menuButton.setOnAction(event -> {
                if (actionsMenu.isShowing()) {
                    actionsMenu.hide();
                } else {
                    actionsMenu.show(menuButton, Side.BOTTOM, 0, 4);
                }
            });

            Separator contentDivider = new Separator();
            contentDivider.getStyleClass().add("collection-content-divider");

            VBox previewBox = new VBox();
            previewBox.getStyleClass().add("collection-preview-box");
            previewBox.getProperties().put("collectionContentDivider", contentDivider);
            refreshCollectionPreview(collectionId, previewBox, contentDivider, expanded);

            expandButton.setOnAction(event -> toggleCollectionPreview(collection, previewBox, expandButton));
            configureCollectionDropTarget(collectionItem, collection);

            actions.getChildren().addAll(expandButton, menuButton);
            row.getChildren().addAll(textBox, spacer, actions);
            collectionItem.getChildren().addAll(row, contentDivider, previewBox);
            collectionsContainer.getChildren().add(collectionItem);
        }
    }
}
