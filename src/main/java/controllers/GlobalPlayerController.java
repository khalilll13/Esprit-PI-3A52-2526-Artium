package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.concurrent.Task;
import services.GlobalMediaPlayerService;
import services.OpenRouterLyricsService;
import entities.Musique;
import utils.PlayerControlIcons;

public class GlobalPlayerController {

  private static final String TRACK_UNFILLED = "#535353";
  private static final String TRACK_FILLED_DEFAULT = "#b3b3b3";
  private static final String TRACK_FILLED_ACTIVE = "#1db954";
  private static final String VOLUME_FILLED = "#1db954";

  @FXML
  private ImageView coverImageView;

  @FXML
  private Label titleLabel;

  @FXML
  private Label artistLabel;

  @FXML
  private Label metaLabel;

  @FXML
  private Label statusLabel;

  @FXML
  private Label timeLabel;

  @FXML
  private Label durationLabel;

  @FXML
  private Button playPauseButton;

  @FXML
  private Slider progressSlider;

  @FXML
  private Slider volumeSlider;

  @FXML
  private Button muteButton;

  @FXML
  private Button modeToggleButton;

  @FXML
  private VBox lyricsPanel;

  @FXML
  private Label lyricsStatusLabel;

  @FXML
  private Button generateLyricsButton;

  @FXML
  private Button copyLyricsButton;

  @FXML
  private TextArea lyricsTextArea;

  @FXML
  private Button lyricsToggleButton;

  private final GlobalMediaPlayerService mediaPlayerService = GlobalMediaPlayerService.getInstance();
  private final OpenRouterLyricsService lyricsService = new OpenRouterLyricsService();
  private boolean lyricsLoading = false;
  private boolean userSeeking = false;

  @FXML
  public void initialize() {
    titleLabel.textProperty().bind(mediaPlayerService.trackTitleProperty());
    artistLabel.textProperty().bind(mediaPlayerService.trackArtistProperty());
    metaLabel.textProperty().bind(mediaPlayerService.trackMetaProperty());
    statusLabel.textProperty().bind(mediaPlayerService.statusTextProperty());

    bindTimeLabels();
    setupSquareCover();
    setupPlayPauseButton();
    coverImageView.imageProperty().bind(mediaPlayerService.coverImageProperty());

    mediaPlayerService.playingProperty().addListener((obs, oldValue, newValue) -> updatePlayPauseButton(newValue));

    mediaPlayerService.playbackModeProperty().addListener((obs, oldMode, newMode) -> updateModeButton(newMode));
    updateModeButton(mediaPlayerService.playbackModeProperty().get());

    mediaPlayerService.progressFractionProperty().addListener((obs, oldValue, newValue) -> {
      if (!userSeeking) {
        progressSlider.setValue(newValue.doubleValue());
        refreshSliderAppearance(progressSlider, false);
      }
    });
    progressSlider.setValue(mediaPlayerService.progressFractionProperty().get());

    progressSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
      if (userSeeking) {
        refreshSliderAppearance(progressSlider, false);
      }
    });

    progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
      userSeeking = isChanging || progressSlider.isPressed();
      refreshSliderAppearance(progressSlider, false);
      if (!isChanging && !progressSlider.isPressed()) {
        mediaPlayerService.seekToFraction(progressSlider.getValue());
      }
    });

    progressSlider.pressedProperty().addListener((obs, wasPressed, isPressed) -> {
      userSeeking = isPressed || progressSlider.isValueChanging();
      setSliderActiveClass(progressSlider, isPressed || progressSlider.isHover());
      refreshSliderAppearance(progressSlider, false);
    });

    if (volumeSlider != null) {
      volumeSlider.setValue(mediaPlayerService.volumeProperty().get());
      mediaPlayerService.volumeProperty().bind(volumeSlider.valueProperty());
      configureSpotifySlider(volumeSlider, true);
    }

    configureSpotifySlider(progressSlider, false);

    if (muteButton != null) {
      muteButton.setText(mediaPlayerService.mutedProperty().get() ? "🔇" : "🔊");
      mediaPlayerService.mutedProperty().addListener((obs, oldV, newV) -> muteButton.setText(newV ? "🔇" : "🔊"));
    }

    mediaPlayerService.currentTrackProperty().addListener((obs, oldTrack, newTrack) -> {
      if (lyricsPanel != null && lyricsPanel.isVisible()) {
        updateLyricsPanel(newTrack);
      }
    });
  }

  private void setupSquareCover() {
    coverImageView.setFitWidth(56);
    coverImageView.setFitHeight(56);
    coverImageView.setPreserveRatio(true);
    coverImageView.setClip(new Rectangle(56, 56));
  }

  private void setupPlayPauseButton() {
    playPauseButton.setText("");
    playPauseButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    updatePlayPauseButton(mediaPlayerService.isPlaying());
  }

  private void bindTimeLabels() {
    Runnable updateTimes = () -> {
      String combined = mediaPlayerService.timeTextProperty().get();
      if (combined == null) {
        return;
      }
      String[] parts = combined.split("/");
      if (parts.length == 2) {
        timeLabel.setText(parts[0].trim());
        if (durationLabel != null) {
          durationLabel.setText(parts[1].trim());
        }
      }
    };

    mediaPlayerService.timeTextProperty().addListener((obs, oldVal, newVal) -> updateTimes.run());
    updateTimes.run();
  }

  private void configureSpotifySlider(Slider slider, boolean volume) {
    Runnable refresh = () -> refreshSliderAppearance(slider, volume);

    slider.valueProperty().addListener((obs, oldVal, newVal) -> refresh.run());
    slider.hoverProperty().addListener((obs, oldVal, newVal) -> {
      setSliderActiveClass(slider, newVal || slider.isPressed() || slider.isValueChanging());
      refresh.run();
    });
    slider.pressedProperty().addListener((obs, oldVal, newVal) -> {
      setSliderActiveClass(slider, newVal || slider.isHover() || slider.isValueChanging());
      refresh.run();
    });
    slider.valueChangingProperty().addListener((obs, oldVal, newVal) -> {
      setSliderActiveClass(slider, newVal || slider.isHover() || slider.isPressed());
      refresh.run();
    });

    slider.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(refresh));
    slider.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene != null) {
        Platform.runLater(refresh);
      }
    });
  }

  private void setSliderActiveClass(Slider slider, boolean active) {
    if (active) {
      if (!slider.getStyleClass().contains("spotify-slider-active")) {
        slider.getStyleClass().add("spotify-slider-active");
      }
    } else {
      slider.getStyleClass().remove("spotify-slider-active");
    }
  }

  private void refreshSliderAppearance(Slider slider, boolean volume) {
    double fraction = slider.getMax() > slider.getMin()
        ? (slider.getValue() - slider.getMin()) / (slider.getMax() - slider.getMin())
        : 0.0;
    fraction = Math.max(0.0, Math.min(1.0, fraction));
    double percent = fraction * 100.0;

    boolean active = slider.isHover() || slider.isPressed() || slider.isValueChanging()
        || slider.getStyleClass().contains("spotify-slider-active");
    String filled = volume ? VOLUME_FILLED : (active ? TRACK_FILLED_ACTIVE : TRACK_FILLED_DEFAULT);

    String trackStyle = String.format(
        "-fx-background-color: linear-gradient(to right, %s %.4f%%, %s %.4f%%);",
        filled, percent, TRACK_UNFILLED, percent);

    applyNodeStyle(slider, ".track", trackStyle);
  }

  private void applyNodeStyle(Slider slider, String selector, String style) {
    Platform.runLater(() -> {
      Node track = slider.lookup(selector);
      if (track != null) {
        track.setStyle(style);
      }
    });
  }

  private void updatePlayPauseButton(boolean playing) {
    if (playPauseButton == null) {
      return;
    }
    playPauseButton.setGraphic(
        playing ? PlayerControlIcons.createPauseGraphic() : PlayerControlIcons.createPlayGraphic());
  }

  private void updateModeButton(GlobalMediaPlayerService.PlaybackMode mode) {
    if (modeToggleButton == null) {
      return;
    }
    modeToggleButton.getStyleClass().remove("global-player-button-active");
    switch (mode) {
      case NORMAL:
        modeToggleButton.setText("🔀");
        break;
      case SHUFFLE:
        modeToggleButton.setText("🔀");
        modeToggleButton.getStyleClass().add("global-player-button-active");
        break;
      case SMART_SHUFFLE:
        modeToggleButton.setText("✨");
        modeToggleButton.getStyleClass().add("global-player-button-active");
        break;
      default:
        break;
    }
  }

  @FXML
  private void handleToggleMode() {
    mediaPlayerService.togglePlaybackMode();
  }

  @FXML
  private void handlePlayPause() {
    mediaPlayerService.togglePlayPause();
  }

  @FXML
  private void handlePrevious() {
    mediaPlayerService.playPrevious();
  }

  @FXML
  private void handleNext() {
    mediaPlayerService.playNext();
  }

  @FXML
  private void handleSeekStart() {
    userSeeking = true;
    setSliderActiveClass(progressSlider, true);
    refreshSliderAppearance(progressSlider, false);
  }

  @FXML
  private void handleSeekEnd() {
    userSeeking = false;
    setSliderActiveClass(progressSlider, progressSlider.isHover());
    mediaPlayerService.seekToFraction(progressSlider.getValue());
    refreshSliderAppearance(progressSlider, false);
  }

  @FXML
  private void handleMute() {
    mediaPlayerService.toggleMute();
  }

  @FXML
  private void handleToggleLyrics() {
    if (lyricsPanel == null) {
      return;
    }
    boolean isVisible = !lyricsPanel.isVisible();
    lyricsPanel.setVisible(isVisible);
    lyricsPanel.setManaged(isVisible);

    if (lyricsToggleButton != null) {
      if (isVisible) {
        lyricsToggleButton.getStyleClass().add("global-player-lyrics-active");
      } else {
        lyricsToggleButton.getStyleClass().remove("global-player-lyrics-active");
      }
    }

    if (isVisible) {
      updateLyricsPanel(mediaPlayerService.getCurrentTrack());
    }
  }

  @FXML
  private void handleGenerateLyrics() {
    Musique track = mediaPlayerService.getCurrentTrack();
    if (track == null) {
      setLyricsStatus("Aucune musique en cours de lecture.");
      return;
    }
    if (lyricsLoading) {
      setLyricsStatus("Génération en cours, veuillez patienter...");
      return;
    }

    lyricsLoading = true;
    setLyricsStatus("Génération des paroles en cours...");
    generateLyricsButton.setDisable(true);
    copyLyricsButton.setDisable(true);
    lyricsTextArea.setText("L'intelligence artificielle écrit les paroles...");

    Task<String> task = new Task<>() {
      @Override
      protected String call() {
        return lyricsService.generateLyrics(track);
      }
    };

    task.setOnSucceeded(event -> {
      lyricsLoading = false;
      lyricsTextArea.setText(task.getValue());
      setLyricsStatus("Paroles générées avec succès.");
      generateLyricsButton.setDisable(false);
      copyLyricsButton.setDisable(false);
    });

    task.setOnFailed(event -> {
      lyricsLoading = false;
      Throwable error = task.getException();
      lyricsTextArea.setText("Impossible de générer les paroles.");
      setLyricsStatus(error != null && error.getMessage() != null ? error.getMessage() : "Erreur.");
      generateLyricsButton.setDisable(false);
      copyLyricsButton.setDisable(false);
    });

    Thread worker = new Thread(task, "openrouter-lyrics-generator");
    worker.setDaemon(true);
    worker.start();
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

  private void updateLyricsPanel(Musique track) {
    if (track == null) {
      lyricsTextArea.setText("Aucune piste en cours.");
      setLyricsStatus("Lancez une musique pour générer les paroles.");
      generateLyricsButton.setDisable(true);
      copyLyricsButton.setDisable(true);
    } else {
      if (!lyricsLoading) {
        lyricsTextArea.setText("Cliquez sur Générer pour créer des paroles pour ce morceau.");
        setLyricsStatus("Prêt à générer.");
        generateLyricsButton.setDisable(false);
        copyLyricsButton.setDisable(true);
      }
    }
  }

  private void setLyricsStatus(String text) {
    if (lyricsStatusLabel != null) {
      lyricsStatusLabel.setText(text);
    }
  }
}
