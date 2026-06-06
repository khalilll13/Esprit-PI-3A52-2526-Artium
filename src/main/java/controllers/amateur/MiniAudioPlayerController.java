package controllers.amateur;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

public class MiniAudioPlayerController {

    @FXML
    private HBox playerBar;

    @FXML
    private Button togglePlayButton;

    private Consumer<String> navigationHandler;
    private boolean playing;

    @FXML
    public void initialize() {
        playerBar.setVisible(false);
        playerBar.setManaged(false);
    }

    public void setNavigationHandler(Consumer<String> navigationHandler) {
        this.navigationHandler = navigationHandler;
    }

    public void setVisibleForRoute(String route) {
        playerBar.setVisible(true);
        playerBar.setManaged(true);
    }

    @FXML
    private void onTogglePlay() {
        MusicfrontController activeMusicController = MusicfrontController.getActiveController();
        if (activeMusicController != null) {
            activeMusicController.togglePlayPauseFromMini();
            playing = activeMusicController.isCurrentlyPlaying();
        } else {
            playing = !playing;
        }
        togglePlayButton.setText(playing ? "||" : ">");
    }

    @FXML
    private void onPrevTrack() {
        MusicfrontController activeMusicController = MusicfrontController.getActiveController();
        if (activeMusicController != null) {
            activeMusicController.playPreviousFromMini();
            playing = activeMusicController.isCurrentlyPlaying();
            togglePlayButton.setText(playing ? "||" : ">");
        }
    }

    @FXML
    private void onNextTrack() {
        MusicfrontController activeMusicController = MusicfrontController.getActiveController();
        if (activeMusicController != null) {
            activeMusicController.playNextFromMini();
            playing = activeMusicController.isCurrentlyPlaying();
            togglePlayButton.setText(playing ? "||" : ">");
        }
    }

    @FXML
    private void onOpenMusic() {
        if (navigationHandler != null) {
            navigationHandler.accept("musique");
        }
    }
}


