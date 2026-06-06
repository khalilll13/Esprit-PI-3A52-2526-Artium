package services;

import entities.Musique;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import utils.ImageUrlUtils;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

public final class GlobalMediaPlayerService {
    public enum PlaybackMode {
        NORMAL, SHUFFLE, SMART_SHUFFLE
    }
    private static final String XAMPP_IMAGE_DIR = "C:\\xampp\\htdocs\\img";
    private static final String XAMPP_AUDIO_DIR = "C:\\xampp\\htdocs\\audio";
    private static final String XAMPP_UPLOADS_AUDIO_DIR = "C:\\xampp\\htdocs\\uploads\\audio";
    private static final String DEFAULT_TITLE = "Aucune piste en lecture";
    private static final String DEFAULT_ARTIST = "Artiste inconnu";

    private static final GlobalMediaPlayerService INSTANCE = new GlobalMediaPlayerService();

    private final ReadOnlyObjectWrapper<Musique> currentTrack = new ReadOnlyObjectWrapper<>();
    private final StringProperty trackTitle = new SimpleStringProperty(DEFAULT_TITLE);
    private final StringProperty trackArtist = new SimpleStringProperty(DEFAULT_ARTIST);
    private final StringProperty trackMeta = new SimpleStringProperty("Genre: -");
    private final StringProperty statusText = new SimpleStringProperty("Pret");
    private final StringProperty timeText = new SimpleStringProperty("0:00 / 0:00");
    private final DoubleProperty progressFraction = new SimpleDoubleProperty(0.0);
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private final ObjectProperty<Image> coverImage = new SimpleObjectProperty<>();

    private final javafx.beans.property.DoubleProperty volume = new javafx.beans.property.SimpleDoubleProperty(0.5);
    private final javafx.beans.property.BooleanProperty muted = new javafx.beans.property.SimpleBooleanProperty(false);

    private final ObservableList<Musique> queue = FXCollections.observableArrayList();
    private int queueIndex = -1;
    private final BooleanProperty isPlaylistMode = new SimpleBooleanProperty(false);
    
    private final ObjectProperty<PlaybackMode> playbackMode = new SimpleObjectProperty<>(PlaybackMode.NORMAL);
    private int smartShuffleCounter = 0;
    private final Random random = new Random();

    private MediaPlayer mediaPlayer;

    private GlobalMediaPlayerService() {
    }

    public static GlobalMediaPlayerService getInstance() {
        return INSTANCE;
    }

    public void playTrack(Musique track, List<Musique> queueTracks, int selectedIndex, String artistName, boolean isPlaylist) {
        if (track == null) {
            return;
        }
        smartShuffleCounter = 0;
        this.isPlaylistMode.set(isPlaylist);

        queue.setAll(queueTracks != null ? queueTracks : List.of(track));
        if (queue.isEmpty()) {
            queue.add(track);
        }

        int resolvedIndex = selectedIndex;
        if (resolvedIndex < 0 || resolvedIndex >= queue.size()) {
            resolvedIndex = queue.indexOf(track);
        }
        queueIndex = resolvedIndex >= 0 ? resolvedIndex : 0;

        openTrack(track, artistName, true);
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) {
            Musique fallback = currentTrack.get();
            if (fallback != null) {
                openTrack(fallback, trackArtist.get(), true);
            }
            return;
        }

        MediaPlayer.Status status = mediaPlayer.getStatus();
        if (status == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playing.set(false);
            statusText.set("En pause");
            return;
        }

        mediaPlayer.play();
        playing.set(true);
        statusText.set("Lecture en cours");
    }

    public void playPrevious() {
        if (queue.isEmpty()) {
            return;
        }
        smartShuffleCounter = 0;
        
        if (isPlaylistMode.get() && playbackMode.get() == PlaybackMode.SHUFFLE) {
            if (queue.size() > 1) {
                int nextIndex;
                do {
                    nextIndex = random.nextInt(queue.size());
                } while (nextIndex == queueIndex);
                queueIndex = nextIndex;
            }
        } else {
            // NORMAL and SMART_SHUFFLE both go sequentially backwards
            queueIndex = queueIndex <= 0 ? queue.size() - 1 : queueIndex - 1;
        }
        
        Musique target = queue.get(queueIndex);
        openTrack(target, trackArtist.get(), true);
    }

    public void playNext() {
        if (queue.isEmpty()) {
            return;
        }

        if (isPlaylistMode.get() && playbackMode.get() == PlaybackMode.SHUFFLE) {
            if (queue.size() > 1) {
                int nextIndex;
                do {
                    nextIndex = random.nextInt(queue.size());
                } while (nextIndex == queueIndex);
                queueIndex = nextIndex;
            } else {
                queueIndex = 0;
            }
            Musique target = queue.get(queueIndex);
            openTrack(target, trackArtist.get(), true);
            return;
        }

        if (isPlaylistMode.get() && playbackMode.get() == PlaybackMode.SMART_SHUFFLE) {
            if (smartShuffleCounter >= 2) {
                // Play a smart song from outside the playlist
                Musique current = currentTrack.get();
                String genre = current != null && current.getGenre() != null ? current.getGenre() : "";
                
                try {
                    services.MusiqueService ms = new services.MusiqueService();
                    List<Musique> allMusics = ms.getAll();
                    
                    // Candidates: same genre, not currently playing, not in queue
                    List<Musique> candidates = allMusics.stream()
                        .filter(m -> !genre.isEmpty() ? (m.getGenre() != null && m.getGenre().equalsIgnoreCase(genre)) : true)
                        .filter(m -> current == null || current.getId() == null || !current.getId().equals(m.getId()))
                        .filter(m -> queue.stream().noneMatch(q -> q.getId() != null && q.getId().equals(m.getId())))
                        .collect(Collectors.toList());
                        
                    if (candidates.isEmpty()) {
                        // Fallback: any song not in queue and not currently playing
                        candidates = allMusics.stream()
                            .filter(m -> current == null || current.getId() == null || !current.getId().equals(m.getId()))
                            .filter(m -> queue.stream().noneMatch(q -> q.getId() != null && q.getId().equals(m.getId())))
                            .collect(Collectors.toList());
                    }
                        
                    if (!candidates.isEmpty()) {
                        Musique smartSong = candidates.get(random.nextInt(candidates.size()));
                        smartShuffleCounter = 0;
                        // Play the external song without modifying the internal queue index
                        openTrack(smartSong, "✨ IA: " + (smartSong.getGenre() != null ? smartSong.getGenre() : ""), true);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Impossible de charger une musique smart: " + e.getMessage());
                }
            }
            
            // If we are here, we either didn't reach 2 yet, or fetching an external song failed.
            smartShuffleCounter++;
            queueIndex = queueIndex >= queue.size() - 1 ? 0 : queueIndex + 1;
            Musique target = queue.get(queueIndex);
            openTrack(target, trackArtist.get(), true);
            return;
        }

        // NORMAL mode (or not in a playlist)
        queueIndex = queueIndex >= queue.size() - 1 ? 0 : queueIndex + 1;
        Musique target = queue.get(queueIndex);
        openTrack(target, trackArtist.get(), true);
    }

    public void seekToFraction(double fraction) {
        if (mediaPlayer == null) {
            return;
        }
        Duration total = mediaPlayer.getTotalDuration();
        if (total == null || total.isUnknown() || total.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        mediaPlayer.seek(total.multiply(clamped));
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        playing.set(false);
        progressFraction.set(0.0);
        statusText.set("Pret");
    }

    public ReadOnlyObjectProperty<Musique> currentTrackProperty() {
        return currentTrack.getReadOnlyProperty();
    }

    public ObjectProperty<PlaybackMode> playbackModeProperty() {
        return playbackMode;
    }

    public void togglePlaybackMode() {
        PlaybackMode current = playbackMode.get();
        if (current == PlaybackMode.NORMAL) {
            playbackMode.set(PlaybackMode.SHUFFLE);
        } else if (current == PlaybackMode.SHUFFLE) {
            playbackMode.set(PlaybackMode.SMART_SHUFFLE);
        } else {
            playbackMode.set(PlaybackMode.NORMAL);
        }
        smartShuffleCounter = 0;
    }

    public StringProperty trackTitleProperty() {
        return trackTitle;
    }

    public StringProperty trackArtistProperty() {
        return trackArtist;
    }

    public StringProperty trackMetaProperty() {
        return trackMeta;
    }

    public StringProperty statusTextProperty() {
        return statusText;
    }

    public StringProperty timeTextProperty() {
        return timeText;
    }

    public ReadOnlyDoubleProperty progressFractionProperty() {
        return progressFraction;
    }

    public BooleanProperty playingProperty() {
        return playing;
    }

    public ObjectProperty<Image> coverImageProperty() {
        return coverImage;
    }

    public Musique getCurrentTrack() {
        return currentTrack.get();
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public javafx.beans.property.DoubleProperty volumeProperty() {
        return volume;
    }

    public javafx.beans.property.BooleanProperty mutedProperty() {
        return muted;
    }

    public void setVolume(double value) {
        volume.set(value);
    }

    public void toggleMute() {
        muted.set(!muted.get());
    }

    private void openTrack(Musique track, String artistName, boolean autoPlay) {
        String source = toMediaSource(track.getAudio());
        if (source == null) {
            statusText.set("Fichier audio introuvable");
            return;
        }

        stop();

        try {
            Media media = new Media(source);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.volumeProperty().bind(volume);
            mediaPlayer.muteProperty().bind(muted);
            currentTrack.set(track);
            trackTitle.set(track.getTitre() != null && !track.getTitre().isBlank() ? track.getTitre() : "Sans titre");
            trackArtist.set(normalizeArtist(artistName));
            trackMeta.set("Genre: " + (track.getGenre() != null && !track.getGenre().isBlank() ? track.getGenre() : "-"));
            coverImage.set(loadImageSafely(track.getImage()));
            statusText.set("Chargement...");
            timeText.set("0:00 / 0:00");
            progressFraction.set(0.0);

            mediaPlayer.setOnReady(() -> {
                // Apply effects if present
                String rawAudio = track.getAudio();
                if (rawAudio != null && rawAudio.contains("?")) {
                    String query = rawAudio.substring(rawAudio.indexOf('?') + 1);
                    double rate = 1.0;
                    double balance = 0.0;
                    double sub = 0.0, bass = 0.0, mid = 0.0, pres = 0.0, brill = 0.0;
                    for (String param : query.split("&")) {
                        String[] kv = param.split("=");
                        if (kv.length == 2) {
                            try {
                                double val = Double.parseDouble(kv[1]);
                                switch (kv[0]) {
                                    case "rate": rate = val; break;
                                    case "bal": balance = val; break;
                                    case "sub": sub = val; break;
                                    case "bass": bass = val; break;
                                    case "mid": mid = val; break;
                                    case "pres": pres = val; break;
                                    case "brill": brill = val; break;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    mediaPlayer.setRate(rate);
                    mediaPlayer.setBalance(balance);
                    javafx.scene.media.AudioEqualizer eq = mediaPlayer.getAudioEqualizer();
                    if (eq != null && (sub != 0 || bass != 0 || mid != 0 || pres != 0 || brill != 0)) {
                        eq.setEnabled(true);
                        for (javafx.scene.media.EqualizerBand band : eq.getBands()) {
                            double freq = band.getCenterFrequency();
                            if (freq <= 64) {
                                band.setGain(sub);
                            } else if (freq <= 250) {
                                band.setGain(bass);
                            } else if (freq <= 1000) {
                                band.setGain(mid);
                            } else if (freq <= 4000) {
                                band.setGain(pres);
                            } else {
                                band.setGain(brill);
                            }
                        }
                    }
                }

                Duration total = mediaPlayer.getTotalDuration();
                timeText.set(formatDuration(Duration.ZERO) + " / " + formatDuration(total));
                updateProgressFraction(Duration.ZERO, total);
                if (autoPlay) {
                    mediaPlayer.play();
                    playing.set(true);
                    statusText.set("Lecture en cours");
                } else {
                    playing.set(false);
                    statusText.set("Pret");
                }
            });

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                Duration total = mediaPlayer.getTotalDuration();
                timeText.set(formatDuration(newTime) + " / " + formatDuration(total));
                updateProgressFraction(newTime, total);
            });

            mediaPlayer.setOnPaused(() -> {
                playing.set(false);
                statusText.set("En pause");
            });
            mediaPlayer.setOnPlaying(() -> {
                playing.set(true);
                statusText.set("Lecture en cours");
            });
            mediaPlayer.setOnEndOfMedia(this::playNext);
            mediaPlayer.setOnError(() -> {
                playing.set(false);
                statusText.set(describeMediaError(mediaPlayer.getError()));
            });
        } catch (MediaException mediaException) {
            statusText.set(describeMediaError(mediaException));
        } catch (RuntimeException runtimeException) {
            statusText.set("Impossible de lire ce fichier audio");
        }
    }

    private String normalizeArtist(String artistName) {
        if (artistName == null || artistName.isBlank()) {
            return DEFAULT_ARTIST;
        }
        return artistName;
    }

    private String toMediaSource(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            return null;
        }

        String trimmed = audioPath.trim();
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex >= 0) {
            trimmed = trimmed.substring(0, queryIndex);
        }

        File local = resolveLocalAudioFile(trimmed);
        if (local != null && local.exists() && local.isFile()) {
            return local.toURI().toString();
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file:/")) {
            return trimmed;
        }
        return null;
    }

    private File resolveLocalAudioFile(String source) {
        String trimmed = source;
        if (trimmed.length() > 1 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("/") && trimmed.length() > 2 && trimmed.charAt(2) == ':') {
            trimmed = trimmed.substring(1);
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

        File directFile = new File(trimmed);
        if (directFile.exists() && directFile.isFile()) {
            return directFile;
        }

        String fileName = extractFileName(trimmed);
        if (!fileName.isEmpty()) {
            File[] fallbackDirs = {
                new File("C:\\xampp\\htdocs\\audio"),
                new File("C:\\xampp\\htdocs\\music"),
                new File("C:\\xampp\\htdocs\\uploads\\audio"),
                new File("C:\\xampp\\htdocs\\uploads\\music"),
                new File("C:\\xampp\\htdocs\\img"),
                new File("c:\\Work\\3eme\\Semestre2\\PI_Dev\\Artium(final)\\ARTIUM\\public\\uploads\\music"),
                new File("c:\\Work\\3eme\\Semestre2\\PI_Dev\\Artium(final)\\ARTIUM\\public\\audio")
            };
            for (File dir : fallbackDirs) {
                File candidate = new File(dir, fileName);
                if (candidate.exists() && candidate.isFile()) {
                    return candidate;
                }
            }
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return null;
        }

        return new File(trimmed);
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

    private void updateProgressFraction(Duration current, Duration total) {
        if (total == null || total.isUnknown() || total.lessThanOrEqualTo(Duration.ZERO)) {
            progressFraction.set(0.0);
            return;
        }
        double millis = current != null && !current.isUnknown() ? current.toMillis() : 0.0;
        progressFraction.set(Math.max(0.0, Math.min(1.0, millis / total.toMillis())));
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.lessThanOrEqualTo(Duration.ZERO)) {
            return "0:00";
        }

        int totalSeconds = (int) Math.floor(duration.toSeconds());
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
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
                Image remote = new Image(trimmed, true);
                return remote.isError() ? null : remote;
            }
        } catch (RuntimeException ignored) {
            return null;
        }

        return null;
    }

    private File resolveLocalImageFile(String source) {
        String trimmed = source;
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

