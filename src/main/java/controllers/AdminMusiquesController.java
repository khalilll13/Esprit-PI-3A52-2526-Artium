package controllers;

import services.MusiqueService;
import services.PlaylistService;
import entities.Musique;
import entities.Playlist;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import utils.ImageUrlUtils;

import java.io.File;
import java.net.URI;
import java.sql.SQLDataException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class AdminMusiquesController {
    private static final String XAMPP_IMAGE_DIR = "C:\\xampp\\htdocs\\img";
    private static final double TRACK_CARD_WIDTH = 170;
    private static final double PLAYLIST_CARD_WIDTH = 180;
    private static final double TRACK_COVER_SIZE = 150;
    private static final double PLAYLIST_COVER_WIDTH = 160;
    private static final double PLAYLIST_COVER_HEIGHT = 110;

    @FXML
    private Button musiqueSectionButton;

    @FXML
    private Button playlistsSectionButton;

    @FXML
    private HBox musicSearchBar;

    @FXML
    private HBox playlistSearchBar;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> sortMusicComboBox;

    @FXML
    private TextField playlistSearchField;

    @FXML
    private ComboBox<String> sortPlaylistComboBox;

    @FXML
    private VBox musiqueSection;

    @FXML
    private VBox playlistsSection;

    @FXML
    private TilePane musicGrid;

    @FXML
    private TilePane playlistGrid;

    @FXML
    private Label emptyListLabel;

    @FXML
    private Label playlistEmptyListLabel;

    @FXML
    private VBox playlistDetailSection;

    @FXML
    private Label playlistDetailTitleLabel;

    @FXML
    private Label playlistDetailMetaLabel;

    @FXML
    private Label playlistSongsEmptyLabel;

    @FXML
    private VBox playlistSongsBox;

    @FXML
    private Label nowPlayingTitleLabel;

    @FXML
    private Label nowPlayingMetaLabel;

    @FXML
    private Label playerStatusLabel;

    @FXML
    private Button playPauseButton;

    private final MusiqueService musiqueService = new MusiqueService();
    private final PlaylistService playlistService = new PlaylistService();

    private final ObservableList<Musique> allTracks = FXCollections.observableArrayList();
    private final ObservableList<Musique> visibleTracks = FXCollections.observableArrayList();
    private final ObservableList<Playlist> allPlaylists = FXCollections.observableArrayList();
    private final ObservableList<Playlist> visiblePlaylists = FXCollections.observableArrayList();

    private MediaPlayer mediaPlayer;
    private int currentTrackIndex = -1;
    private Integer selectedPlaylistId;

    @FXML
    public void initialize() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldValue, newValue) -> filterTracks(newValue));
        }
        if (playlistSearchField != null) {
            playlistSearchField.textProperty().addListener((obs, oldValue, newValue) -> filterPlaylists(newValue));
        }

        if (sortMusicComboBox != null) {
            sortMusicComboBox.setItems(FXCollections.observableArrayList(
                    "Titre (A-Z)", "Titre (Z-A)", "Genre (A-Z)", "Recent"
            ));
            sortMusicComboBox.getSelectionModel().selectFirst();
            sortMusicComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> filterTracks(searchField != null ? searchField.getText() : null));
        }

        if (sortPlaylistComboBox != null) {
            sortPlaylistComboBox.setItems(FXCollections.observableArrayList(
                    "Nom (A-Z)", "Nom (Z-A)", "Recent"
            ));
            sortPlaylistComboBox.getSelectionModel().selectFirst();
            sortPlaylistComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> filterPlaylists(playlistSearchField != null ? playlistSearchField.getText() : null));
        }

        configureGridSizing();

        setActiveSection(true);
        hidePlaylistDetails();
        refreshTracks();
        refreshPlaylists();
    }

    private void configureGridSizing() {
        if (musicGrid != null) {
            musicGrid.setPrefTileWidth(TRACK_CARD_WIDTH);
            musicGrid.setTileAlignment(javafx.geometry.Pos.TOP_LEFT);
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
    private void handleSearch() {
        filterTracks(searchField != null ? searchField.getText() : null);
    }

    @FXML
    private void handleClearSearch() {
        if (searchField != null) {
            searchField.clear();
        }
        filterTracks(null);
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
    private void handleClosePlaylistDetail() {
        hidePlaylistDetails();
    }

    @FXML
    private void handlePlayPause() {
        if (mediaPlayer == null) {
            int selectedIndex = currentTrackIndex;
            if (selectedIndex < 0 && !visibleTracks.isEmpty()) {
                selectedIndex = 0;
            }
            if (selectedIndex >= 0) {
                playTrackAtIndex(selectedIndex);
            }
            return;
        }

        MediaPlayer.Status status = mediaPlayer.getStatus();
        if (status == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            setPlayPauseText("Play");
            setPlayerStatusText("En pause");
        } else {
            mediaPlayer.play();
            setPlayPauseText("Pause");
            setPlayerStatusText("Lecture en cours");
        }
    }

    @FXML
    private void handlePreviousTrack() {
        if (visibleTracks.isEmpty()) {
            return;
        }
        int targetIndex = currentTrackIndex <= 0 ? visibleTracks.size() - 1 : currentTrackIndex - 1;
        playTrackAtIndex(targetIndex);
    }

    @FXML
    private void handleNextTrack() {
        if (visibleTracks.isEmpty()) {
            return;
        }
        int targetIndex = currentTrackIndex >= visibleTracks.size() - 1 ? 0 : currentTrackIndex + 1;
        playTrackAtIndex(targetIndex);
    }

    private void refreshTracks() {
        try {
            allTracks.setAll(musiqueService.getAll());
            filterTracks(searchField != null ? searchField.getText() : null);
        } catch (SQLDataException e) {
            allTracks.clear();
            visibleTracks.clear();
            renderTrackGrid();
            if (emptyListLabel != null) {
                emptyListLabel.setText("Impossible de charger les musiques: " + e.getMessage());
                emptyListLabel.setVisible(true);
                emptyListLabel.setManaged(true);
            }
        }
    }

    private void refreshPlaylists() {
        try {
            allPlaylists.setAll(playlistService.getAll());
            filterPlaylists(playlistSearchField != null ? playlistSearchField.getText() : null);
            if (playlistDetailSection != null && playlistDetailSection.isVisible()) {
                Playlist refreshed = findPlaylistById(selectedPlaylistId);
                if (refreshed != null) {
                    showPlaylistDetails(refreshed);
                } else {
                    hidePlaylistDetails();
                }
            }
        } catch (SQLDataException e) {
            allPlaylists.clear();
            visiblePlaylists.clear();
            renderPlaylistGrid();
            if (playlistEmptyListLabel != null) {
                playlistEmptyListLabel.setText("Impossible de charger les playlists: " + e.getMessage());
                playlistEmptyListLabel.setVisible(true);
                playlistEmptyListLabel.setManaged(true);
            }
            hidePlaylistDetails();
        }
    }

    private void filterTracks(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        visibleTracks.setAll(allTracks.stream()
                .filter(track -> normalizedQuery.isEmpty() || matchesTrackQuery(track, normalizedQuery))
                .toList());

        applySortToTracks();

        if (visibleTracks.isEmpty()) {
            currentTrackIndex = -1;
            stopPlayer();
            setNowPlayingTitle("Selectionnez une musique");
            setNowPlayingMeta("Genre: -");
            setPlayerStatusText("Pret");
            setPlayPauseText("Play");
        } else if (currentTrackIndex >= visibleTracks.size()) {
            currentTrackIndex = -1;
        }

        renderTrackGrid();
        if (emptyListLabel != null) {
            emptyListLabel.setVisible(visibleTracks.isEmpty());
            emptyListLabel.setManaged(visibleTracks.isEmpty());
        }

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

    private boolean matchesTrackQuery(Musique musique, String query) {
        String titre = musique.getTitre() != null ? musique.getTitre().toLowerCase(Locale.ROOT) : "";
        String description = musique.getDescription() != null ? musique.getDescription().toLowerCase(Locale.ROOT) : "";
        String genre = musique.getGenre() != null ? musique.getGenre().toLowerCase(Locale.ROOT) : "";
        return titre.contains(query) || description.contains(query) || genre.contains(query);
    }

    private boolean matchesPlaylistQuery(Playlist playlist, String query) {
        String name = playlist.getNom() != null ? playlist.getNom().toLowerCase(Locale.ROOT) : "";
        String description = playlist.getDescription() != null ? playlist.getDescription().toLowerCase(Locale.ROOT) : "";
        return name.contains(query) || description.contains(query);
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
            case "Recent" -> visibleTracks.sort((a, b) -> {
                LocalDate dateA = a.getDateCreation() != null ? a.getDateCreation() : LocalDate.MIN;
                LocalDate dateB = b.getDateCreation() != null ? b.getDateCreation() : LocalDate.MIN;
                return dateB.compareTo(dateA);
            });
            default -> {
            }
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
            case "Recent" -> visiblePlaylists.sort((a, b) -> {
                LocalDate dateA = a.getDateCreation() != null ? a.getDateCreation() : LocalDate.MIN;
                LocalDate dateB = b.getDateCreation() != null ? b.getDateCreation() : LocalDate.MIN;
                return dateB.compareTo(dateA);
            });
            default -> {
            }
        }
    }

    private void renderTrackGrid() {
        if (musicGrid == null) {
            return;
        }
        musicGrid.getChildren().clear();
        for (int i = 0; i < visibleTracks.size(); i++) {
            Musique musique = visibleTracks.get(i);
            musicGrid.getChildren().add(createTrackCard(musique, i));
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

    private VBox createTrackCard(Musique musique, int index) {
        VBox card = new VBox(6);
        card.setMinWidth(TRACK_CARD_WIDTH);
        card.setPrefWidth(TRACK_CARD_WIDTH);
        card.setMaxWidth(TRACK_CARD_WIDTH);
        card.setStyle(index == currentTrackIndex
                ? "-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-border-color: #38bdf8; -fx-border-width: 2; -fx-border-radius: 12; -fx-padding: 8; -fx-effect: dropshadow(gaussian, rgba(56,189,248,0.2), 12, 0, 0, 4);"
                : "-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-padding: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.02), 8, 0, 0, 2);");

        Node coverNode = buildCoverNode(musique.getImage());

        String titre = musique.getTitre() != null ? musique.getTitre() : "Sans titre";
        Label titleLabel = new Label(titre);
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-weight: 800;");

        String genre = musique.getGenre() != null ? musique.getGenre() : "-";
        Label metaLabel = new Label("Genre: " + genre);
        metaLabel.setStyle("-fx-text-fill: #64748b;");

        Button playButton = new Button("Play");
        playButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #0f172a; -fx-font-weight: bold; -fx-background-radius: 6;");
        playButton.setOnAction(event -> {
            event.consume();
            playTrackAtIndex(index);
        });

        Button deleteButton = new Button("Supprimer");
        deleteButton.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-background-radius: 6;");
        deleteButton.setOnAction(event -> {
            event.consume();
            if (confirmDeleteMusique(musique)) {
                deleteMusique(musique);
            }
        });

        HBox actions = new HBox(8, playButton, deleteButton);

        card.getChildren().addAll(coverNode, titleLabel, metaLabel, actions);
        card.setOnMouseClicked(event -> playTrackAtIndex(index));
        return card;
    }

    private VBox createPlaylistCard(Playlist playlist) {
        VBox card = new VBox(6);
        card.setMinWidth(PLAYLIST_CARD_WIDTH);
        card.setPrefWidth(PLAYLIST_CARD_WIDTH);
        card.setMaxWidth(PLAYLIST_CARD_WIDTH);
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-padding: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.02), 8, 0, 0, 2);");

        Node coverNode = buildPlaylistCoverNode(playlist.getImage());

        Label nameLabel = new Label(safePlaylistName(playlist));
        nameLabel.setWrapText(true);
        nameLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-weight: 800;");

        String description = playlist.getDescription() != null && !playlist.getDescription().isBlank()
                ? playlist.getDescription()
                : "Aucune description";
        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-text-fill: #64748b;");

        Label countLabel = new Label((playlist.getMusiques() != null ? playlist.getMusiques().size() : 0) + " musique(s)");
        countLabel.setStyle("-fx-text-fill: #94a3b8;");

        Button openButton = new Button("Ouvrir");
        openButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #0f172a; -fx-font-weight: bold; -fx-background-radius: 6;");
        openButton.setOnAction(event -> {
            event.consume();
            showPlaylistDetails(playlist);
        });

        Button deleteButton = new Button("Supprimer");
        deleteButton.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-background-radius: 6;");
        deleteButton.setOnAction(event -> {
            event.consume();
            if (confirmDeletePlaylist(playlist)) {
                deletePlaylist(playlist);
            }
        });

        HBox actions = new HBox(8, openButton, deleteButton);

        card.getChildren().addAll(coverNode, nameLabel, descriptionLabel, countLabel, actions);
        card.setOnMouseClicked(event -> showPlaylistDetails(playlist));
        return card;
    }

    private void playTrackAtIndex(int index) {
        if (index < 0 || index >= visibleTracks.size()) {
            return;
        }

        Musique selectedTrack = visibleTracks.get(index);
        String source = toMediaSource(selectedTrack.getAudio());
        if (source == null) {
            setPlayerStatusText("Fichier audio introuvable");
            return;
        }

        stopPlayer();
        try {
            Media media = new Media(source);
            mediaPlayer = new MediaPlayer(media);
            currentTrackIndex = index;
            renderTrackGrid();
            updateNowPlayingLabels(selectedTrack);

            mediaPlayer.setOnReady(() -> {
                mediaPlayer.play();
                setPlayPauseText("Pause");
                setPlayerStatusText("Lecture en cours");
            });
            mediaPlayer.setOnEndOfMedia(this::handleNextTrack);
            mediaPlayer.setOnError(() -> setPlayerStatusText(describeMediaError(mediaPlayer.getError())));
        } catch (MediaException mediaException) {
            setPlayPauseText("Play");
            setPlayerStatusText(describeMediaError(mediaException));
        } catch (RuntimeException runtimeException) {
            setPlayPauseText("Play");
            setPlayerStatusText("Impossible de lire ce fichier audio");
        }
    }

    private void showPlaylistDetails(Playlist playlist) {
        if (playlist == null) {
            hidePlaylistDetails();
            return;
        }

        selectedPlaylistId = playlist.getId();
        if (playlistDetailSection != null) {
            playlistDetailSection.setVisible(true);
            playlistDetailSection.setManaged(true);
        }
        if (playlistDetailTitleLabel != null) {
            playlistDetailTitleLabel.setText(safePlaylistName(playlist));
        }

        List<Musique> tracks = fetchPlaylistTracks(playlist);

        if (playlistDetailMetaLabel != null) {
            String description = playlist.getDescription() != null && !playlist.getDescription().isBlank()
                    ? playlist.getDescription() + " - "
                    : "";
            playlistDetailMetaLabel.setText(description + tracks.size() + " musique(s)");
        }

        if (playlistSongsBox != null) {
            playlistSongsBox.getChildren().clear();
            boolean empty = tracks.isEmpty();
            if (playlistSongsEmptyLabel != null) {
                playlistSongsEmptyLabel.setVisible(empty);
                playlistSongsEmptyLabel.setManaged(empty);
            }
            for (Musique track : tracks) {
                HBox row = new HBox(8);
                Label title = new Label(track.getTitre() != null ? track.getTitre() : "Sans titre");
                title.setStyle("-fx-text-fill: #0f172a; -fx-font-weight: 700;");

                Button playButton = new Button("Play");
                playButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #0f172a; -fx-font-weight: bold; -fx-background-radius: 6;");
                playButton.setOnAction(event -> playTrackFromAnyList(track));

                Button removeButton = new Button("Retirer");
                removeButton.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-background-radius: 6;");
                removeButton.setOnAction(event -> {
                    event.consume();
                    if (selectedPlaylistId == null || track == null || track.getId() == null) {
                        Alert warn = new Alert(Alert.AlertType.WARNING);
                        warn.setTitle("Retirer de la playlist");
                        warn.setHeaderText(null);
                        warn.setContentText("Identifiants manquants pour retirer la musique.");
                        warn.showAndWait();
                        return;
                    }

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Retirer de la playlist");
                    confirm.setHeaderText("Retirer \"" + (track.getTitre() != null ? track.getTitre() : "cette musique") + "\" ?");
                    confirm.setContentText("Confirmer la suppression de cette musique de la playlist.");
                    java.util.Optional<ButtonType> result = confirm.showAndWait();
                    if (result.isEmpty() || result.get() != ButtonType.OK) {
                        return;
                    }

                    try {
                        playlistService.removeMusiqueFromPlaylist(selectedPlaylistId, track.getId());
                        // refresh UI
                        Playlist refreshed = findPlaylistById(selectedPlaylistId);
                        if (refreshed != null) {
                            showPlaylistDetails(refreshed);
                        } else {
                            hidePlaylistDetails();
                        }
                        refreshPlaylists();
                    } catch (SQLDataException ex) {
                        Alert err = new Alert(Alert.AlertType.ERROR);
                        err.setTitle("Erreur");
                        err.setHeaderText("Impossible de retirer la musique");
                        err.setContentText(ex.getMessage());
                        err.showAndWait();
                    }
                });

                row.getChildren().addAll(title, playButton, removeButton);
                playlistSongsBox.getChildren().add(row);
            }
        }
    }

    private List<Musique> fetchPlaylistTracks(Playlist playlist) {
        try {
            if (playlist != null && playlist.getId() != null) {
                List<Musique> tracks = playlistService.getMusiquesForPlaylist(playlist.getId());
                playlist.setMusiques(tracks);
                return tracks;
            }
        } catch (SQLDataException ignored) {
            return List.of();
        }
        return List.of();
    }

    private void hidePlaylistDetails() {
        selectedPlaylistId = null;
        if (playlistDetailSection != null) {
            playlistDetailSection.setVisible(false);
            playlistDetailSection.setManaged(false);
        }
        if (playlistSongsBox != null) {
            playlistSongsBox.getChildren().clear();
        }
        if (playlistSongsEmptyLabel != null) {
            playlistSongsEmptyLabel.setVisible(true);
            playlistSongsEmptyLabel.setManaged(true);
        }
        if (playlistDetailTitleLabel != null) {
            playlistDetailTitleLabel.setText("Aucune playlist selectionnee");
        }
        if (playlistDetailMetaLabel != null) {
            playlistDetailMetaLabel.setText("Cliquez sur une playlist pour voir ses musiques.");
        }
    }

    private Playlist findPlaylistById(Integer playlistId) {
        if (playlistId == null) {
            return null;
        }
        for (Playlist playlist : allPlaylists) {
            if (playlist.getId() != null && playlist.getId().equals(playlistId)) {
                return playlist;
            }
        }
        return null;
    }

    private void deleteMusique(Musique musique) {
        if (musique == null || musique.getId() == null) {
            return;
        }

        try {
            if (currentTrackIndex >= 0 && currentTrackIndex < visibleTracks.size()) {
                Musique current = visibleTracks.get(currentTrackIndex);
                if (current.getId() != null && current.getId().equals(musique.getId())) {
                    stopPlayer();
                    currentTrackIndex = -1;
                    setPlayPauseText("Play");
                    setPlayerStatusText("Pret");
                }
            }

            musiqueService.delete(musique);
            refreshTracks();
            refreshPlaylists();
        } catch (SQLDataException ignored) {
            // Keep UI stable even if backend deletion fails.
        }
    }

    private void deletePlaylist(Playlist playlist) {
        if (playlist == null || playlist.getId() == null) {
            return;
        }

        try {
            playlistService.delete(playlist);
            if (selectedPlaylistId != null && selectedPlaylistId.equals(playlist.getId())) {
                hidePlaylistDetails();
            }
            refreshPlaylists();
        } catch (SQLDataException ignored) {
            // Keep UI stable even if backend deletion fails.
        }
    }

    private boolean confirmDeleteMusique(Musique musique) {
        String title = musique != null && musique.getTitre() != null ? musique.getTitre() : "cette musique";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer la musique");
        alert.setHeaderText("Supprimer \"" + title + "\" ?");
        alert.setContentText("Cette action est definitive.");
        return alert.showAndWait().filter(buttonType -> buttonType == ButtonType.OK).isPresent();
    }

    private boolean confirmDeletePlaylist(Playlist playlist) {
        String name = safePlaylistName(playlist);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer la playlist");
        alert.setHeaderText("Supprimer \"" + name + "\" ?");
        alert.setContentText("Cette action est definitive.");
        return alert.showAndWait().filter(buttonType -> buttonType == ButtonType.OK).isPresent();
    }

    private void playTrackFromAnyList(Musique track) {
        if (track == null) {
            return;
        }

        int index = -1;
        for (int i = 0; i < visibleTracks.size(); i++) {
            Musique candidate = visibleTracks.get(i);
            if (candidate.getId() != null && candidate.getId().equals(track.getId())) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            playTrackAtIndex(index);
            return;
        }

        String source = toMediaSource(track.getAudio());
        if (source == null) {
            setPlayerStatusText("Fichier audio introuvable");
            return;
        }

        stopPlayer();
        try {
            Media media = new Media(source);
            mediaPlayer = new MediaPlayer(media);
            currentTrackIndex = -1;
            renderTrackGrid();
            updateNowPlayingLabels(track);
            mediaPlayer.setOnReady(() -> {
                mediaPlayer.play();
                setPlayPauseText("Pause");
                setPlayerStatusText("Lecture en cours");
            });
            mediaPlayer.setOnError(() -> setPlayerStatusText(describeMediaError(mediaPlayer.getError())));
        } catch (MediaException mediaException) {
            setPlayPauseText("Play");
            setPlayerStatusText(describeMediaError(mediaException));
        }
    }

    private void setActiveSection(boolean showMusique) {
        if (musicSearchBar != null) {
            musicSearchBar.setVisible(showMusique);
            musicSearchBar.setManaged(showMusique);
        }
        if (playlistSearchBar != null) {
            playlistSearchBar.setVisible(!showMusique);
            playlistSearchBar.setManaged(!showMusique);
        }

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
                    ? "-fx-background-color: #198754; -fx-text-fill: white;"
                    : "-fx-background-color: #4b5563; -fx-text-fill: white;");
        }
        if (playlistsSectionButton != null) {
            playlistsSectionButton.setStyle(!showMusique
                    ? "-fx-background-color: #198754; -fx-text-fill: white;"
                    : "-fx-background-color: #4b5563; -fx-text-fill: white;");
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

    private void stopPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }

    private void updateNowPlayingLabels(Musique musique) {
        String titre = musique.getTitre() != null ? musique.getTitre() : "Sans titre";
        String genre = musique.getGenre() != null ? musique.getGenre() : "-";
        setNowPlayingTitle(titre);
        setNowPlayingMeta("Genre: " + genre);
    }

    private String describeMediaError(Throwable throwable) {
        if (throwable instanceof MediaException mediaException) {
            if (mediaException.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                return "Format non pris en charge par JavaFX";
            }
            String message = mediaException.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("unrecognized file signature")) {
                return "Codec audio non pris en charge (essayez MP3/M4A/WAV)";
            }
        }
        return "Impossible de lire ce fichier audio";
    }

    private Node buildCoverNode(String imageSource) {
        StackPane placeholder = createCoverContainer(TRACK_COVER_SIZE, TRACK_COVER_SIZE);
        placeholder.setStyle("-fx-background-color: #2d333b; -fx-background-radius: 6;");

        if (imageSource == null || imageSource.isBlank()) {
            Label noImageLabel = new Label("No cover");
            noImageLabel.setStyle("-fx-text-fill: #9ca3af;");
            placeholder.getChildren().add(noImageLabel);
            return placeholder;
        }

        try {
            Image image = loadImageSafely(imageSource);
            if (image == null) {
                Label noImageLabel = new Label("No cover");
                noImageLabel.setStyle("-fx-text-fill: #9ca3af;");
                placeholder.getChildren().add(noImageLabel);
                return placeholder;
            }

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(TRACK_COVER_SIZE);
            imageView.setFitHeight(TRACK_COVER_SIZE);
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);

            StackPane coverWrap = createCoverContainer(TRACK_COVER_SIZE, TRACK_COVER_SIZE);
            coverWrap.setStyle("-fx-background-color: #2d333b; -fx-background-radius: 6;");
            coverWrap.getChildren().add(imageView);
            return coverWrap;
        } catch (Exception ex) {
            Label noImageLabel = new Label("No cover");
            noImageLabel.setStyle("-fx-text-fill: #9ca3af;");
            placeholder.getChildren().add(noImageLabel);
            return placeholder;
        }
    }

    private Node buildPlaylistCoverNode(String imageSource) {
        StackPane placeholder = createCoverContainer(PLAYLIST_COVER_WIDTH, PLAYLIST_COVER_HEIGHT);
        placeholder.setStyle("-fx-background-color: #2d333b; -fx-background-radius: 6;");

        if (imageSource == null || imageSource.isBlank()) {
            Label noImageLabel = new Label("Playlist");
            noImageLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-weight: bold;");
            placeholder.getChildren().add(noImageLabel);
            return placeholder;
        }

        try {
            Image image = loadImageSafely(imageSource);
            if (image == null) {
                Label noImageLabel = new Label("Playlist");
                noImageLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-weight: bold;");
                placeholder.getChildren().add(noImageLabel);
                return placeholder;
            }

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(PLAYLIST_COVER_WIDTH);
            imageView.setFitHeight(PLAYLIST_COVER_HEIGHT);
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);

            StackPane coverWrap = createCoverContainer(PLAYLIST_COVER_WIDTH, PLAYLIST_COVER_HEIGHT);
            coverWrap.setStyle("-fx-background-color: #2d333b; -fx-background-radius: 6;");
            coverWrap.getChildren().add(imageView);
            return coverWrap;
        } catch (Exception ex) {
            Label noImageLabel = new Label("Playlist");
            noImageLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-weight: bold;");
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
        container.setClip(new Rectangle(width, height));
        return container;
    }

    private Image loadImageSafely(String imageSource) {
        if (imageSource == null || imageSource.isBlank()) {
            return null;
        }

        try {
            String trimmed = imageSource.trim();

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

    private void setNowPlayingTitle(String text) {
        if (nowPlayingTitleLabel != null) {
            nowPlayingTitleLabel.setText(text);
        }
    }

    private void setNowPlayingMeta(String text) {
        if (nowPlayingMetaLabel != null) {
            nowPlayingMetaLabel.setText(text);
        }
    }

    private void setPlayerStatusText(String text) {
        if (playerStatusLabel != null) {
            playerStatusLabel.setText(text);
        }
    }

    private void setPlayPauseText(String text) {
        if (playPauseButton != null) {
            playPauseButton.setText(text);
        }
    }
}

