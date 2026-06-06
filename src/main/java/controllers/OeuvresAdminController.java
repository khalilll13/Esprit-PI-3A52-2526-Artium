package controllers;

import services.CommentaireService;
import services.LikeService;
import services.OeuvreCollectionService;
import services.OeuvreService;
import entities.CollectionOeuvre;
import entities.Commentaire;
import entities.Oeuvre;
import entities.User;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.geometry.Insets;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class OeuvresAdminController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRANCE);
    private static final int DESCRIPTION_PREVIEW_MAX = 96;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> sortCombo;

    @FXML
    private ComboBox<String> filterCombo;

    @FXML
    private FlowPane oeuvresCardsWrap;

    @FXML
    private Label emptyStateLabel;

    private final OeuvreService oeuvreService = new OeuvreService();
    private final OeuvreCollectionService collectionService = new OeuvreCollectionService();
    private final CommentaireService commentaireService = new CommentaireService();
    private final LikeService likeService = new LikeService();
    private final Map<Integer, CollectionOeuvre> collectionById = new HashMap<>();
    private final Map<Integer, User> userById = new HashMap<>();
    private final Map<Integer, Integer> commentCountByOeuvreId = new HashMap<>();
    private final Map<Integer, Integer> likeCountByOeuvreId = new HashMap<>();
    private final Map<Integer, Integer> favoriCountByOeuvreId = new HashMap<>();
    private List<Oeuvre> allOeuvres = new ArrayList<>();

    @FXML
    public void initialize() {
        sortCombo.getItems().addAll(
                "Commentaires decroissant", "Commentaires croissant",
                "Likes decroissant", "Likes croissant",
                "Favoris decroissant", "Favoris croissant"
        );
        sortCombo.setValue("Commentaires decroissant");
        filterCombo.getItems().addAll("Tous types", "Peinture", "Sculpture", "Photographie");

        // Listener de recherche : filtre les oeuvres en temps réel
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        // Listener de filtre : applique le filtre par type
        filterCombo.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        // Listener de tri : applique le tri commentaire asc/desc.
        sortCombo.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        loadOeuvres();
    }

    private void applyFilters() {
        String searchQuery = searchField.getText().toLowerCase().trim();
        String selectedType = filterCombo.getValue();

        List<Oeuvre> filtered = allOeuvres.stream()
                .filter(oeuvre -> matchesSearch(oeuvre, searchQuery))
                .filter(oeuvre -> matchesType(oeuvre, selectedType))
                .collect(Collectors.toCollection(ArrayList::new));

        applySort(filtered, sortCombo.getValue());

        displayOeuvres(filtered);
    }

    private void applySort(List<Oeuvre> oeuvres, String sortValue) {
        if (oeuvres == null || oeuvres.isEmpty() || sortValue == null) {
            return;
        }

        Comparator<Oeuvre> comparator;

        if (sortValue.startsWith("Commentaires")) {
            comparator = Comparator.comparingInt(this::getCommentCount);
        } else if (sortValue.startsWith("Likes")) {
            comparator = Comparator.comparingInt(this::getLikeCount);
        } else if (sortValue.startsWith("Favoris")) {
            comparator = Comparator.comparingInt(this::getFavoriCount);
        } else {
            return;
        }

        if (sortValue.endsWith("croissant") && !sortValue.endsWith("decroissant")) {
            oeuvres.sort(comparator);
        } else {
            oeuvres.sort(comparator.reversed());
        }
    }

    private boolean matchesType(Oeuvre oeuvre, String selectedType) {
        // Si "Tous types" est sélectionné, accepter toutes les oeuvres
        if (selectedType == null || "Tous types".equals(selectedType)) {
            return true;
        }

        String oeuvreType = safe(oeuvre.getType(), "");
        return oeuvreType.equalsIgnoreCase(selectedType);
    }

    private boolean matchesSearch(Oeuvre oeuvre, String query) {
        // Si la recherche est vide, accepter l'oeuvre
        if (query.isEmpty()) {
            return true;
        }

        // Rechercher dans le titre de l'oeuvre
        if (safe(oeuvre.getTitre(), "").toLowerCase().contains(query)) {
            return true;
        }

        // Rechercher dans le titre de la collection
        String collectionTitle = loadCollectionTitle(oeuvre.getCollectionId()).toLowerCase();
        if (collectionTitle.contains(query)) {
            return true;
        }

        // Rechercher dans le nom de l'artiste
        String artistName = loadArtistName(oeuvre.getCollectionId()).toLowerCase();
        if (artistName.contains(query)) {
            return true;
        }

        return false;
    }

    private void loadOeuvres() {
        try {
            allOeuvres = oeuvreService.getAll();
            commentCountByOeuvreId.clear();
            likeCountByOeuvreId.clear();
            favoriCountByOeuvreId.clear();

            if (allOeuvres == null) {
                allOeuvres = new ArrayList<>();
            }

            applyFilters();
        } catch (Exception e) {
            oeuvresCardsWrap.getChildren().clear();
            emptyStateLabel.setText("Erreur chargement oeuvres: " + e.getMessage());
            emptyStateLabel.setVisible(true);
        }
    }

    private void displayOeuvres(List<Oeuvre> oeuvres) {
        oeuvresCardsWrap.getChildren().clear();

        if (oeuvres == null || oeuvres.isEmpty()) {
            emptyStateLabel.setVisible(true);
            if (searchField.getText().isEmpty()) {
                emptyStateLabel.setText("Aucune oeuvre.");
            } else {
                emptyStateLabel.setText("Aucune oeuvre trouvée.");
            }
            return;
        }

        emptyStateLabel.setVisible(false);
        for (Oeuvre oeuvre : oeuvres) {
            oeuvresCardsWrap.getChildren().add(buildCard(oeuvre));
        }
    }

    private VBox buildCard(Oeuvre oeuvre) {
        VBox card = new VBox(8);
        card.setPrefWidth(300);
        card.getStyleClass().add("oeuvre-admin-card");

        Label title = new Label(safe(oeuvre.getTitre(), "Sans titre"));
        title.getStyleClass().add("oeuvre-admin-title");

        Button menuButton = new Button("...");
        menuButton.getStyleClass().add("oeuvre-card-menu-trigger");
        menuButton.setFocusTraversable(false);

        ContextMenu actionsMenu = buildCardActionsMenu(oeuvre);
        menuButton.setOnAction(event -> {
            if (actionsMenu.isShowing()) {
                actionsMenu.hide();
            } else {
                actionsMenu.show(menuButton, Side.BOTTOM, 0, 4);
            }
        });

        HBox header = new HBox(8);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, menuButton);

        Label artist = new Label("Artiste: " + loadArtistName(oeuvre.getCollectionId()));
        artist.getStyleClass().add("oeuvre-admin-meta");

        Label date = new Label("Date: " + formatDate(oeuvre.getDateCreation()));
        date.getStyleClass().add("oeuvre-admin-meta");

        Label desc = new Label(buildDescriptionPreview(oeuvre.getDescription()));
        desc.getStyleClass().add("oeuvre-admin-desc");
        desc.setWrapText(true);

        // Obtenir la liste complète des commentaires (réutilisable dans le détail)
        int commentCount = getCommentCount(oeuvre);
        int likeCount = getLikeCount(oeuvre);
        int favoriCount = getFavoriCount(oeuvre);

        HBox statsRow = new HBox(8,
                buildStatChip("M12.1 18.55 10.55 17.14C5.4 12.47 2 9.39 2 5.6 2 2.52 4.42 0 7.5 0c1.74 0 3.41.81 4.5 2.09C13.09.81 14.76 0 16.5 0 19.58 0 22 2.52 22 5.6c0 3.79-3.4 6.87-8.55 11.55z", likeCount),
                buildStatChip("M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z", commentCount),
                buildStatChip("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z", favoriCount)
        );
        statsRow.getStyleClass().add("oeuvre-admin-stats-row");

        Label typeChip = new Label(toHashtag(safe(oeuvre.getType(), "Oeuvre")));
        typeChip.getStyleClass().addAll("oeuvre-chip", "oeuvre-chip-type");

        Label collectionChip = new Label(toHashtag(loadCollectionTitle(oeuvre.getCollectionId())));
        collectionChip.getStyleClass().addAll("oeuvre-chip", "oeuvre-chip-collection");

        HBox tagsRow = new HBox(8);
        Region tagsSpacer = new Region();
        HBox.setHgrow(tagsSpacer, Priority.ALWAYS);
        tagsRow.getChildren().addAll(tagsSpacer, typeChip, collectionChip);

        card.getChildren().addAll(header, artist, date, desc, statsRow, tagsRow);
        return card;
    }

    private ContextMenu buildCardActionsMenu(Oeuvre oeuvre) {
        MenuItem detailsItem = new MenuItem("Voir details");
        detailsItem.getStyleClass().add("oeuvre-menu-details");
        detailsItem.setGraphic(createIcon("M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zm0 12.5a5 5 0 1 1 0-10 5 5 0 0 1 0 10z", "#4f6178"));
        detailsItem.setOnAction(event -> handleViewDetails(oeuvre));

        MenuItem editItem = new MenuItem("Modifier");
        editItem.getStyleClass().add("oeuvre-menu-edit");
        editItem.setGraphic(createIcon("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z", "#365074"));
        editItem.setOnAction(event -> handleEditOeuvre(oeuvre));

        MenuItem deleteItem = new MenuItem("Supprimer");
        deleteItem.getStyleClass().add("oeuvre-menu-delete");
        deleteItem.setGraphic(createIcon("M6 7h12v2H6V7zm2 3h8v10H8V10zm3-6h2l1 1h4v2H6V5h4l1-1z", "#d12f3f"));
        deleteItem.setOnAction(event -> handleDeleteOeuvre(oeuvre));

        ContextMenu menu = new ContextMenu(detailsItem, editItem, deleteItem);
        menu.getStyleClass().add("oeuvre-card-menu");
        return menu;
    }

    private void handleViewDetails(Oeuvre oeuvre) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/components/OeuvreDetailsPopup.fxml"));
            Parent root = loader.load();

            OeuvreDetailsPopupController popupController = loader.getController();
            popupController.setData(
                    oeuvre,
                    loadArtistName(oeuvre.getCollectionId()),
                    loadCollectionTitle(oeuvre.getCollectionId())
            );

            Stage popupStage = new Stage();
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.initOwner(oeuvresCardsWrap.getScene().getWindow());
            popupStage.setTitle("Détails de l'oeuvre");

            Scene scene = new Scene(root, 720, 540);
            scene.getStylesheets().addAll(oeuvresCardsWrap.getScene().getStylesheets());

            popupStage.setScene(scene);
            popupStage.showAndWait();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur");
            alert.setHeaderText("Impossible d'ouvrir les détails");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private void handleEditOeuvre(Oeuvre oeuvre) {
        showOeuvrePopup(oeuvre);
    }

    private void showOeuvrePopup(Oeuvre existingOeuvre) {
        boolean editMode = existingOeuvre != null;
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.initOwner(oeuvresCardsWrap.getScene().getWindow());
        popupStage.setTitle(editMode ? "Modifier une oeuvre" : "Ajouter une oeuvre");

        Label headerLabel = new Label(editMode ? "Modifier une oeuvre" : "Ajouter une oeuvre");
        headerLabel.getStyleClass().add("popup-title");

        Label titreLabel = new Label("Titre de l'oeuvre");
        titreLabel.getStyleClass().add("popup-field-label");
        TextField titreField = new TextField();
        titreField.getStyleClass().add("popup-input");
        if (editMode) {
            titreField.setText(existingOeuvre.getTitre() == null ? "" : existingOeuvre.getTitre());
        }
        Label titreError = createPopupErrorLabel();

        Label descriptionLabel = new Label("Description");
        descriptionLabel.getStyleClass().add("popup-field-label");
        TextArea descriptionArea = new TextArea();
        descriptionArea.getStyleClass().add("popup-input");
        descriptionArea.setPrefRowCount(4);
        if (editMode) {
            descriptionArea.setText(existingOeuvre.getDescription() == null ? "" : existingOeuvre.getDescription());
        }
        Label descriptionError = createPopupErrorLabel();
        Label descriptionCounter = new Label("0/500");
        descriptionCounter.getStyleClass().add("popup-counter");
        HBox descriptionMetaRow = new HBox(8);
        descriptionMetaRow.setAlignment(Pos.CENTER_LEFT);
        Region descriptionSpacer = new Region();
        HBox.setHgrow(descriptionSpacer, Priority.ALWAYS);
        descriptionMetaRow.getChildren().addAll(descriptionError, descriptionSpacer, descriptionCounter);

        Label collectionLabel = new Label("Collection");
        collectionLabel.getStyleClass().add("popup-field-label");
        ComboBox<CollectionOeuvre> collectionCombo = new ComboBox<>();
        collectionCombo.getStyleClass().add("collections-filter-combo");
        collectionCombo.getStyleClass().add("popup-input");
        collectionCombo.setPrefWidth(460);
        collectionCombo.setPromptText("Choisir une collection");
        collectionCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(CollectionOeuvre value) {
                return value == null ? "" : safe(value.getTitre(), "(Sans titre)");
            }

            @Override
            public CollectionOeuvre fromString(String string) {
                return null;
            }
        });
        Label collectionError = createPopupErrorLabel();

        Label imageLabel = new Label("Image");
        imageLabel.getStyleClass().add("popup-field-label");
        Button chooseImageButton = new Button("Choisir un fichier");
        chooseImageButton.getStyleClass().add("popup-close-button");
        chooseImageButton.setGraphic(createIcon("M20 6h-6.18L12 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z", 0.58));
        chooseImageButton.setGraphicTextGap(6);

        Label selectedFileLabel = new Label("Aucun fichier n'a ete selectionne");
        selectedFileLabel.getStyleClass().add("page-subtitle-small");
        Label imageError = createPopupErrorLabel();

        final String[] imagePathHolder = new String[1];
        imagePathHolder[0] = editMode ? existingOeuvre.getImage() : null;
        if (editMode && imagePathHolder[0] != null && !imagePathHolder[0].isBlank()) {
            selectedFileLabel.setText("Image actuelle conservée");
        }

        titreField.textProperty().addListener((observable, oldValue, newValue) -> validateTitreField(titreField, titreError));
        titreField.focusedProperty().addListener((observable, oldValue, isFocused) -> {
            if (!isFocused) {
                validateTitreField(titreField, titreError);
            }
        });

        descriptionArea.textProperty().addListener((observable, oldValue, newValue) -> {
            String current = newValue == null ? "" : newValue;
            if (current.length() > 500) {
                descriptionArea.setText(current.substring(0, 500));
                return;
            }
            validateDescriptionField(descriptionArea, descriptionError, descriptionCounter);
        });

        if (editMode) {
            validateDescriptionField(descriptionArea, descriptionError, descriptionCounter);
            validateTitreField(titreField, titreError);
        }

        collectionCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                clearFieldError(collectionError, collectionCombo);
            }
        });

        chooseImageButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Selectionner une image");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
            );
            File file = chooser.showOpenDialog(popupStage);
            if (file == null) {
                return;
            }
            imagePathHolder[0] = file.getAbsolutePath();
            selectedFileLabel.setText(file.getName());
            clearPopupError(imageError);
        });

        try {
            List<CollectionOeuvre> collections = new ArrayList<>();

            if (editMode && existingOeuvre.getCollectionId() != null) {
                CollectionOeuvre currentCollection = getCollection(existingOeuvre.getCollectionId());
                if (currentCollection != null && currentCollection.getArtisteId() != null) {
                    collections = collectionService.getCollectionsByArtisteId(currentCollection.getArtisteId());
                }

                // Fallback: garder au moins la collection actuelle si elle n'apparait pas dans le fetch.
                if ((collections == null || collections.isEmpty()) && currentCollection != null) {
                    collections = new ArrayList<>();
                    collections.add(currentCollection);
                }
            } else {
                // Cas creation: comportement permissif pour garder le popup reutilisable.
                collections = collectionService.getAll();
            }

            if (collections == null) {
                collections = new ArrayList<>();
            }

            collectionCombo.setItems(FXCollections.observableArrayList(collections));
            if (editMode && existingOeuvre.getCollectionId() != null) {
                for (CollectionOeuvre collection : collections) {
                    if (Objects.equals(collection.getId(), existingOeuvre.getCollectionId())) {
                        collectionCombo.setValue(collection);
                        break;
                    }
                }
            }

            if (collections.isEmpty()) {
                showPopupError(collectionError, "Aucune collection disponible pour l'artiste proprietaire de cette oeuvre.");
            }
        } catch (Exception e) {
            showPopupError(collectionError, "Erreur chargement collections: " + e.getMessage());
        }

        Button cancelButton = new Button("Fermer");
        cancelButton.getStyleClass().add("popup-close-button");
        cancelButton.setGraphic(createIcon("M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z", 0.52));
        cancelButton.setGraphicTextGap(6);
        cancelButton.setOnAction(event -> popupStage.close());

        Button publishButton = new Button(editMode ? "Enregistrer" : "Publier");
        publishButton.getStyleClass().add("popup-confirm-button");
        publishButton.setGraphic(createIcon("M9 16.17 4.83 12 3.41 13.41 9 19l12-12-1.41-1.41z", 0.58));
        publishButton.setGraphicTextGap(6);
        publishButton.setOnAction(event -> {
            clearPopupError(titreError);
            clearPopupError(descriptionError);
            clearPopupError(collectionError);
            clearPopupError(imageError);

            String titre = safe(titreField.getText(), "");
            String description = safe(descriptionArea.getText(), "");
            CollectionOeuvre selectedCollection = collectionCombo.getValue();

            boolean hasError = false;
            if (!validateTitreField(titreField, titreError)) {
                hasError = true;
            }

            if (!validateDescriptionField(descriptionArea, descriptionError, descriptionCounter)) {
                hasError = true;
            }
            if (selectedCollection == null || selectedCollection.getId() == null) {
                showFieldError(collectionError, collectionCombo, "Selectionnez une collection.");
                hasError = true;
            } else {
                clearFieldError(collectionError, collectionCombo);
            }
            if (imagePathHolder[0] == null || imagePathHolder[0].isBlank()) {
                showPopupError(imageError, "L'image est obligatoire.");
                hasError = true;
            }
            if (hasError) {
                return;
            }

            try {
                Oeuvre oeuvre = editMode ? existingOeuvre : new Oeuvre();
                if (oeuvre.getId() == null && editMode) {
                    oeuvre.setId(existingOeuvre.getId());
                }
                oeuvre.setTitre(titre);
                oeuvre.setDescription(description);
                oeuvre.setCollectionId(selectedCollection.getId());
                oeuvre.setImage(imagePathHolder[0]);
                oeuvre.setDateCreation(editMode && existingOeuvre.getDateCreation() != null ? existingOeuvre.getDateCreation() : LocalDate.now());
                if (editMode) {
                    oeuvreService.update(oeuvre);
                } else {
                    oeuvreService.add(oeuvre);
                }

                popupStage.close();
                loadOeuvres();
            } catch (Exception e) {
                showFieldError(titreError, titreField, e.getMessage() == null ? (editMode ? "Erreur modification oeuvre." : "Erreur ajout oeuvre.") : e.getMessage());
            }
        });

        HBox imageRow = new HBox(10, chooseImageButton, selectedFileLabel);
        imageRow.setAlignment(Pos.CENTER_LEFT);

        HBox footer = new HBox(10, cancelButton, publishButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(
                8,
                headerLabel,
                titreLabel,
                titreField,
                titreError,
                descriptionLabel,
                descriptionArea,
                descriptionMetaRow,
                collectionLabel,
                collectionCombo,
                collectionError,
                imageLabel,
                imageRow,
                imageError,
                footer
        );
        root.setPadding(new Insets(16));
        root.getStyleClass().add("collection-popup");

        Scene scene = new Scene(root, 720, 500);
        scene.getStylesheets().addAll(oeuvresCardsWrap.getScene().getStylesheets());

        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private Label createPopupErrorLabel() {
        Label label = new Label();
        label.getStyleClass().add("popup-error");
        label.setManaged(false);
        label.setVisible(false);
        return label;
    }

    private void showPopupError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void showFieldError(Label errorLabel, javafx.scene.control.Control field, String message) {
        showPopupError(errorLabel, message);
        field.getStyleClass().remove("popup-input-valid");
        if (!field.getStyleClass().contains("popup-input-invalid")) {
            field.getStyleClass().add("popup-input-invalid");
        }
    }

    private void clearFieldError(Label errorLabel, javafx.scene.control.Control field) {
        clearPopupError(errorLabel);
        field.getStyleClass().remove("popup-input-invalid");
        if (!field.getStyleClass().contains("popup-input-valid")) {
            field.getStyleClass().add("popup-input-valid");
        }
    }

    private void clearPopupError(Label errorLabel) {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private boolean validateDescriptionField(TextArea descriptionArea, Label descriptionError, Label descriptionCounter) {
        String description = safe(descriptionArea.getText(), "");
        int length = description.length();
        int minLength = 10;
        int maxLength = 500;
        boolean valid = !description.isEmpty() && length >= minLength && length <= maxLength;
        updateDescriptionState(descriptionArea, descriptionCounter, valid);
        if (!valid) {
            if (description.isEmpty()) {
                showPopupError(descriptionError, "La description est obligatoire.");
            } else if (length < minLength) {
                showPopupError(descriptionError, "La description doit contenir au moins " + minLength + " caracteres.");
            } else {
                showPopupError(descriptionError, "La description ne doit pas depasser " + maxLength + " caracteres.");
            }
        } else {
            clearPopupError(descriptionError);
        }
        return valid;
    }

    private void updateDescriptionState(TextArea descriptionArea, Label descriptionCounter, boolean forceValid) {
        String description = safe(descriptionArea.getText(), "");
        int length = description.length();
        descriptionCounter.setText(length + "/500");
        descriptionCounter.getStyleClass().removeAll("popup-counter-valid", "popup-counter-limit");

        descriptionArea.getStyleClass().removeAll("popup-input-valid", "popup-input-invalid");
        if (forceValid || (!description.isEmpty() && length >= 10 && length <= 500)) {
            descriptionArea.getStyleClass().add("popup-input-valid");
            descriptionCounter.getStyleClass().add("popup-counter-valid");
        } else {
            descriptionArea.getStyleClass().add("popup-input-invalid");
            descriptionCounter.getStyleClass().add("popup-counter-limit");
        }
    }

    private boolean validateTitreField(TextField titreField, Label titreError) {
        String titre = safe(titreField.getText(), "");
        if (titre.isEmpty()) {
            showFieldError(titreError, titreField, "Le titre est obligatoire.");
            return false;
        }
        clearFieldError(titreError, titreField);
        return true;
    }

    private void handleDeleteOeuvre(Oeuvre oeuvre) {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirmer la suppression");
        confirmDialog.setHeaderText("Êtes-vous sûr ?");
        confirmDialog.setContentText("Supprimer l'oeuvre \"" + safe(oeuvre.getTitre(), "Sans titre") + "\" ?\n\nCette action est irréversible et supprimera aussi tous les commentaires associés.");

        var result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
            try {
                oeuvreService.delete(oeuvre);
                loadOeuvres();

                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Suppression réussie");
                successAlert.setHeaderText("Succès");
                successAlert.setContentText("L'oeuvre a été supprimée avec succès.");
                successAlert.showAndWait();
            } catch (Exception e) {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Erreur");
                errorAlert.setHeaderText("Erreur lors de la suppression");
                errorAlert.setContentText("Une erreur est survenue : " + e.getMessage());
                errorAlert.showAndWait();
            }
        }
    }

    private HBox buildStatChip(String iconPath, int value) {
        HBox chip = new HBox(5);
        chip.getStyleClass().add("oeuvre-admin-stat-chip");
        chip.setAlignment(Pos.CENTER_LEFT);

        SVGPath icon = createIcon(iconPath, "#6b7280");
        Label count = new Label(String.valueOf(value));
        count.getStyleClass().add("oeuvre-admin-stat-count");

        chip.getChildren().addAll(icon, count);
        return chip;
    }

    private int getCommentCount(Oeuvre oeuvre) {
        if (oeuvre == null || oeuvre.getId() == null) {
            return 0;
        }

        Integer oeuvreId = oeuvre.getId();
        if (commentCountByOeuvreId.containsKey(oeuvreId)) {
            return commentCountByOeuvreId.get(oeuvreId);
        }

        try {
            List<Commentaire> comments = commentaireService.getCommentsByOeuvreId(oeuvreId);
            if (comments == null) {
                comments = new ArrayList<>();
            }
            oeuvre.setComments(comments);
            int count = comments.size();
            commentCountByOeuvreId.put(oeuvreId, count);
            return count;
        } catch (Exception ignored) {
            commentCountByOeuvreId.put(oeuvreId, 0);
            return 0;
        }
    }

    private int getLikeCount(Oeuvre oeuvre) {
        if (oeuvre == null || oeuvre.getId() == null) {
            return 0;
        }

        Integer oeuvreId = oeuvre.getId();
        if (likeCountByOeuvreId.containsKey(oeuvreId)) {
            return likeCountByOeuvreId.get(oeuvreId);
        }

        int count = likeService.countLikesByOeuvre(oeuvreId);
        likeCountByOeuvreId.put(oeuvreId, count);
        return count;
    }

    private int getFavoriCount(Oeuvre oeuvre) {
        if (oeuvre == null || oeuvre.getId() == null) {
            return 0;
        }

        Integer oeuvreId = oeuvre.getId();
        if (favoriCountByOeuvreId.containsKey(oeuvreId)) {
            return favoriCountByOeuvreId.get(oeuvreId);
        }

        int count = likeService.countFavorisByOeuvre(oeuvreId);
        favoriCountByOeuvreId.put(oeuvreId, count);
        return count;
    }

    private SVGPath createIcon(String path, String color) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setScaleX(0.48);
        icon.setScaleY(0.48);
        icon.setStyle("-fx-fill: " + color + ";");
        return icon;
    }

    private SVGPath createIcon(String path, double scale) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setScaleX(scale);
        icon.setScaleY(scale);
        icon.setStyle("-fx-fill: #ffffff;");
        return icon;
    }

    private String buildDescriptionPreview(String description) {
        String value = safe(description, "Aucune description.");
        if (value.length() <= DESCRIPTION_PREVIEW_MAX) {
            return value;
        }
        return value.substring(0, DESCRIPTION_PREVIEW_MAX).trim() + "...";
    }

    private String toHashtag(String value) {
        String clean = safe(value, "-");
        return "#" + clean.replaceAll("\\s+", "_");
    }

    private String loadCollectionTitle(Integer collectionId) {
        CollectionOeuvre collection = getCollection(collectionId);
        if (collection == null) {
            return "-";
        }
        return safe(collection.getTitre(), "-");
    }

    private String loadArtistName(Integer collectionId) {
        CollectionOeuvre collection = getCollection(collectionId);
        if (collection == null || collection.getArtisteId() == null) {
            return "-";
        }
        try {
            Integer artisteId = collection.getArtisteId();
            User artist = userById.get(artisteId);
            if (artist == null) {
                artist = oeuvreService.getUserById(artisteId);
                userById.put(artisteId, artist);
            }
            if (artist == null) {
                return "-";
            }
            String fullName = (safe(artist.getPrenom(), "") + " " + safe(artist.getNom(), "")).trim();
            return fullName.isEmpty() ? "-" : fullName;
        } catch (Exception ignored) {
            return "-";
        }
    }

    private CollectionOeuvre getCollection(Integer collectionId) {
        if (collectionId == null) {
            return null;
        }
        if (collectionById.containsKey(collectionId)) {
            return collectionById.get(collectionId);
        }
        try {
            CollectionOeuvre collection = collectionService.getCollectionById(collectionId);
            collectionById.put(collectionId, collection);
            return collection;
        } catch (Exception ignored) {
            collectionById.put(collectionId, null);
            return null;
        }
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return "-";
        }
        return DATE_FORMATTER.format(date);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

