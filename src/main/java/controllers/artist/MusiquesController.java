package controllers.artist;

import services.MusiqueService;
import services.PlaylistService;
import entities.Musique;
import entities.Playlist;
import entities.User;
import utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.geometry.Side;
import javafx.scene.media.MediaException;
import javafx.stage.FileChooser;
import services.GlobalMediaPlayerService;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import utils.ImageUrlUtils;
import utils.MyDatabase;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.SQLDataException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MusiquesController {
    private static final String XAMPP_IMAGE_DIR = "C:\\xampp\\htdocs\\img";
    private static final double TRACK_CARD_WIDTH = 170;
    private static final double PLAYLIST_CARD_WIDTH = 180;
    private static final double TRACK_COVER_SIZE = 150;
    private static final double PLAYLIST_COVER_WIDTH = 160;
    private static final double PLAYLIST_COVER_HEIGHT = 110;

    @FXML
    private TextField titreField;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> sortMusicComboBox;

    @FXML
    private TextField playlistSearchField;

    @FXML
    private ComboBox<String> sortPlaylistComboBox;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private ComboBox<String> genreComboBox;

    @FXML
    private ComboBox<CollectionChoice> collectionComboBox;

    @FXML
    private TextField imagePathField;

    @FXML
    private TextField audioPathField;

    @FXML
    private Label feedbackLabel;

    @FXML
    private Button toggleFormButton;

    @FXML
    private VBox formSection;

    @FXML
    private TilePane musiqueGrid;

    @FXML
    private TilePane playlistGrid;

    @FXML
    private Label emptyListLabel;

    @FXML
    private Label playlistEmptyListLabel;

    @FXML
    private Button musiqueSectionButton;

    @FXML
    private Button playlistsSectionButton;

    @FXML
    private VBox musiqueSection;

    @FXML
    private VBox playlistsSection;

    @FXML
    private Label nowPlayingTitleLabel;

    @FXML
    private Label nowPlayingMetaLabel;

    @FXML
    private Label playerStatusLabel;

    @FXML
    private Button playPauseButton;

    @FXML
    private Label formTitleLabel;

    @FXML
    private Button submitMusiqueButton;

    private final MusiqueService musiqueService = new MusiqueService();
    private final PlaylistService playlistService = new PlaylistService();
    private final GlobalMediaPlayerService globalMediaPlayer = GlobalMediaPlayerService.getInstance();
    private final ObservableList<Musique> allTracks = FXCollections.observableArrayList();
    private final ObservableList<Musique> visibleTracks = FXCollections.observableArrayList();
    private final ObservableList<Playlist> allPlaylists = FXCollections.observableArrayList();
    private final ObservableList<Playlist> visiblePlaylists = FXCollections.observableArrayList();
    private final Map<Integer, String> trackArtistNames = new HashMap<>();
    private final ObservableList<String> genres = FXCollections.observableArrayList(
            "Rock",
            "Pop",
            "Jazz",
            "Classique"
    );
    private int currentTrackIndex = -1;
    private static final Set<String> PLAYABLE_EXTENSIONS = new HashSet<>(Arrays.asList("mp3", "m4a", "wav"));
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList("png", "jpg", "jpeg"));
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;
    private Integer editingMusiqueId;
    private LocalDate editingDateCreation;
    private String editingImagePath;
    private String editingAudioPath;
    private Dialog<Void> formDialog;

    @FXML
    public void initialize() {
        genreComboBox.setItems(genres);
        loadCollections();
        feedbackLabel.setText("");
        resetEditMode();
        showListView();
        setActiveSection(true);
        
        // Add search functionality
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldValue, newValue) -> filterTracks(newValue));
        }
        
        // Add sort combo box
        if (sortMusicComboBox != null) {
            sortMusicComboBox.setItems(FXCollections.observableArrayList(
                    "Titre (A-Z)", "Titre (Z-A)", "Genre (A-Z)", "Récent"
            ));
            sortMusicComboBox.getSelectionModel().selectFirst();
            sortMusicComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> filterTracks(searchField != null ? searchField.getText() : null));
        }

        if (playlistSearchField != null) {
            playlistSearchField.textProperty().addListener((obs, oldValue, newValue) -> filterPlaylists(newValue));
        }

        if (sortPlaylistComboBox != null) {
            sortPlaylistComboBox.setItems(FXCollections.observableArrayList(
                    "Nom (A-Z)", "Nom (Z-A)", "Récent"
            ));
            sortPlaylistComboBox.getSelectionModel().selectFirst();
            sortPlaylistComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> filterPlaylists(playlistSearchField != null ? playlistSearchField.getText() : null));
        }

        configureGridSizing();
        
        refreshMusiquesList();
        refreshPlaylistsList();

        // Listen to active track changes to dynamically update card style
        globalMediaPlayer.currentTrackProperty().addListener((obs, oldTrack, newTrack) -> {
            javafx.application.Platform.runLater(() -> {
                updateCurrentTrackIndex(newTrack);
                renderTrackGrid();
            });
        });

        // Listen to play/pause state
        globalMediaPlayer.playingProperty().addListener((obs, oldVal, newVal) -> {
            javafx.application.Platform.runLater(this::renderTrackGrid);
        });
    }

    private void updateCurrentTrackIndex(Musique newTrack) {
        if (newTrack == null) {
            currentTrackIndex = -1;
            return;
        }
        for (int i = 0; i < visibleTracks.size(); i++) {
            if (newTrack.getId() != null && newTrack.getId().equals(visibleTracks.get(i).getId())) {
                currentTrackIndex = i;
                return;
            }
        }
        currentTrackIndex = -1;
    }

    private void configureGridSizing() {
        if (musiqueGrid != null) {
            musiqueGrid.setPrefTileWidth(TRACK_CARD_WIDTH);
            musiqueGrid.setTileAlignment(javafx.geometry.Pos.TOP_LEFT);
        }
        if (playlistGrid != null) {
            playlistGrid.setPrefTileWidth(PLAYLIST_CARD_WIDTH);
            playlistGrid.setTileAlignment(javafx.geometry.Pos.TOP_LEFT);
        }
    }

    @FXML
    private void handleShowMusiqueSection() {
        setActiveSection(true);
    }

    @FXML
    private void handleShowPlaylistsSection() {
        setActiveSection(false);
    }

    @FXML
    private void handleSearchPlaylist() {
        filterPlaylists(playlistSearchField != null ? playlistSearchField.getText() : null);
    }

    @FXML
    private void handleClearPlaylistSearch() {
        if (playlistSearchField != null) {
            playlistSearchField.clear();
        }
        filterPlaylists(null);
    }

    @FXML
    private void handleToggleForm() {
        clearForm();
        resetEditMode();
        openFormDialog();
    }

    @FXML
    private void handlePlayPause() {
        if (globalMediaPlayer.getCurrentTrack() == null && !visibleTracks.isEmpty()) {
            playTrackAtIndex(0, true);
            return;
        }
        globalMediaPlayer.togglePlayPause();
    }

    @FXML
    private void handlePreviousTrack() {
        if (visibleTracks.isEmpty()) {
            return;
        }
        globalMediaPlayer.playPrevious();
    }

    @FXML
    private void handleNextTrack() {
        if (visibleTracks.isEmpty()) {
            return;
        }
        globalMediaPlayer.playNext();
    }

    @FXML
    private void handleChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image de couverture");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );

        File file = chooser.showOpenDialog(imagePathField.getScene().getWindow());
        if (file != null) {
            String selectedPath = file.getAbsolutePath();
            String validationError = validateImagePath(selectedPath);
            if (validationError != null) {
                imagePathField.clear();
                setFeedback(validationError, false);
                return;
            }
            imagePathField.setText(selectedPath);
        }
    }

    @FXML
    private void handleChooseAudio() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir un fichier audio");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio lisible (JavaFX)", "*.mp3", "*.m4a", "*.wav")
        );

        File file = chooser.showOpenDialog(audioPathField.getScene().getWindow());
        if (file != null) {
            audioPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleOpenStudio() {
        String currentAudio = audioPathField.getText() != null ? audioPathField.getText().trim() : "";
        if (currentAudio.isEmpty() && editingAudioPath != null && !editingAudioPath.isEmpty()) {
            currentAudio = editingAudioPath;
        }

        if (currentAudio.isEmpty()) {
            setFeedback("Veuillez d'abord choisir un fichier audio avant d'ouvrir le studio.", false);
            return;
        }

        try {
            File audioFile = utils.AudioPathResolver.resolveForStudio(currentAudio);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/artist/Studio.fxml"));
            Parent root = loader.load();

            StudioController controller = loader.getController();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Artium Studio — Édition Audio Pro");
            Scene scene = new Scene(root, 1360, 820);
            stage.setScene(scene);
            stage.setMinWidth(1200);
            stage.setMinHeight(760);

            controller.openWithAudio(audioFile, currentAudio);
            controller.setDialogStage(stage);

            stage.showAndWait();

            String newPath = controller.getFinalAudioPath();
            if (newPath != null && !newPath.isEmpty()) {
                audioPathField.setText(newPath);
            }
        } catch (IOException e) {
            setFeedback("Impossible d'ouvrir le studio : " + e.getMessage(), false);
        } catch (Exception e) {
            setFeedback("Erreur studio : " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleCreateMusique() {
        String titre = titreField.getText() != null ? titreField.getText().trim() : "";
        String description = descriptionArea.getText() != null ? descriptionArea.getText().trim() : "";
        String genre = genreComboBox.getValue() != null ? genreComboBox.getValue().trim() : "";
        CollectionChoice collectionChoice = collectionComboBox.getValue();
        String imagePath = imagePathField.getText() != null ? imagePathField.getText().trim() : "";
        String audioPath = audioPathField.getText() != null ? audioPathField.getText().trim() : "";
        boolean editMode = editingMusiqueId != null;
        String resolvedAudioPath = !audioPath.isEmpty() ? audioPath : (editingAudioPath != null ? editingAudioPath : "");

        if (titre.length() < 3 || titre.length() > 255) {
            setFeedback("Le titre doit contenir entre 3 et 255 caracteres.", false);
            return;
        }

        if (description.length() < 10 || description.length() > 5000) {
            setFeedback("La description doit contenir entre 10 et 5000 caracteres.", false);
            return;
        }

        if (genre.isEmpty() || resolvedAudioPath.isEmpty()) {
            setFeedback("Veuillez renseigner le genre et le fichier audio.", false);
            return;
        }

        if (!isPlayableAudioPath(resolvedAudioPath)) {
            setFeedback("Format audio non supporte. Utilisez: MP3, M4A ou WAV.", false);
            return;
        }

        String imageValidationError = validateImagePath(imagePath);
        if (imageValidationError != null) {
            setFeedback(imageValidationError, false);
            return;
        }

        if (!editMode && imagePath.isEmpty()) {
            setFeedback("Veuillez ajouter une image de couverture (JPEG/PNG, max 5 MB).", false);
            return;
        }

        Musique musique = new Musique();
        musique.setTitre(titre);
        musique.setDescription(description);
        musique.setId(editingMusiqueId);
        musique.setDateCreation(editingDateCreation != null ? editingDateCreation : LocalDate.now());
        musique.setGenre(genre);
        musique.setCollectionId(collectionChoice != null && collectionChoice.getId() > 0 ? collectionChoice.getId() : null);
        musique.setAudio(resolvedAudioPath);

        String imageSource = !imagePath.isEmpty() ? imagePath : editingImagePath;
        if (imageSource == null || imageSource.isBlank()) {
            setFeedback("Image de couverture obligatoire. Veuillez choisir un fichier JPEG/PNG (max 5 MB).", false);
            return;
        }

        musique.setImage(imageSource);

        try {
            if (editMode) {
                musique.setUpdatedAt(LocalDateTime.now());
                musiqueService.update(musique);
                setFeedback("Musique modifiee avec succes.", true);
            } else {
                musiqueService.add(musique);
                setFeedback("Musique ajoutee avec succes.", true);
            }
            clearForm();
            resetEditMode();
            refreshMusiquesList();
            closeFormDialog();
        } catch (SQLDataException e) {
            setFeedback("Erreur lors de l'enregistrement: " + e.getMessage(), false);
        }
    }

    private void clearForm() {
        titreField.clear();
        descriptionArea.clear();
        genreComboBox.getSelectionModel().clearSelection();
        collectionComboBox.getSelectionModel().clearSelection();
        imagePathField.clear();
        audioPathField.clear();
    }

    private void resetEditMode() {
        editingMusiqueId = null;
        editingDateCreation = null;
        editingImagePath = null;
        editingAudioPath = null;
        updateFormTexts();
    }

    private void startEditMusique(Musique musique) {
        if (musique == null || musique.getId() == null) {
            setFeedback("Impossible de modifier cette musique (id manquant).", false);
            return;
        }

        editingMusiqueId = musique.getId();
        editingDateCreation = musique.getDateCreation();
        editingImagePath = musique.getImage();
        editingAudioPath = musique.getAudio();

        titreField.setText(musique.getTitre() != null ? musique.getTitre() : "");
        descriptionArea.setText(musique.getDescription() != null ? musique.getDescription() : "");
        genreComboBox.setValue(musique.getGenre());
        imagePathField.clear();
        audioPathField.setText(musique.getAudio() != null ? musique.getAudio() : "");

        collectionComboBox.getSelectionModel().clearSelection();
        if (musique.getCollectionId() != null) {
            for (CollectionChoice choice : collectionComboBox.getItems()) {
                if (choice.getId() == musique.getCollectionId()) {
                    collectionComboBox.getSelectionModel().select(choice);
                    break;
                }
            }
        }

        updateFormTexts();
        setFeedback("Mode modification: mettez a jour les champs puis enregistrez.", true);
        openFormDialog();
    }

    private void updateFormTexts() {
        boolean editMode = editingMusiqueId != null;
        if (formTitleLabel != null) {
            formTitleLabel.setText(editMode ? "Modifier la musique" : "Ajouter une nouvelle musique");
        }
        if (submitMusiqueButton != null) {
            submitMusiqueButton.setText(editMode ? "Enregistrer" : "Creer");
        }
        if (toggleFormButton != null) {
            toggleFormButton.setText("Ajouter une musique");
        }
    }

    private void openFormDialog() {
        if (formSection == null) {
            return;
        }

        if (formDialog != null && formDialog.isShowing()) {
            return;
        }

        formDialog = new Dialog<>();
        formDialog.setTitle(editingMusiqueId != null ? "Modifier la musique" : "Ajouter une musique");
        formSection.setVisible(true);
        formSection.setManaged(true);
        formDialog.getDialogPane().setContent(formSection);
        formDialog.getDialogPane().getButtonTypes().clear();
        formDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Button hiddenCancelButton = (Button) formDialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (hiddenCancelButton != null) {
            hiddenCancelButton.setManaged(false);
            hiddenCancelButton.setVisible(false);
        }

        formDialog.getDialogPane().getStyleClass().add("custom-dialog");
        formDialog.getDialogPane().getStyleClass().add("music-form-dialog");
        if (musiqueGrid != null && musiqueGrid.getScene() != null) {
            formDialog.getDialogPane().getStylesheets().setAll(musiqueGrid.getScene().getStylesheets());
        }
        formDialog.getDialogPane().setPrefSize(760, 700);
        formDialog.showAndWait();
        formDialog = null;
        showListView();
    }

    private void closeFormDialog() {
        if (formDialog != null) {
            formDialog.close();
            formDialog = null;
        }
        showListView();
    }

    private void setFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle(success ? "-fx-text-fill: #1f7a1f;" : "-fx-text-fill: #b00020;");
    }

    private void loadCollections() {
        List<CollectionChoice> choices = new ArrayList<>();
        
        // Get the currently logged-in user
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            collectionComboBox.setItems(FXCollections.observableArrayList());
            return;
        }
        
        // Query collections for the current artist only
        String[] queries = {
                "SELECT id, titre FROM collections WHERE artiste_id = ?",
                "SELECT id, titre FROM collection_oeuvre WHERE artiste_id = ?",
                "SELECT id, titre FROM collectionoeuvre WHERE artiste_id = ?",
                "SELECT id, titre FROM collection WHERE artiste_id = ?"
        };

        Connection connection = MyDatabase.getInstance().getConnection();
        if (connection == null) {
            collectionComboBox.setItems(FXCollections.observableArrayList());
            return;
        }

        for (String query : queries) {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, currentUser.getId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        choices.add(new CollectionChoice(resultSet.getInt("id"), resultSet.getString("titre")));
                    }
                    if (!choices.isEmpty()) {
                        collectionComboBox.setItems(FXCollections.observableArrayList(choices));
                        return;
                    }
                }
            } catch (SQLException ignored) {
                choices.clear();
            }
        }

        choices.add(new CollectionChoice(0, "Aucune collection disponible"));
        collectionComboBox.setItems(FXCollections.observableArrayList(choices));
        collectionComboBox.getSelectionModel().selectFirst();
    }

    private void refreshMusiquesList() {
        try {
            List<Musique> musiques;
            User loggedInUser = SessionManager.getCurrentUser();
            if (loggedInUser != null) {
                musiques = musiqueService.getByArtistId(loggedInUser.getId());
            } else {
                musiques = musiqueService.getAll();
            }
            allTracks.setAll(musiques);
            loadTrackArtistNames(musiques);
            filterTracks(searchField != null ? searchField.getText() : null);
        } catch (SQLDataException e) {
            allTracks.clear();
            visibleTracks.clear();
            trackArtistNames.clear();
            renderTrackGrid();
            emptyListLabel.setText("Impossible de charger les musiques: " + e.getMessage());
            emptyListLabel.setVisible(true);
            emptyListLabel.setManaged(true);
        }
    }

    private void refreshPlaylistsList() {
        try {
            allPlaylists.setAll(playlistService.getAll());
            filterPlaylists(playlistSearchField != null ? playlistSearchField.getText() : null);
        } catch (SQLDataException e) {
            allPlaylists.clear();
            visiblePlaylists.clear();
            renderPlaylistGrid();
            if (playlistEmptyListLabel != null) {
                playlistEmptyListLabel.setText("Impossible de charger les playlists: " + e.getMessage());
                playlistEmptyListLabel.setVisible(true);
                playlistEmptyListLabel.setManaged(true);
            }
        }
    }

    private void showListView() {
        formSection.setVisible(false);
        formSection.setManaged(false);
        updateFormTexts();
    }

    private void showFormView() {
        formSection.setVisible(true);
        formSection.setManaged(true);
        updateFormTexts();
    }

    private void setActiveSection(boolean showMusique) {
        if (musiqueSection != null) {
            musiqueSection.setVisible(showMusique);
            musiqueSection.setManaged(showMusique);
        }
        if (playlistsSection != null) {
            playlistsSection.setVisible(!showMusique);
            playlistsSection.setManaged(!showMusique);
        }
        if (musiqueSectionButton != null) {
            musiqueSectionButton.setStyle(showMusique
                    ? "-fx-background-color: #1f7a1f; -fx-text-fill: white;"
                    : "-fx-background-color: #4b5563; -fx-text-fill: white;");
        }
        if (playlistsSectionButton != null) {
            playlistsSectionButton.setStyle(!showMusique
                    ? "-fx-background-color: #1f7a1f; -fx-text-fill: white;"
                    : "-fx-background-color: #4b5563; -fx-text-fill: white;");
        }
    }

    private void playTrackAtIndex(int index, boolean autoPlay) {
        if (index < 0 || index >= visibleTracks.size()) {
            return;
        }

        Musique selectedTrack = visibleTracks.get(index);
        currentTrackIndex = index;
        renderTrackGrid();

        String artistName = resolveTrackArtistName(selectedTrack);
        globalMediaPlayer.playTrack(selectedTrack, visibleTracks, index, artistName,true);
    }

    private String resolveTrackArtistName(Musique track) {
        if (track == null || track.getId() == null) {
            return "Artiste inconnu";
        }
        return trackArtistNames.getOrDefault(track.getId(), "Artiste inconnu");
    }

    private void loadTrackArtistNames(List<Musique> tracks) {
        trackArtistNames.clear();
        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        List<Integer> ids = tracks.stream()
                .map(Musique::getId)
                .filter(id -> id != null)
                .toList();
        if (ids.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT o.id AS oeuvre_id, u.prenom, u.nom "
                + "FROM oeuvre o "
                + "LEFT JOIN collections c ON c.id = o.collection_id "
                + "LEFT JOIN `user` u ON u.id = c.artiste_id "
                + "WHERE o.id IN (" + placeholders + ")";

        Connection connection = MyDatabase.getInstance().getConnection();
        if (connection == null) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setInt(i + 1, ids.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int oeuvreId = resultSet.getInt("oeuvre_id");
                    String prenom = resultSet.getString("prenom");
                    String nom = resultSet.getString("nom");
                    String fullName = ((prenom != null ? prenom : "") + " " + (nom != null ? nom : "")).trim();
                    trackArtistNames.put(oeuvreId, fullName.isEmpty() ? "Artiste inconnu" : fullName);
                }
            }
        } catch (SQLException ignored) {
            // Keep fallback label if relation/tables are unavailable in this environment.
        }
    }

    private String describeMediaError(Throwable throwable) {
        if (throwable instanceof MediaException mediaException) {
            if (mediaException.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                return "Format non pris en charge par JavaFX (ex: OPUS)";
            }

            String message = mediaException.getMessage();
            if (message != null && message.toLowerCase().contains("unrecognized file signature")) {
                return "Codec audio non pris en charge (essayez MP3/M4A/WAV)";
            }
        }
        return "Impossible de lire ce fichier audio";
    }

    private void updateNowPlayingLabels(Musique musique) {
        String titre = musique.getTitre() != null ? musique.getTitre() : "Sans titre";
        String genre = musique.getGenre() != null ? musique.getGenre() : "-";
        setNowPlayingText(titre, "Genre: " + genre);
    }

    private void setNowPlayingText(String title, String meta) {
        if (nowPlayingTitleLabel != null) {
            nowPlayingTitleLabel.setText(title);
        }
        if (nowPlayingMetaLabel != null) {
            nowPlayingMetaLabel.setText(meta);
        }
    }

    private void setPlayerStatusText(String status) {
        if (playerStatusLabel != null) {
            playerStatusLabel.setText(status);
        }
    }

    private void setPlayPauseButtonText(String text) {
        if (playPauseButton != null) {
            playPauseButton.setText(text);
        }
    }

    private String toMediaSource(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            return null;
        }

        String trimmed = audioPath.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file:/")) {
            return trimmed;
        }

        File file = new File(trimmed);
        if (!file.exists()) {
            return null;
        }
        return file.toURI().toString();
    }

    private boolean isPlayableAudioPath(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            return false;
        }

        String normalized = audioPath.trim();
        int questionMarkIndex = normalized.indexOf('?');
        if (questionMarkIndex >= 0) {
            normalized = normalized.substring(0, questionMarkIndex);
        }

        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == normalized.length() - 1) {
            return false;
        }

        String extension = normalized.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return PLAYABLE_EXTENSIONS.contains(extension);
    }

    private String validateImagePath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        File imageFile = new File(imagePath.trim());
        if (!imageFile.exists() || !imageFile.isFile()) {
            return "Image introuvable. Veuillez choisir un fichier valide.";
        }
        if (!imageFile.canRead()) {
            return "Image non lisible. Verifiez les permissions du fichier.";
        }

        String fileName = imageFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "Format image non supporte. Utilisez JPEG ou PNG.";
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            return "Format image non supporte. Utilisez JPEG ou PNG.";
        }

        if (imageFile.length() > MAX_IMAGE_SIZE_BYTES) {
            return "Image trop volumineuse. Taille max: 5 MB.";
        }

        return null;
    }

    private void renderTrackGrid() {
        musiqueGrid.getChildren().clear();
        for (int i = 0; i < visibleTracks.size(); i++) {
            Musique musique = visibleTracks.get(i);
            musiqueGrid.getChildren().add(createTrackCard(musique, i));
        }
    }

    private void handleDeleteMusique(Musique musique) {
        if (musique == null || musique.getId() == null) {
            setFeedback("Impossible de supprimer cette musique (id manquant).", false);
            return;
        }

        try {
            if (currentTrackIndex >= 0 && currentTrackIndex < visibleTracks.size()) {
                Musique current = visibleTracks.get(currentTrackIndex);
                if (current.getId() != null && current.getId().equals(musique.getId())) {
                    globalMediaPlayer.stop();
                    currentTrackIndex = -1;
                    setPlayPauseButtonText("Play");
                    setPlayerStatusText("Pret");
                }
            }

            musiqueService.delete(musique);
            setFeedback("Musique supprimee avec succes.", true);

            if (editingMusiqueId != null && editingMusiqueId.equals(musique.getId())) {
                clearForm();
                resetEditMode();
                showListView();
            }

            refreshMusiquesList();
        } catch (SQLDataException e) {
            setFeedback("Erreur lors de la suppression: " + e.getMessage(), false);
        }
    }

    private boolean confirmDeleteMusique(Musique musique) {
        String titre = musique != null && musique.getTitre() != null ? musique.getTitre() : "cette musique";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmer la suppression");
        alert.setHeaderText("Supprimer \"" + titre + "\" ?");
        alert.setContentText("Cette action est definitive.");

        return alert.showAndWait()
                .filter(buttonType -> buttonType == ButtonType.OK)
                .isPresent();
    }

    private VBox createTrackCard(Musique musique, int index) {
        VBox card = new VBox(8);
        card.setMinWidth(TRACK_CARD_WIDTH);
        card.setPrefWidth(TRACK_CARD_WIDTH);
        card.setMaxWidth(TRACK_CARD_WIDTH);
        card.getStyleClass().add("music-track-card");
        
        boolean isActive = (index == currentTrackIndex);
        boolean isPlaying = isActive && globalMediaPlayer.isPlaying();
        
        if (isActive) {
            card.getStyleClass().add("music-track-card-active");
        }

        // Lift transition on hover
        card.setOnMouseEntered(event -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(120), card);
            tt.setToY(-5);
            tt.play();
        });
        card.setOnMouseExited(event -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(120), card);
            tt.setToY(0);
            tt.play();
        });

        Node coverNode = buildCoverNode(musique.getImage(), isActive, isPlaying, index);

        String titre = musique.getTitre() != null ? musique.getTitre() : "Sans titre";
        Label titleLabel = new Label(titre);
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().add("music-track-title");

        Button menuButton = new Button("⋮");
        menuButton.getStyleClass().add("music-card-menu-button");

        MenuItem editItem = new MenuItem("Modifier");
        editItem.getStyleClass().add("music-actions-edit");
        editItem.setOnAction(event -> {
            event.consume();
            startEditMusique(musique);
        });

        MenuItem deleteItem = new MenuItem("Supprimer");
        deleteItem.getStyleClass().add("music-actions-delete");

        ContextMenu actionsMenu = new ContextMenu(editItem, deleteItem);
        actionsMenu.getStyleClass().add("music-actions-menu");

        menuButton.setOnMouseClicked(event -> {
            event.consume();
            if (actionsMenu.isShowing()) {
                actionsMenu.hide();
            } else {
                actionsMenu.show(menuButton, Side.BOTTOM, 0, 0);
            }
        });

        deleteItem.setOnAction(event -> {
            event.consume();
            if (confirmDeleteMusique(musique)) {
                handleDeleteMusique(musique);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox titleRow = new HBox(4, titleLabel, spacer, menuButton);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        String artist = resolveTrackArtistName(musique);
        Label artistLabel = new Label(artist);
        artistLabel.getStyleClass().add("music-track-artist");

        String genre = musique.getGenre() != null ? musique.getGenre() : "-";
        Label genreBadge = new Label(genre);
        genreBadge.getStyleClass().add("music-genre-badge");

        HBox badgeRow = new HBox(genreBadge);
        badgeRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        card.getChildren().addAll(coverNode, titleRow, artistLabel, badgeRow);
        card.setOnMouseClicked(event -> playTrackAtIndex(index, true));
        return card;
    }

    private Node buildCoverNode(String imageSource, boolean isActive, boolean isPlaying, int index) {
        StackPane placeholder = createCoverContainer(TRACK_COVER_SIZE, TRACK_COVER_SIZE);
        placeholder.getStyleClass().add("music-cover-placeholder");

        Node contentNode;
        if (imageSource == null || imageSource.isBlank()) {
            Label noImageLabel = new Label("🎵");
            noImageLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: #9ca3af;");
            placeholder.getChildren().add(noImageLabel);
            contentNode = placeholder;
        } else {
            try {
                Image image = loadImageSafely(imageSource);
                if (image == null) {
                    Label noImageLabel = new Label("🎵");
                    noImageLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: #9ca3af;");
                    placeholder.getChildren().add(noImageLabel);
                    contentNode = placeholder;
                } else {
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(TRACK_COVER_SIZE);
                    imageView.setFitHeight(TRACK_COVER_SIZE);
                    imageView.setPreserveRatio(false);
                    imageView.setSmooth(true);
                    imageView.getStyleClass().add("music-cover-image");

                    StackPane coverWrap = createCoverContainer(TRACK_COVER_SIZE, TRACK_COVER_SIZE);
                    coverWrap.getStyleClass().add("music-cover-placeholder");
                    coverWrap.getChildren().add(imageView);
                    contentNode = coverWrap;
                }
            } catch (Exception ex) {
                Label noImageLabel = new Label("🎵");
                noImageLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: #9ca3af;");
                placeholder.getChildren().add(noImageLabel);
                contentNode = placeholder;
            }
        }

        StackPane coverStack = (StackPane) contentNode;

        StackPane hoverOverlay = new StackPane();
        hoverOverlay.getStyleClass().add("music-cover-hover-overlay");

        Button hoverPlayBtn = new Button();
        hoverPlayBtn.getStyleClass().add("music-cover-hover-play-button");

        if (isActive) {
            hoverPlayBtn.setText(isPlaying ? "⏸" : "▶");
            hoverOverlay.setOpacity(0.8);
        } else {
            hoverPlayBtn.setText("▶");
            hoverOverlay.setOpacity(0.0);
        }

        hoverPlayBtn.setOnAction(event -> {
            event.consume();
            if (isActive) {
                globalMediaPlayer.togglePlayPause();
            } else {
                playTrackAtIndex(index, true);
            }
        });

        hoverOverlay.getChildren().add(hoverPlayBtn);
        coverStack.getChildren().add(hoverOverlay);

        if (!isActive) {
            coverStack.setOnMouseEntered(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(150), hoverOverlay);
                fade.setToValue(0.85);
                fade.play();
            });
            coverStack.setOnMouseExited(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(150), hoverOverlay);
                fade.setToValue(0.0);
                fade.play();
            });
        } else {
            coverStack.setOnMouseEntered(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(100), hoverOverlay);
                fade.setToValue(1.0);
                fade.play();
            });
            coverStack.setOnMouseExited(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(100), hoverOverlay);
                fade.setToValue(0.8);
                fade.play();
            });
        }

        // Live equalizer badge on top right
        if (isActive) {
            HBox eqContainer = new HBox(2);
            eqContainer.getStyleClass().add("music-eq-badge");
            eqContainer.setAlignment(javafx.geometry.Pos.CENTER);
            eqContainer.setMaxSize(30, 20);
            StackPane.setAlignment(eqContainer, javafx.geometry.Pos.TOP_RIGHT);
            StackPane.setMargin(eqContainer, new javafx.geometry.Insets(8));

            for (int j = 0; j < 3; j++) {
                Rectangle bar = new Rectangle(3, 10);
                bar.setArcWidth(1.5);
                bar.setArcHeight(1.5);
                bar.setStyle("-fx-fill: white;");
                eqContainer.getChildren().add(bar);

                if (isPlaying) {
                    javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(Duration.ZERO, new javafx.animation.KeyValue(bar.scaleYProperty(), 0.2)),
                        new javafx.animation.KeyFrame(Duration.millis(250 + new java.util.Random().nextInt(250)), new javafx.animation.KeyValue(bar.scaleYProperty(), 1.0))
                    );
                    timeline.setAutoReverse(true);
                    timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
                    timeline.play();
                } else {
                    bar.setScaleY(0.2);
                }
            }
            coverStack.getChildren().add(eqContainer);
        }

        return coverStack;
    }

    private void filterTracks(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        visibleTracks.setAll(allTracks.stream()
                .filter(track -> normalizedQuery.isEmpty() || matchesQuery(track, normalizedQuery))
                .toList());

        // Apply sorting
        applySortToTracks();

        if (visibleTracks.isEmpty()) {
            currentTrackIndex = -1;
            globalMediaPlayer.stop();
            setNowPlayingText("Selectionnez une musique", "Genre: -");
            setPlayerStatusText("Pret");
            setPlayPauseButtonText("Play");
        } else if (currentTrackIndex >= visibleTracks.size()) {
            currentTrackIndex = -1;
        }

        renderTrackGrid();
        emptyListLabel.setVisible(visibleTracks.isEmpty());
        emptyListLabel.setManaged(visibleTracks.isEmpty());

        if (!visibleTracks.isEmpty() && currentTrackIndex < 0) {
            updateNowPlayingLabels(visibleTracks.get(0));
        }
    }

    private void filterPlaylists(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        visiblePlaylists.setAll(allPlaylists.stream()
                .filter(playlist -> normalizedQuery.isEmpty() || matchesPlaylistQuery(playlist, normalizedQuery))
                .toList());

        applySortToPlaylists();
        renderPlaylistGrid();

        if (playlistEmptyListLabel != null) {
            playlistEmptyListLabel.setVisible(visiblePlaylists.isEmpty());
            playlistEmptyListLabel.setManaged(visiblePlaylists.isEmpty());
        }
    }

    private boolean matchesPlaylistQuery(Playlist playlist, String query) {
        String name = playlist.getNom() != null ? playlist.getNom().toLowerCase(Locale.ROOT) : "";
        String description = playlist.getDescription() != null ? playlist.getDescription().toLowerCase(Locale.ROOT) : "";
        return name.contains(query) || description.contains(query);
    }

    private boolean matchesQuery(Musique musique, String query) {
        String titre = musique.getTitre() != null ? musique.getTitre().toLowerCase(Locale.ROOT) : "";
        String description = musique.getDescription() != null ? musique.getDescription().toLowerCase(Locale.ROOT) : "";
        String genre = musique.getGenre() != null ? musique.getGenre().toLowerCase(Locale.ROOT) : "";
        return titre.contains(query) || description.contains(query) || genre.contains(query);
    }

    private void applySortToTracks() {
        if (sortMusicComboBox == null) {
            return;
        }
        String sortOption = sortMusicComboBox.getValue();
        if (sortOption == null) {
            return;
        }

        switch (sortOption) {
            case "Titre (A-Z)" -> visibleTracks.sort((a, b) -> {
                String titleA = a.getTitre() != null ? a.getTitre() : "";
                String titleB = b.getTitre() != null ? b.getTitre() : "";
                return titleA.compareToIgnoreCase(titleB);
            });
            case "Titre (Z-A)" -> visibleTracks.sort((a, b) -> {
                String titleA = a.getTitre() != null ? a.getTitre() : "";
                String titleB = b.getTitre() != null ? b.getTitre() : "";
                return titleB.compareToIgnoreCase(titleA);
            });
            case "Genre (A-Z)" -> visibleTracks.sort((a, b) -> {
                String genreA = a.getGenre() != null ? a.getGenre() : "";
                String genreB = b.getGenre() != null ? b.getGenre() : "";
                return genreA.compareToIgnoreCase(genreB);
            });
            case "Récent" -> visibleTracks.sort((a, b) -> {
                LocalDate dateA = a.getDateCreation() != null ? a.getDateCreation() : LocalDate.MIN;
                LocalDate dateB = b.getDateCreation() != null ? b.getDateCreation() : LocalDate.MIN;
                return dateB.compareTo(dateA);
            });
        }
    }

    private void applySortToPlaylists() {
        if (sortPlaylistComboBox == null) {
            return;
        }
        String sortOption = sortPlaylistComboBox.getValue();
        if (sortOption == null) {
            return;
        }

        switch (sortOption) {
            case "Nom (A-Z)" -> visiblePlaylists.sort((a, b) -> {
                String nameA = a.getNom() != null ? a.getNom() : "";
                String nameB = b.getNom() != null ? b.getNom() : "";
                return nameA.compareToIgnoreCase(nameB);
            });
            case "Nom (Z-A)" -> visiblePlaylists.sort((a, b) -> {
                String nameA = a.getNom() != null ? a.getNom() : "";
                String nameB = b.getNom() != null ? b.getNom() : "";
                return nameB.compareToIgnoreCase(nameA);
            });
            case "Récent" -> visiblePlaylists.sort((a, b) -> {
                LocalDate dateA = a.getDateCreation() != null ? a.getDateCreation() : LocalDate.MIN;
                LocalDate dateB = b.getDateCreation() != null ? b.getDateCreation() : LocalDate.MIN;
                return dateB.compareTo(dateA);
            });
        }
    }

    private void renderPlaylistGrid() {
        if (playlistGrid == null) {
            return;
        }
        playlistGrid.getChildren().clear();
        for (Playlist playlist : visiblePlaylists) {
            playlistGrid.getChildren().add(createPlaylistCard(playlist));
        }
    }

    private VBox createPlaylistCard(Playlist playlist) {
        VBox card = new VBox(6);
        card.setMinWidth(PLAYLIST_CARD_WIDTH);
        card.setPrefWidth(PLAYLIST_CARD_WIDTH);
        card.setMaxWidth(PLAYLIST_CARD_WIDTH);
        card.getStyleClass().add("music-track-card");

        Node coverNode = buildPlaylistCoverNode(playlist.getImage());
        Label titleLabel = new Label(safePlaylistName(playlist));
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().add("music-track-title");

        String description = playlist.getDescription() != null && !playlist.getDescription().isBlank()
                ? playlist.getDescription()
                : "Aucune description";
        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("music-track-meta");

        Label countLabel = new Label((playlist.getMusiques() != null ? playlist.getMusiques().size() : 0) + " musique(s)");
        countLabel.getStyleClass().add("music-track-meta");

        card.getChildren().addAll(coverNode, titleLabel, descLabel, countLabel);
        return card;
    }

    private Node buildPlaylistCoverNode(String imageSource) {
        StackPane placeholder = createCoverContainer(PLAYLIST_COVER_WIDTH, PLAYLIST_COVER_HEIGHT);
        placeholder.getStyleClass().add("music-cover-placeholder");

        if (imageSource == null || imageSource.isBlank()) {
            Label noImageLabel = new Label("Playlist");
            noImageLabel.getStyleClass().add("music-cover-placeholder-text");
            placeholder.getChildren().add(noImageLabel);
            return placeholder;
        }

        try {
            Image image = loadImageSafely(imageSource);
            if (image == null) {
                Label noImageLabel = new Label("Playlist");
                noImageLabel.getStyleClass().add("music-cover-placeholder-text");
                placeholder.getChildren().add(noImageLabel);
                return placeholder;
            }

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(PLAYLIST_COVER_WIDTH);
            imageView.setFitHeight(PLAYLIST_COVER_HEIGHT);
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);
            imageView.getStyleClass().add("music-cover-image");

            StackPane coverWrap = createCoverContainer(PLAYLIST_COVER_WIDTH, PLAYLIST_COVER_HEIGHT);
            coverWrap.getStyleClass().add("music-cover-placeholder");
            coverWrap.getChildren().add(imageView);
            return coverWrap;
        } catch (Exception ex) {
            Label noImageLabel = new Label("Playlist");
            noImageLabel.getStyleClass().add("music-cover-placeholder-text");
            placeholder.getChildren().add(noImageLabel);
            return placeholder;
        }
    }

    private String safePlaylistName(Playlist playlist) {
        if (playlist == null || playlist.getNom() == null || playlist.getNom().isBlank()) {
            return "Playlist sans nom";
        }
        return playlist.getNom();
    }

    private StackPane createCoverContainer(double width, double height) {
        StackPane container = new StackPane();
        container.setMinSize(width, height);
        container.setPrefSize(width, height);
        container.setMaxSize(width, height);

        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        container.setClip(clip);
        return container;
    }

    private Image loadImageSafely(String imageSource) {
        if (imageSource == null || imageSource.isBlank()) {
            return null;
        }

        try {
            String trimmed = imageSource.trim();

            // Prefer local resolution first for known web paths so UI does not depend on localhost web server availability.
            File localImage = resolveLocalImageFile(trimmed);
            if (localImage != null && localImage.exists() && localImage.isFile()) {
                Image local = new Image(localImage.toURI().toString(), false);
                if (!local.isError()) {
                    return local;
                }
            }

            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                Image remoteImage = new Image(trimmed, true);
                return remoteImage.isError() ? null : remoteImage;
            }

            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private File resolveLocalImageFile(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }

        String trimmed = source.trim();
        if (trimmed.length() > 1 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("/") && trimmed.length() > 2 && trimmed.charAt(2) == ':') {
            trimmed = trimmed.substring(1);
        }

        if (trimmed.startsWith(ImageUrlUtils.IMAGE_BASE_URL)) {
            String fileName = trimmed.substring(ImageUrlUtils.IMAGE_BASE_URL.length()).trim();
            return fileName.isEmpty() ? null : new File(XAMPP_IMAGE_DIR, fileName);
        }

        if (trimmed.startsWith("/img/") || trimmed.startsWith("/htdocs/img/")) {
            String fileName = extractFileName(trimmed);
            return fileName.isEmpty() ? null : new File(XAMPP_IMAGE_DIR, fileName);
        }

        if (trimmed.startsWith("file:")) {
            try {
                return new File(new URI(trimmed));
            } catch (Exception ignored) {
                String rawPath = trimmed.substring("file:".length());
                if (rawPath.startsWith("//")) {
                    rawPath = rawPath.substring(2);
                }
                if (rawPath.startsWith("/") && rawPath.length() > 2 && rawPath.charAt(2) == ':') {
                    rawPath = rawPath.substring(1);
                }
                return new File(rawPath);
            }
        }

        return new File(trimmed);
    }

    private String extractFileName(String value) {
        String normalized = value.replace('\\', '/');
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int lastSlash = normalized.lastIndexOf('/');
        return (lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized).trim();
    }

    public static final class CollectionChoice {
        private final int id;
        private final String titre;

        public CollectionChoice(int id, String titre) {
            this.id = id;
            this.titre = titre != null ? titre : ("Collection " + id);
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return titre;
        }
    }
}
