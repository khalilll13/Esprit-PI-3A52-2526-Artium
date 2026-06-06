package controllers.amateur;

import javafx.concurrent.Task;
import services.PlaylistService;
import services.MusiqueService;
import services.OpenRouterLyricsService;
import services.GroqPlaylistGeneratorService;
import services.GlobalMediaPlayerService;
import entities.Musique;
import entities.Playlist;
import entities.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import utils.ImageUrlUtils;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import utils.MyDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import java.io.File;
import java.net.URI;
import java.sql.SQLDataException;
import java.util.Locale;

public class MusicfrontController {
	private static final String XAMPP_IMAGE_DIR = "C:\\xampp\\htdocs\\img";
	private static final double TRACK_CARD_WIDTH = 170;
	private static final double PLAYLIST_CARD_WIDTH = 180;
	private static final double TRACK_COVER_SIZE = 150;
	private static final double PLAYLIST_COVER_WIDTH = 160;
	private static final double PLAYLIST_COVER_HEIGHT = 110;

	private static MusicfrontController activeController;

	@FXML
	private TextField searchField;

	@FXML
	private HBox musicSearchBar;

	@FXML
	private TextField playlistSearchField;

	@FXML
	private HBox playlistSearchBar;

	@FXML
	private ComboBox<String> sortMusicComboBox;

	@FXML
	private TilePane musicGrid;

	@FXML
	private Label emptyListLabel;

	@FXML
	private Label nowPlayingTitleLabel;

	@FXML
	private Label nowPlayingMetaLabel;

	@FXML
	private Label playerStatusLabel;

	@FXML
	private Button playPauseButton;

	@FXML
	private VBox lyricsPanel;

	@FXML
	private Label lyricsTrackLabel;

	@FXML
	private Button generateLyricsButton;

	@FXML
	private Button copyLyricsButton;

	@FXML
	private TextArea lyricsTextArea;

	@FXML
	private Label lyricsStatusLabel;

	@FXML
	private Button musiqueSectionButton;

	@FXML
	private Button playlistsSectionButton;

	@FXML
	private VBox musiqueSection;

	@FXML
	private VBox playlistsSection;

	@FXML
	private TextField playlistNameField;

	@FXML
	private ComboBox<String> sortPlaylistComboBox;

	@FXML
	private TextArea playlistDescriptionArea;

	@FXML
	private TextField playlistImageField;

	@FXML
	private Button createPlaylistButton;

	@FXML
	private Button togglePlaylistFormButton;

	@FXML
	private VBox playlistFormSection;

	@FXML
	private Label playlistFeedbackLabel;

	@FXML
	private VBox playlistDetailSection;

	@FXML
	private Label playlistDetailTitleLabel;

	@FXML
	private Label playlistDetailMetaLabel;

	@FXML
	private Button closePlaylistDetailButton;

	@FXML
	private Label playlistSongsEmptyLabel;

	@FXML
	private TilePane playlistSongsBox;

	@FXML
	private TilePane playlistGrid;

	@FXML
	private VBox aiPlaylistSection;

	@FXML
	private TextField aiPlaylistPromptField;

	@FXML
	private Button generatePlaylistButton;

	@FXML
	private Label aiPlaylistStatusLabel;

	@FXML
	private Button toggleAIPlaylistFormButton;
	private final MusiqueService musiqueService = new MusiqueService();
	private final PlaylistService playlistService = new PlaylistService();
	private final OpenRouterLyricsService lyricsService = new OpenRouterLyricsService();
	private final GroqPlaylistGeneratorService playlistGeneratorService = new GroqPlaylistGeneratorService();
	private final GlobalMediaPlayerService globalMediaPlayer = GlobalMediaPlayerService.getInstance();
	private final ObservableList<Musique> allTracks = FXCollections.observableArrayList();
	private final ObservableList<Musique> visibleTracks = FXCollections.observableArrayList();
	private final ObservableList<Playlist> allPlaylists = FXCollections.observableArrayList();
	private final ObservableList<Playlist> visiblePlaylists = FXCollections.observableArrayList();
	private Integer selectedPlaylistId;
	private Integer editingPlaylistId;
	private java.time.LocalDate editingPlaylistDateCreation;
	private String editingPlaylistImagePath;
	private static final java.util.Set<String> IMAGE_EXTENSIONS = new java.util.HashSet<>(java.util.Arrays.asList("png", "jpg", "jpeg"));
	private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;
	private boolean lyricsLoading;
	private Musique currentLyricsTrack;

	private int currentTrackIndex = -1;
	private final Map<Integer, String> trackArtistNames = new HashMap<>();

	@FXML
	public void initialize() {
		activeController = this;
		searchField.textProperty().addListener((obs, oldValue, newValue) -> filterTracks(newValue));
		if (sortMusicComboBox != null) {
			sortMusicComboBox.setItems(FXCollections.observableArrayList(
					"Titre (A-Z)", "Titre (Z-A)", "Genre (A-Z)", "Récent"
			));
			sortMusicComboBox.getSelectionModel().selectFirst();
			sortMusicComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> filterTracks(searchField.getText()));
		}
		if (sortPlaylistComboBox != null) {
			sortPlaylistComboBox.setItems(FXCollections.observableArrayList(
					"Nom (A-Z)", "Nom (Z-A)", "Récent"
			));
			sortPlaylistComboBox.getSelectionModel().selectFirst();
			sortPlaylistComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> filterPlaylists(playlistSearchField != null ? playlistSearchField.getText() : null));
		}
		if (playlistSearchField != null) {
			playlistSearchField.textProperty().addListener((obs, oldValue, newValue) -> filterPlaylists(newValue));
		}
		updateLyricsPanel(null);
		setLyricsControlsDisabled(true);
		if (createPlaylistButton != null) {
			createPlaylistButton.setText("Créer la playlist");
		}
		if (closePlaylistDetailButton != null) {
			closePlaylistDetailButton.setText("Fermer");
		}
		configureGridSizing();
		setActiveSection(true);
		hidePlaylistForm();
		hidePlaylistDetails();
		setPlaylistFeedback("Créez une playlist puis cliquez sur + Playlist depuis une musique.", false);
		refreshTracks();
		refreshPlaylists();

		// Listen to active track changes to dynamically update card style
		globalMediaPlayer.currentTrackProperty().addListener((obs, oldTrack, newTrack) -> {
			javafx.application.Platform.runLater(() -> {
				updateCurrentTrackIndex(newTrack);
				renderGrid();
				if (playlistDetailSection != null && playlistDetailSection.isVisible()) {
					Playlist refreshed = findPlaylistById(selectedPlaylistId);
					if (refreshed != null) {
						showPlaylistDetails(refreshed);
					}
				}
			});
		});

		// Listen to play/pause state
		globalMediaPlayer.playingProperty().addListener((obs, oldVal, newVal) -> {
			javafx.application.Platform.runLater(() -> {
				renderGrid();
				if (playlistDetailSection != null && playlistDetailSection.isVisible()) {
					Playlist refreshed = findPlaylistById(selectedPlaylistId);
					if (refreshed != null) {
						showPlaylistDetails(refreshed);
					}
				}
			});
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
		}
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
	private void handleSearch() {
		filterTracks(searchField.getText());
	}

	@FXML
	private void handleClearSearch() {
		searchField.clear();
		filterTracks(null);
	}

	@FXML
	private void handleShowMusiqueSection() {
		setActiveSection(true);
	}

	@FXML
	private void handleShowPlaylistsSection() {
		setActiveSection(false);
		if (playlistDetailSection != null && allPlaylists.isEmpty()) {
			hidePlaylistDetails();
		}
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
	private void handleEditSelectedPlaylist() {
		Playlist playlist = findPlaylistById(selectedPlaylistId);
		if (playlist == null) {
			setPlaylistFeedback("Sélectionnez d'abord une playlist à modifier.", false);
			return;
		}

		editingPlaylistId = playlist.getId();
		editingPlaylistDateCreation = playlist.getDateCreation();
		editingPlaylistImagePath = playlist.getImage();

		if (playlistNameField != null) {
			playlistNameField.setText(playlist.getNom() != null ? playlist.getNom() : "");
		}
		if (playlistDescriptionArea != null) {
			playlistDescriptionArea.setText(playlist.getDescription() != null ? playlist.getDescription() : "");
		}
		if (playlistImageField != null) {
			playlistImageField.clear();
		}

		if (createPlaylistButton != null) {
			createPlaylistButton.setText("Enregistrer");
		}
		if (togglePlaylistFormButton != null) {
			togglePlaylistFormButton.setText("Fermer le formulaire");
		}
		if (playlistFormSection != null) {
			playlistFormSection.setVisible(true);
			playlistFormSection.setManaged(true);
		}
		setPlaylistFeedback("Mode modification de playlist activé.", true);
	}

	@FXML
	private void handleDeleteSelectedPlaylist() {
		Playlist playlist = findPlaylistById(selectedPlaylistId);
		if (playlist == null) {
			setPlaylistFeedback("Sélectionnez d'abord une playlist à supprimer.", false);
			return;
		}

		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle("Supprimer la playlist");
		confirm.setHeaderText("Supprimer \"" + safePlaylistName(playlist) + "\" ?");
		confirm.setContentText("Cette action est définitive.");

		java.util.Optional<ButtonType> result = confirm.showAndWait();
		if (result.isEmpty() || result.get() != ButtonType.OK) {
			return;
		}

		try {
			playlistService.delete(playlist);
			editingPlaylistId = null;
			editingPlaylistDateCreation = null;
			editingPlaylistImagePath = null;
			clearPlaylistForm();
			hidePlaylistForm();
			hidePlaylistDetails();
			refreshPlaylists();
			setPlaylistFeedback("Playlist supprimée avec succès.", true);
		} catch (SQLDataException e) {
			setPlaylistFeedback("Erreur lors de la suppression: " + e.getMessage(), false);
		}
	}

	@FXML
	private void handleChoosePlaylistImage() {
		javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
		chooser.setTitle("Choisir une image de playlist");
		chooser.getExtensionFilters().addAll(
				new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
		);

		File file = chooser.showOpenDialog(playlistImageField.getScene().getWindow());
		if (file != null) {
			String validationError = validateImagePath(file.getAbsolutePath());
			if (validationError != null) {
				playlistImageField.clear();
				setPlaylistFeedback(validationError, false);
				return;
			}
			playlistImageField.setText(file.getAbsolutePath());
		}
	}

	@FXML
	private void handleCreatePlaylist() {
		String nom = playlistNameField != null && playlistNameField.getText() != null ? playlistNameField.getText().trim() : "";
		String description = playlistDescriptionArea != null && playlistDescriptionArea.getText() != null ? playlistDescriptionArea.getText().trim() : "";
		String imagePath = playlistImageField != null && playlistImageField.getText() != null ? playlistImageField.getText().trim() : "";

		if (nom.length() < 3 || nom.length() > 255) {
			setPlaylistFeedback("Le nom de la playlist doit contenir entre 3 et 255 caractères.", false);
			return;
		}
		if (!description.isEmpty() && (description.length() < 10 || description.length() > 5000)) {
			setPlaylistFeedback("La description doit contenir entre 10 et 5000 caractères.", false);
			return;
		}

		String imageValidationError = validateImagePath(imagePath);
		if (imageValidationError != null) {
			setPlaylistFeedback(imageValidationError, false);
			return;
		}

		Playlist playlist = new Playlist();
		boolean editMode = editingPlaylistId != null;
		playlist.setId(editingPlaylistId);
		playlist.setNom(nom);
		playlist.setDescription(description.isEmpty() ? null : description);
		playlist.setDateCreation(editMode && editingPlaylistDateCreation != null ? editingPlaylistDateCreation : java.time.LocalDate.now());

		if (editMode) {
			Playlist existing = allPlaylists.stream()
					.filter(p -> p.getId() != null && p.getId().equals(editingPlaylistId))
					.findFirst()
					.orElse(null);
			if (existing != null) {
				playlist.setMusiques(existing.getMusiques());
				playlist.setUserId(existing.getUserId());
			}
		}

		if (!imagePath.isEmpty()) {
			playlist.setImage(imagePath);
		} else if (editMode) {
			playlist.setImage(editingPlaylistImagePath);
		}

		try {
			if (editMode) {
				playlistService.update(playlist);
				setPlaylistFeedback("Playlist modifiée avec succès.", true);
			} else {
				playlistService.add(playlist);
				setPlaylistFeedback("Playlist créée avec succès.", true);
			}
			clearPlaylistForm();
			editingPlaylistId = null;
			editingPlaylistDateCreation = null;
			editingPlaylistImagePath = null;
			hidePlaylistForm();
			refreshPlaylists();
		} catch (SQLDataException e) {
			setPlaylistFeedback("Erreur lors de la création: " + e.getMessage(), false);
		}
	}

	@FXML
	private void handleTogglePlaylistForm() {
		if (playlistFormSection == null) {
			return;
		}

		boolean shouldShow = !playlistFormSection.isVisible();
		playlistFormSection.setVisible(shouldShow);
		playlistFormSection.setManaged(shouldShow);
		if (togglePlaylistFormButton != null) {
			togglePlaylistFormButton.setText(shouldShow ? "Fermer le formulaire" : "Nouvelle playlist");
		}
		if (!shouldShow) {
			editingPlaylistId = null;
			editingPlaylistDateCreation = null;
			editingPlaylistImagePath = null;
			if (createPlaylistButton != null) {
				createPlaylistButton.setText("Créer la playlist");
			}
		}
	}

	@FXML
	private void handleGeneratePlaylistWithAI() {
		String prompt = aiPlaylistPromptField != null ? aiPlaylistPromptField.getText().trim() : "";

		if (prompt.isEmpty()) {
			showAlert("Erreur", "Veuillez entrer une demande pour générer une playlist.", Alert.AlertType.WARNING);
			return;
		}

		if (prompt.length() < 5 || prompt.length() > 500) {
			showAlert("Erreur", "La demande doit contenir entre 5 et 500 caractères.", Alert.AlertType.WARNING);
			return;
		}

		// Get current user ID (you may need to adjust this based on your user management)
		Integer userId = getCurrentUserId();
		if (userId == null) {
			showAlert("Erreur", "Vous devez être connecté pour générer une playlist.", Alert.AlertType.WARNING);
			return;
		}

		// Show loading status
		setAIPlaylistStatus("Génération en cours... Cela peut prendre quelques secondes.", false);
		generatePlaylistButton.setDisable(true);
		aiPlaylistPromptField.setDisable(true);

		// Run generation in background thread
		Task<Playlist> task = new Task<Playlist>() {
			@Override
			protected Playlist call() throws Exception {
				return playlistGeneratorService.generatePlaylistFromPrompt(prompt, userId);
			}
		};

		task.setOnSucceeded(event -> {
			Playlist generatedPlaylist = task.getValue();
			setAIPlaylistStatus("✓ Playlist générée avec succès: " + generatedPlaylist.getNom(), true);
			aiPlaylistPromptField.clear();
			generatePlaylistButton.setDisable(false);
			aiPlaylistPromptField.setDisable(false);
			refreshPlaylists();
			// Show success message
			showAlert("Succès", "Votre playlist a été créée avec succès!\n\nNom: " + generatedPlaylist.getNom() +
					"\nMusiques ajoutées: " + generatedPlaylist.getMusiques().size(), Alert.AlertType.INFORMATION);
		});

		task.setOnFailed(event -> {
			Throwable exception = task.getException();
			String errorMessage = exception != null ? exception.getMessage() : "Erreur inconnue lors de la génération.";
			setAIPlaylistStatus("✗ Erreur: " + errorMessage, false);
			generatePlaylistButton.setDisable(false);
			aiPlaylistPromptField.setDisable(false);
			showAlert("Erreur", "Impossible de générer la playlist:\n" + errorMessage, Alert.AlertType.ERROR);
		});

		new Thread(task).start();
	}

	private void setAIPlaylistStatus(String message, boolean success) {
		if (aiPlaylistStatusLabel != null) {
			aiPlaylistStatusLabel.setText(message);
			aiPlaylistStatusLabel.setStyle(success ? "-fx-text-fill: #10b981;" : "-fx-text-fill: #ef4444;");
		}
	}

	private Integer getCurrentUserId() {
		// Get the authenticated user from MainFX
		User currentUser = controllers.MainFX.getAuthenticatedUser();
		if (currentUser != null && currentUser.getId() != null) {
			return currentUser.getId();
		}
		return null;
	}

	private void showAlert(String title, String message, Alert.AlertType type) {
		Alert alert = new Alert(type);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}

	@FXML
	private void handlePlayPause() {
		if (globalMediaPlayer.getCurrentTrack() == null) {
			int selectedIndex = currentTrackIndex;
			if (selectedIndex < 0 && !visibleTracks.isEmpty()) {
				selectedIndex = 0;
			}
			if (selectedIndex >= 0) {
				playTrackAtIndex(selectedIndex);
			}
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
			hidePlaylistDetails();
			setPlaylistFeedback("Impossible de charger les playlists: " + e.getMessage(), false);
		}
	}

	private void refreshTracks() {
		try {
			List<Musique> musiques = musiqueService.getAll();
			allTracks.setAll(musiques);
			loadTrackArtistNames(musiques);
			filterTracks(searchField != null ? searchField.getText() : null);
		} catch (SQLDataException e) {
			allTracks.clear();
			visibleTracks.clear();
			renderGrid();
			emptyListLabel.setText("Impossible de charger les musiques: " + e.getMessage());
			emptyListLabel.setVisible(true);
			emptyListLabel.setManaged(true);
			globalMediaPlayer.stop();
			updateLyricsPanel(currentLyricsTrack);
		}
	}

	private void filterTracks(String query) {
		String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

		visibleTracks.setAll(allTracks.stream()
				.filter(track -> normalizedQuery.isEmpty() || matchesQuery(track, normalizedQuery))
				.toList());

		// Apply sorting
		if (sortMusicComboBox != null) {
			String sortOption = sortMusicComboBox.getValue();
			if (sortOption != null) {
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
						java.time.LocalDate dateA = a.getDateCreation() != null ? a.getDateCreation() : java.time.LocalDate.MIN;
						java.time.LocalDate dateB = b.getDateCreation() != null ? b.getDateCreation() : java.time.LocalDate.MIN;
						return dateB.compareTo(dateA);
					});
				}
			}
		}

		if (visibleTracks.isEmpty()) {
			currentTrackIndex = -1;
			globalMediaPlayer.stop();
			setNowPlayingTitle("Selectionnez une musique");
			setNowPlayingMeta("Genre: -");
			setPlayerStatusText("Pret");
			setPlayPauseText("Play");
			updateLyricsPanel(currentLyricsTrack);
		} else if (currentTrackIndex >= visibleTracks.size()) {
			currentTrackIndex = -1;
		}

		renderGrid();
		emptyListLabel.setVisible(visibleTracks.isEmpty());
		emptyListLabel.setManaged(visibleTracks.isEmpty());

		if (!visibleTracks.isEmpty() && currentTrackIndex < 0) {
			updateNowPlayingLabels(visibleTracks.get(0));
		}
	}

	private boolean matchesQuery(Musique musique, String query) {
		String titre = musique.getTitre() != null ? musique.getTitre().toLowerCase(Locale.ROOT) : "";
		String description = musique.getDescription() != null ? musique.getDescription().toLowerCase(Locale.ROOT) : "";
		String genre = musique.getGenre() != null ? musique.getGenre().toLowerCase(Locale.ROOT) : "";
		return titre.contains(query) || description.contains(query) || genre.contains(query);
	}

	private void filterPlaylists(String query) {
		String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

		visiblePlaylists.setAll(allPlaylists.stream()
				.filter(playlist -> normalizedQuery.isEmpty() || matchesPlaylistQuery(playlist, normalizedQuery))
				.toList());

		if (sortPlaylistComboBox != null) {
			String sortOption = sortPlaylistComboBox.getValue();
			if (sortOption != null) {
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
						java.time.LocalDate dateA = a.getDateCreation() != null ? a.getDateCreation() : java.time.LocalDate.MIN;
						java.time.LocalDate dateB = b.getDateCreation() != null ? b.getDateCreation() : java.time.LocalDate.MIN;
						return dateB.compareTo(dateA);
					});
				}
			}
		}
		renderPlaylistGrid();
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
			case "Récent" -> visibleTracks.sort((a, b) -> {
				java.time.LocalDate dateA = a.getDateCreation() != null ? a.getDateCreation() : java.time.LocalDate.MIN;
				java.time.LocalDate dateB = b.getDateCreation() != null ? b.getDateCreation() : java.time.LocalDate.MIN;
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
				java.time.LocalDate dateA = a.getDateCreation() != null ? a.getDateCreation() : java.time.LocalDate.MIN;
				java.time.LocalDate dateB = b.getDateCreation() != null ? b.getDateCreation() : java.time.LocalDate.MIN;
				return dateB.compareTo(dateA);
			});
		}
	}

	private void playTrackAtIndex(int index) {
		if (index < 0 || index >= visibleTracks.size()) {
			return;
		}

		Musique selectedTrack = visibleTracks.get(index);
		currentLyricsTrack = selectedTrack;
		currentTrackIndex = index;
		renderGrid();
		updateNowPlayingLabels(selectedTrack);
		updateLyricsPanel(selectedTrack);
		// When playing from the general list, it is not a playlist context.
		globalMediaPlayer.playTrack(selectedTrack, visibleTracks, index, "Artiste inconnu", false);
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
			return "Format image non supporté. Utilisez JPEG ou PNG.";
		}

		String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
		if (!IMAGE_EXTENSIONS.contains(extension)) {
			return "Format image non supporté. Utilisez JPEG ou PNG.";
		}

		if (imageFile.length() > MAX_IMAGE_SIZE_BYTES) {
			return "Image trop volumineuse. Taille max: 5 MB.";
		}

		return null;
	}

	private void updateNowPlayingLabels(Musique musique) {
		String titre = musique.getTitre() != null ? musique.getTitre() : "Sans titre";
		String genre = musique.getGenre() != null ? musique.getGenre() : "-";
		setNowPlayingTitle(titre);
		setNowPlayingMeta("Genre: " + genre);
	}

	private void renderGrid() {
		musicGrid.getChildren().clear();
		for (int i = 0; i < visibleTracks.size(); i++) {
			Musique musique = visibleTracks.get(i);
			musicGrid.getChildren().add(createTrackCard(musique, i));
		}
	}

	private void renderPlaylistGrid() {
		playlistGrid.getChildren().clear();
		for (Playlist playlist : visiblePlaylists) {
			playlistGrid.getChildren().add(createPlaylistCard(playlist));
		}
	}

	private void clearPlaylistForm() {
		if (playlistNameField != null) {
			playlistNameField.clear();
		}
		if (playlistDescriptionArea != null) {
			playlistDescriptionArea.clear();
		}
		if (playlistImageField != null) {
			playlistImageField.clear();
		}
	}

	private void hidePlaylistForm() {
		if (playlistFormSection != null) {
			playlistFormSection.setVisible(false);
			playlistFormSection.setManaged(false);
		}
		if (togglePlaylistFormButton != null) {
			togglePlaylistFormButton.setText("Nouvelle playlist");
		}
		if (createPlaylistButton != null) {
			createPlaylistButton.setText(editingPlaylistId != null ? "Enregistrer" : "Créer la playlist");
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
					? "-fx-background-color: #3f44d4; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 8 16; -fx-font-weight: bold;"
					: "-fx-background-color: #e2e8f0; -fx-text-fill: #475569; -fx-background-radius: 999; -fx-padding: 8 16; -fx-font-weight: bold;");
		}
		if (playlistsSectionButton != null) {
			playlistsSectionButton.setStyle(!showMusique
					? "-fx-background-color: #3f44d4; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 8 16; -fx-font-weight: bold;"
					: "-fx-background-color: #e2e8f0; -fx-text-fill: #475569; -fx-background-radius: 999; -fx-padding: 8 16; -fx-font-weight: bold;");
		}
	}

	private void setPlaylistFeedback(String message, boolean success) {
		if (playlistFeedbackLabel == null) {
			return;
		}
		playlistFeedbackLabel.setText(message);
		playlistFeedbackLabel.setStyle(success ? "-fx-text-fill: #198754;" : "-fx-text-fill: #b00020;");
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
		if (playlistDetailMetaLabel != null) {
			playlistDetailMetaLabel.setText((playlist.getDescription() != null && !playlist.getDescription().isBlank() ? playlist.getDescription() + " · " : "")
					+ (playlist.getMusiques() != null ? playlist.getMusiques().size() : 0) + " musique(s)");
		}

		if (playlistSongsBox != null) {
			playlistSongsBox.getChildren().clear();
			java.util.List<Musique> musiques = fetchPlaylistTracks(playlist);
			boolean empty = musiques.isEmpty();
			if (playlistSongsEmptyLabel != null) {
				playlistSongsEmptyLabel.setVisible(empty);
				playlistSongsEmptyLabel.setManaged(empty);
			}
			for (Musique track : musiques) {
				playlistSongsBox.getChildren().add(createPlaylistTrackCard(track));
			}
		}
	}

	private VBox createPlaylistTrackCard(Musique track) {
		VBox card = new VBox(6);
		card.setMinWidth(TRACK_CARD_WIDTH);
		card.setPrefWidth(TRACK_CARD_WIDTH);
		card.setMaxWidth(TRACK_CARD_WIDTH);
		card.getStyleClass().add("music-card");

		Node coverNode = buildCoverNode(track.getImage());

		String titre = track.getTitre() != null ? track.getTitre() : "Sans titre";
		Label titleLabel = new Label(titre);
		titleLabel.setWrapText(true);
		titleLabel.getStyleClass().add("music-card-title");

		String genre = track.getGenre() != null ? track.getGenre() : "-";
		Label metaLabel = new Label("Genre: " + genre);
		metaLabel.getStyleClass().add("music-card-meta");

		Button playBtn = new Button("Play");
		playBtn.getStyleClass().add("music-card-button");
		playBtn.setOnAction(event -> {
			event.consume();
			playTrackFromPlaylist(track);
		});

		Button removeBtn = new Button("Retirer");
		removeBtn.getStyleClass().add("music-card-button");
		removeBtn.setStyle("-fx-text-fill: #ef4444; -fx-border-color: rgba(239, 68, 68, 0.3);");
		removeBtn.setOnAction(event -> {
			event.consume();
			if (selectedPlaylistId == null || track == null || track.getId() == null) {
				setPlaylistFeedback("Impossible de retirer la musique (identifiants manquants).", false);
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
				Playlist refreshed = findPlaylistById(selectedPlaylistId);
				if (refreshed != null) {
					showPlaylistDetails(refreshed);
				} else {
					hidePlaylistDetails();
				}
				refreshPlaylists();
				setPlaylistFeedback("Musique retirée de la playlist.", true);
			} catch (SQLDataException ex) {
				setPlaylistFeedback("Erreur lors du retrait: " + ex.getMessage(), false);
			}
		});

		HBox actionsRow = new HBox(8, playBtn, removeBtn);
		card.getChildren().addAll(coverNode, titleLabel, metaLabel, actionsRow);
		return card;
	}

	private java.util.List<Musique> fetchPlaylistTracks(Playlist playlist) {
		try {
			if (playlist != null && playlist.getId() != null) {
				java.util.List<Musique> musiques = playlistService.getMusiquesForPlaylist(playlist.getId());
				playlist.setMusiques(musiques);
				return musiques;
			}
		} catch (SQLDataException e) {
			setPlaylistFeedback("Impossible de charger les musiques de la playlist: " + e.getMessage(), false);
		}
		return java.util.List.of();
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

	private void hidePlaylistDetails() {
		selectedPlaylistId = null;
		editingPlaylistId = null;
		editingPlaylistDateCreation = null;
		editingPlaylistImagePath = null;
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
			playlistDetailTitleLabel.setText("Aucune playlist sélectionnée");
		}
		if (playlistDetailMetaLabel != null) {
			playlistDetailMetaLabel.setText("Cliquez sur une playlist pour voir ses musiques.");
		}
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

	private void setLyricsStatus(String text) {
		if (lyricsStatusLabel != null) {
			lyricsStatusLabel.setText(text);
		}
	}

	private void setLyricsText(String text) {
		if (lyricsTextArea != null) {
			lyricsTextArea.setText(text);
		}
	}

	private void setLyricsControlsDisabled(boolean disabled) {
		if (generateLyricsButton != null) {
			generateLyricsButton.setDisable(disabled);
		}
		if (copyLyricsButton != null) {
			copyLyricsButton.setDisable(disabled);
		}
	}

	private void updateLyricsPanel(Musique track) {
		String title = track != null && track.getTitre() != null && !track.getTitre().isBlank()
				? track.getTitre()
				: "Aucune musique sélectionnée";
		if (lyricsTrackLabel != null) {
			lyricsTrackLabel.setText(title);
		}

		if (track == null) {
			setLyricsText("Les paroles originales générées par OpenRouter apparaîtront ici.");
			setLyricsStatus("Sélectionnez une musique puis cliquez sur Générer les paroles.");
			setLyricsControlsDisabled(true);
			return;
		}

		if (!lyricsLoading) {
			setLyricsText("Cliquez sur « Générer les paroles » pour créer des paroles originales inspirées de ce morceau.");
			setLyricsStatus("Prêt à générer des paroles originales.");
		}
		setLyricsControlsDisabled(false);
	}

	private Musique getCurrentTrackForLyrics() {
		return currentLyricsTrack;
	}

	@FXML
	private void handleGenerateLyrics() {
		Musique track = getCurrentTrackForLyrics();
		if (track == null) {
			setLyricsStatus("Lancez une musique avant de générer les paroles.");
			return;
		}
		requestLyricsForTrack(track);
	}

	@FXML
	private void handleCopyLyrics() {
		if (lyricsTextArea == null || lyricsTextArea.getText() == null || lyricsTextArea.getText().isBlank()) {
			setLyricsStatus("Aucune parole à copier.");
			return;
		}
		ClipboardContent content = new ClipboardContent();
		content.putString(lyricsTextArea.getText());
		Clipboard.getSystemClipboard().setContent(content);
		setLyricsStatus("Paroles copiées dans le presse-papiers.");
	}

	private void requestLyricsForTrack(Musique track) {
		if (track == null) {
			setLyricsStatus("Aucune musique sélectionnée.");
			return;
		}
		if (lyricsLoading) {
			setLyricsStatus("Génération en cours, veuillez patienter...");
			return;
		}

		lyricsLoading = true;
		currentLyricsTrack = track;
		updateLyricsPanel(track);
		setLyricsStatus("Génération des paroles en cours...");
		setLyricsControlsDisabled(true);

		Task<String> task = new Task<>() {
			@Override
			protected String call() {
				return lyricsService.generateLyrics(track);
			}
		};

		task.setOnSucceeded(event -> {
			lyricsLoading = false;
			setLyricsText(task.getValue());
			setLyricsStatus("Paroles générées avec OpenRouter pour " + safeTrackTitle(track) + ".");
			setLyricsControlsDisabled(false);
		});
		task.setOnFailed(event -> {
			lyricsLoading = false;
			Throwable error = task.getException();
			setLyricsText("Impossible de générer les paroles pour le moment.");
			setLyricsStatus(error != null && error.getMessage() != null ? error.getMessage() : "Erreur lors de la génération des paroles.");
			setLyricsControlsDisabled(false);
		});

		Thread worker = new Thread(task, "openrouter-lyrics-generator");
		worker.setDaemon(true);
		worker.start();
	}

	private String safeTrackTitle(Musique track) {
		return track != null && track.getTitre() != null && !track.getTitre().isBlank() ? track.getTitre() : "ce morceau";
	}

	private record PlaylistChoice(Integer id, String name) {
		@Override
		public String toString() {
			return name;
		}
	}

	private VBox createPlaylistCard(Playlist playlist) {
		VBox card = new VBox(8);
		card.setMinWidth(PLAYLIST_CARD_WIDTH);
		card.setPrefWidth(PLAYLIST_CARD_WIDTH);
		card.setMaxWidth(PLAYLIST_CARD_WIDTH);
		card.getStyleClass().add("music-card");

		// Hover Lift Transition
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

		Node coverNode = buildPlaylistCoverNode(playlist.getImage());
		StackPane coverContainer = (StackPane) coverNode;

		// Add hover overlay to playlist cover
		StackPane hoverOverlay = new StackPane();
		hoverOverlay.getStyleClass().add("playlist-cover-hover-overlay");
		hoverOverlay.setOpacity(0.0);

		Button hoverOpenBtn = new Button("📂");
		hoverOpenBtn.getStyleClass().add("playlist-cover-hover-open-button");
		hoverOpenBtn.setOnAction(event -> {
			event.consume();
			showPlaylistDetails(playlist);
		});
		hoverOverlay.getChildren().add(hoverOpenBtn);
		coverContainer.getChildren().add(hoverOverlay);

		coverContainer.setOnMouseEntered(e -> {
			FadeTransition fade = new FadeTransition(Duration.millis(150), hoverOverlay);
			fade.setToValue(0.8);
			fade.play();
		});
		coverContainer.setOnMouseExited(e -> {
			FadeTransition fade = new FadeTransition(Duration.millis(150), hoverOverlay);
			fade.setToValue(0.0);
			fade.play();
		});

		Label nameLabel = new Label(safePlaylistName(playlist));
		nameLabel.setWrapText(true);
		nameLabel.getStyleClass().add("music-card-title");
		nameLabel.setMinHeight(38);
		nameLabel.setPrefHeight(38);
		nameLabel.setMaxHeight(38);

		Label descriptionLabel = new Label(playlist.getDescription() != null && !playlist.getDescription().isBlank()
				? playlist.getDescription()
				: "Aucune description");
		descriptionLabel.setWrapText(true);
		descriptionLabel.getStyleClass().add("music-card-meta");
		descriptionLabel.setMinHeight(36);
		descriptionLabel.setPrefHeight(36);
		descriptionLabel.setMaxHeight(36);

		Label countLabel = new Label((playlist.getMusiques() != null ? playlist.getMusiques().size() : 0) + " musique(s)");
		countLabel.getStyleClass().add("music-card-meta");

		Button openButton = new Button("Ouvrir");
		openButton.getStyleClass().add("music-card-button");
		openButton.setOnAction(event -> {
			event.consume();
			showPlaylistDetails(playlist);
		});

		HBox actionsRow = new HBox(openButton);
		actionsRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

		card.getChildren().addAll(coverNode, nameLabel, descriptionLabel, countLabel, actionsRow);
		card.setOnMouseClicked(event -> showPlaylistDetails(playlist));
		return card;
	}


	private void playTrackFromPlaylist(Musique track) {
		if (track == null) {
			return;
		}

		java.util.List<Musique> queueTracks = java.util.List.of(track);
		int index = 0;

		if (selectedPlaylistId != null) {
			Playlist playlist = findPlaylistById(selectedPlaylistId);
			if (playlist != null && playlist.getMusiques() != null && !playlist.getMusiques().isEmpty()) {
				queueTracks = playlist.getMusiques();
				index = -1;
				for (int i = 0; i < queueTracks.size(); i++) {
					if (queueTracks.get(i).getId() != null && queueTracks.get(i).getId().equals(track.getId())) {
						index = i;
						break;
					}
				}
				if (index < 0) {
					index = 0;
				}
			}
		}

		currentLyricsTrack = track;
		currentTrackIndex = -1;
		renderGrid();
		updateNowPlayingLabels(track);
		updateLyricsPanel(track);
		
		// This is a playlist context!
		globalMediaPlayer.playTrack(track, queueTracks, index, "Artiste inconnu", true);
	}

	private Node buildPlaylistCoverNode(String imageSource) {
		StackPane placeholder = createCoverContainer(PLAYLIST_COVER_WIDTH, PLAYLIST_COVER_HEIGHT);
		placeholder.getStyleClass().add("playlist-cover-placeholder");

		if (imageSource == null || imageSource.isBlank()) {
			Label noImageLabel = new Label("Playlist");
			noImageLabel.getStyleClass().add("playlist-cover-placeholder-text");
			placeholder.getChildren().add(noImageLabel);
			return placeholder;
		}

		try {
			Image image = loadImageSafely(imageSource);
			if (image == null) {
				Label noImageLabel = new Label("Playlist");
				noImageLabel.getStyleClass().add("playlist-cover-placeholder-text");
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
			coverWrap.getStyleClass().add("playlist-cover-placeholder");
			coverWrap.getChildren().add(imageView);
			return coverWrap;
		} catch (Exception ex) {
			Label noImageLabel = new Label("Playlist");
			noImageLabel.getStyleClass().add("playlist-cover-placeholder-text");
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

	private VBox createTrackCard(Musique musique, int index) {
		VBox card = new VBox(8);
		card.setMinWidth(TRACK_CARD_WIDTH);
		card.setPrefWidth(TRACK_CARD_WIDTH);
		card.setMaxWidth(TRACK_CARD_WIDTH);
		card.getStyleClass().add("music-card");
		
		boolean isActive = (index == currentTrackIndex);
		boolean isPlaying = isActive && globalMediaPlayer.isPlaying();
		
		if (isActive) {
			card.getStyleClass().add("music-card-active");
		}

		// Lift Transition on hover
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
		titleLabel.getStyleClass().add("music-card-title");
		titleLabel.setMinHeight(38);
		titleLabel.setPrefHeight(38);
		titleLabel.setMaxHeight(38);

		String artist = resolveTrackArtistName(musique);
		Label artistLabel = new Label(artist);
		artistLabel.getStyleClass().add("music-track-artist");
		artistLabel.setMinHeight(16);
		artistLabel.setPrefHeight(16);
		artistLabel.setMaxHeight(16);

		String genre = musique.getGenre() != null ? musique.getGenre() : "-";
		Label genreBadge = new Label(genre);
		genreBadge.getStyleClass().add("music-genre-badge");

		Button addToPlaylistButton = new Button("+ Playlist");
		addToPlaylistButton.getStyleClass().add("music-card-button");
		addToPlaylistButton.setOnAction(event -> {
			event.consume();
			if (allPlaylists.isEmpty()) {
				setPlaylistFeedback("Créez d'abord une playlist.", false);
				return;
			}
			if (musique == null || musique.getId() == null) {
				setPlaylistFeedback("Musique invalide.", false);
				return;
			}

			java.util.List<PlaylistChoice> choices = allPlaylists.stream()
					.filter(playlist -> playlist.getId() != null)
					.map(playlist -> new PlaylistChoice(playlist.getId(), safePlaylistName(playlist)))
					.toList();
			if (choices.isEmpty()) {
				setPlaylistFeedback("Aucune playlist valide disponible.", false);
				return;
			}

			ChoiceDialog<PlaylistChoice> dialog = new ChoiceDialog<>(choices.get(0), choices);
			dialog.setTitle("Ajouter à une playlist");
			dialog.setHeaderText("Choisissez une playlist");
			dialog.setContentText("Playlist:");

			java.util.Optional<PlaylistChoice> selected = dialog.showAndWait();
			if (selected.isEmpty()) {
				return;
			}

			try {
				playlistService.addMusiqueToPlaylist(selected.get().id(), musique.getId());
				refreshPlaylists();
				setPlaylistFeedback("Musique ajoutée à \"" + selected.get().name() + "\" avec succès.", true);
			} catch (SQLDataException ex) {
				setPlaylistFeedback("Erreur lors de l'ajout à la playlist: " + ex.getMessage(), false);
			}
		});

		HBox actionsRow = new HBox(addToPlaylistButton);
		actionsRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

		card.getChildren().addAll(coverNode, titleLabel, artistLabel, genreBadge, actionsRow);
		card.setOnMouseClicked(event -> playTrackAtIndex(index));
		return card;
	}


	public static MusicfrontController getActiveController() {
		return activeController;
	}

	public void togglePlayPauseFromMini() {
		handlePlayPause();
	}

	public void playPreviousFromMini() {
		handlePreviousTrack();
	}

	public void playNextFromMini() {
		handleNextTrack();
	}

	public boolean isCurrentlyPlaying() {
		return globalMediaPlayer.isPlaying();
	}

	private Node buildCoverNode(String imageSource) {
		return buildCoverNode(imageSource, false, false, -1);
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
				playTrackAtIndex(index);
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
}

