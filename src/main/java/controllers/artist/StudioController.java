package controllers.artist;

import audio.dsp.AudioAnalysis;
import audio.dsp.AudioEditOperations;
import audio.dsp.AudioEffects;
import audio.dsp.EffectChainSettings;
import audio.model.*;
import audio.service.StudioAiAssistantService;
import audio.service.StudioAudioService;
import audio.service.StudioPlaybackService;
import components.studio.LevelMeterPane;
import components.studio.SpectrumPane;
import components.studio.TimeRulerPane;
import components.studio.WaveformPane;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import utils.AudioPathResolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Professional audio editing workstation UI.
 */
public class StudioController {

    @FXML private BorderPane rootPane;
    @FXML private StackPane timeRulerHost;
    @FXML private StackPane waveformHost;
    @FXML private StackPane meterHost;
    @FXML private StackPane spectrumHost;
    @FXML private VBox trackPanel;
    @FXML private Label trackInfoLabel;
    @FXML private Label headerDurationLabel;
    @FXML private VBox eqPanel;
    @FXML private VBox dynamicsPanel;
    @FXML private VBox effectsPanel;
    @FXML private VBox enhancePanel;
    @FXML private VBox aiPanel;
    @FXML private VBox masterPanel;
    @FXML private VBox slicesListVBox;
    @FXML private Label statusLabel;
    @FXML private Label trackNameLabel;
    @FXML private Label timePositionLabel;
    @FXML private Label durationLabel;
    @FXML private Label analysisLabel;
    @FXML private Button playPauseButton;
    @FXML private ProgressBar loadProgress;
    @FXML private ProgressBar exportProgress;
    @FXML private ComboBox<String> exportFormatCombo;
    @FXML private ComboBox<String> exportBitrateCombo;
    @FXML private ComboBox<String> exportSampleRateCombo;
    @FXML private ComboBox<String> exportChannelsCombo;
    @FXML private CheckBox exportNormalizeCheck;

    private final StudioAudioService audioService = new StudioAudioService();
    private final StudioPlaybackService playback = new StudioPlaybackService();
    private final StudioAiAssistantService aiService = new StudioAiAssistantService();
    private final ScheduledExecutorService meterExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "studio-meters");
        t.setDaemon(true);
        return t;
    });

    private WaveformPane waveformPane;
    private TimeRulerPane timeRulerPane;
    private LevelMeterPane meterPane;
    private SpectrumPane spectrumPane;
    private Stage dialogStage;
    private String finalAudioPath;
    private String originalAudioPath;
    private File pendingFile;
    private EffectChainSettings fx;
    private boolean initialized;
    private Slider trackVolumeSlider;
    private Slider trackPanSlider;
    private final ScheduledExecutorService fxDebounce = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "studio-fx-live");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> fxLiveTask;
    private boolean trackLoaded;
    private final List<TimeSelection> slices = new ArrayList<>();

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
        stage.setMinWidth(1200);
        stage.setMinHeight(760);
        stage.setOnCloseRequest(e -> cleanup());
        stage.setOnShown(e -> {
            refreshTimeline(true);
            triggerPendingLoad();
        });
    }

    /** Ouvre le studio avec le fichier déjà choisi dans le formulaire musique. */
    public void openWithAudio(File audioFile, String originalReference) {
        if (audioFile == null || !audioFile.exists()) {
            return;
        }
        originalAudioPath = originalReference;
        pendingFile = audioFile;
        triggerPendingLoad();
    }

    /** Compatibilité — résout et charge automatiquement. */
    public void setAudioPath(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            return;
        }
        try {
            File resolved = AudioPathResolver.resolveForStudio(audioPath);
            openWithAudio(resolved, audioPath);
        } catch (IOException e) {
            if (initialized) {
                Platform.runLater(() -> statusLabel.setText("Erreur : " + e.getMessage()));
            }
        }
    }

    private void triggerPendingLoad() {
        if (!initialized || pendingFile == null) {
            return;
        }
        File file = pendingFile;
        pendingFile = null;
        Platform.runLater(() -> loadResolvedFile(file));
    }

    public String getFinalAudioPath() {
        return finalAudioPath;
    }

    @FXML
    public void initialize() {
        fx = audioService.getProject().getMasterEffects();
        initVisualComponents();
        buildEqPanel();
        buildDynamicsPanel();
        buildEffectsPanel();
        buildEnhancePanel();
        buildAiPanel();
        buildMasterPanel();
        initExportCombos();
        setupDragAndDrop();
        setupKeyboardShortcuts();
        playback.setPlayheadListener(frame -> Platform.runLater(() -> {
            audioService.getProject().setPlayheadFrame(frame);
            refreshTimeline(false);
        }));
        playback.setOnEndedListener(() -> Platform.runLater(() -> {
            updateTransportState(false);
            statusLabel.setText("Fin de lecture");
        }));
        updateTransportState(false);
        meterExecutor.scheduleAtFixedRate(this::updateMetersSafe, 500, 200, TimeUnit.MILLISECONDS);
        waveformPane.widthProperty().addListener((o, a, b) -> {
            if (b.doubleValue() > 50 && audioService.getProject().getMasterBuffer().getFrameCount() > 0) {
                refreshTimeline(true);
            }
        });
        initialized = true;
        triggerPendingLoad();
    }

    private void initVisualComponents() {
        waveformPane = new WaveformPane();
        timeRulerPane = new TimeRulerPane();
        meterPane = new LevelMeterPane();
        spectrumPane = new SpectrumPane();
        waveformHost.getChildren().setAll(waveformPane);
        timeRulerHost.getChildren().setAll(timeRulerPane);
        meterHost.getChildren().setAll(meterPane);
        spectrumHost.getChildren().setAll(spectrumPane);
        waveformPane.setOnPlayheadChanged(f -> {
            audioService.getProject().setPlayheadFrame(f);
            playback.setPlayheadFrame(f);
            refreshTimeline(false);
        });
        waveformPane.setOnSelectionChanged(sel -> audioService.getProject().setSelection(sel));
    }

    private void loadResolvedFile(File file) {
        trackNameLabel.setText(file.getName());
        statusLabel.setText("Chargement de « " + file.getName() + " »…");
        loadProgress.setVisible(true);
        loadProgress.setProgress(-1);
        audioService.loadFile(file, () -> Platform.runLater(() -> {
            loadProgress.setVisible(false);
            finalAudioPath = audioService.getProject().getWorkingPath() != null
                    ? audioService.getProject().getWorkingPath().toString() : file.getAbsolutePath();
            updateTrackPanel(file);
            refreshTimeline(true);
            runAnalysis();
            trackLoaded = true;
            updateTransportState(false);
            statusLabel.setText("Prêt — effets en temps réel pendant la lecture");
            initDefaultSlices();
        }), err -> Platform.runLater(() -> {
            statusLabel.setText("Erreur : " + err);
            loadProgress.setVisible(false);
            trackNameLabel.setText("Échec chargement");
        }));
    }

    private void updateTrackPanel(File file) {
        StudioProject p = audioService.getProject();
        AudioBuffer buf = p.getMasterBuffer();
        StudioTrack track = p.getPrimaryTrack();
        String dur = formatFrames(buf.getFrameCount(), buf.getSampleRate());
        if (headerDurationLabel != null) {
            headerDurationLabel.setText(dur);
        }
        if (trackInfoLabel != null) {
            trackInfoLabel.setText(String.format(Locale.ROOT,
                    "%s\n%.1f sec • %d Hz • %d canaux",
                    file.getName(), buf.getDurationSeconds(), buf.getSampleRate(), buf.getChannels()));
        }
        if (trackPanel.getChildren().size() <= 2) {
            buildTrackControls(track);
        }
    }

    private void buildTrackControls(StudioTrack track) {
        Label volLabel = new Label("Volume");
        volLabel.getStyleClass().add("title");
        trackVolumeSlider = new Slider(0, 2, track.getVolume());
        trackVolumeSlider.getStyleClass().add("studio-slider");
        trackVolumeSlider.valueProperty().addListener((o, a, b) -> {
            track.setVolume(b.doubleValue());
            notifyFxChanged();
        });

        Label panLabel = new Label("Panoramique (L ← → R)");
        panLabel.getStyleClass().add("title");
        trackPanSlider = new Slider(-1, 1, track.getPan());
        trackPanSlider.getStyleClass().add("studio-slider");
        trackPanSlider.valueProperty().addListener((o, a, b) -> {
            track.setPan(b.doubleValue());
            fx.pan = b.doubleValue();
            notifyFxChanged();
        });

        CheckBox mute = new CheckBox("Muet");
        mute.getStyleClass().add("studio-check-box");
        mute.selectedProperty().addListener((o, a, b) -> track.setMuted(b));

        trackPanel.getChildren().addAll(volLabel, trackVolumeSlider, panLabel, trackPanSlider, mute);
    }

    private void refreshTimeline(boolean rebuildPeaks) {
        StudioProject p = audioService.getProject();
        AudioBuffer buf = p.getMasterBuffer();
        if (rebuildPeaks && buf.getFrameCount() > 0) {
            int w = Math.max(256, (int) waveformPane.getWidth());
            waveformPane.setPeaks(audioService.buildPeaks(w > 0 ? w : 1024));
        }
        waveformPane.setPlayheadFrame(p.getPlayheadFrame());
        waveformPane.setSelection(p.getSelection());
        waveformPane.setZoom(p.getZoom());
        timeRulerPane.configure(buf.getFrameCount(), buf.getSampleRate(), p.getPlayheadFrame(), p.getZoom());
        timePositionLabel.setText(formatFrames(p.getPlayheadFrame(), buf.getSampleRate()));
        durationLabel.setText("/ " + formatFrames(buf.getFrameCount(), buf.getSampleRate()));
    }

    private void runAnalysis() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AudioAnalysis.AnalysisReport r = audioService.analyze();
            float[] spec = r.spectrum;
            float[][] specGram = AudioAnalysis.computeSpectrogram(
                    audioService.getProject().getMasterBuffer(), 64, 48);
            Platform.runLater(() -> {
                spectrumPane.setSpectrum(spec);
                spectrumPane.setSpectrogram(specGram);
                analysisLabel.setText(String.format(Locale.ROOT,
                        "Peak %.1f dB | RMS %.1f dB | LUFS ~%.1f | DR %.1f dB%s",
                        r.peakDb, r.rmsDb, r.estimatedLufs, r.dynamicRangeDb,
                        r.clippingDetected ? " | CLIP" : ""));
                meterPane.update(r.peakDb, r.rmsDb, r.estimatedLufs, r.clippingDetected);
            });
        });
    }

    private void updateMetersSafe() {
        if (!Platform.isFxApplicationThread()) {
            try {
                AudioAnalysis.AnalysisReport r = audioService.analyze();
                Platform.runLater(() -> meterPane.update(r.peakDb, r.rmsDb, r.estimatedLufs, r.clippingDetected));
            } catch (Exception ignored) {
            }
        }
    }

    private void setupDragAndDrop() {
        waveformHost.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        waveformHost.setOnDragDropped(e -> {
            var db = e.getDragboard();
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                loadResolvedFile(db.getFiles().get(0));
            }
            e.setDropCompleted(true);
            e.consume();
        });
    }

    private void setupKeyboardShortcuts() {
        rootPane.sceneProperty().addListener((o, old, scene) -> {
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
            }
        });
    }

    private void onKey(KeyEvent e) {
        if (e.getTarget() instanceof TextInputControl) {
            return;
        }
        if (e.isControlDown() && e.getCode() == KeyCode.Z) {
            handleUndo();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.Y) {
            handleRedo();
            e.consume();
        } else if (e.getCode() == KeyCode.SPACE) {
            handlePlayPause();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.C) {
            handleCopy();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.X) {
            handleCut();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.V) {
            handlePaste();
            e.consume();
        } else if (e.getCode() == KeyCode.DELETE) {
            handleDelete();
            e.consume();
        }
    }

  // ——— Transport ———

    @FXML private void handlePlay() {
        StudioProject p = audioService.getProject();
        if (!trackLoaded || p.getMasterBuffer().getFrameCount() == 0) {
            statusLabel.setText("Aucune piste chargée");
            return;
        }
        if (playback.isPaused()) {
            playback.resume();
            updateTransportState(true);
            statusLabel.setText("Lecture…");
            return;
        }
        if (playback.isPlaying()) {
            return;
        }
        AudioBuffer playBuf = audioService.getPlaybackBuffer();
        long from = p.getPlayheadFrame();
        if (from >= p.getMasterBuffer().getFrameCount()) {
            from = 0;
            p.setPlayheadFrame(0);
        }
        playback.play(playBuf, from);
        updateTransportState(true);
        statusLabel.setText("Lecture avec effets en direct…");
    }

    @FXML private void handlePause() {
        if (playback.isPlaying()) {
            playback.pause();
            updateTransportState(false);
            statusLabel.setText("Pause");
        } else if (playback.isPaused()) {
            statusLabel.setText("Déjà en pause");
        }
    }

    private void handlePlayPause() {
        if (playback.isPlaying()) {
            handlePause();
        } else {
            handlePlay();
        }
    }

    @FXML
    private void handlePlayPauseToggle() {
        handlePlayPause();
    }

    /** Effets appliqués en temps réel pendant la lecture. */
    private void notifyFxChanged() {
        if (fxLiveTask != null) {
            fxLiveTask.cancel(false);
        }
        fxLiveTask = fxDebounce.schedule(() -> Platform.runLater(this::applyLiveEffects), 120,
                TimeUnit.MILLISECONDS);
    }

    private void applyLiveEffects() {
        if (!trackLoaded) {
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            AudioAnalysis.AnalysisReport r = audioService.analyze();
            Platform.runLater(() -> {
                spectrumPane.setSpectrum(r.spectrum);
                meterPane.update(r.peakDb, r.rmsDb, r.estimatedLufs, r.clippingDetected);
                if (playback.isActive()) {
                    playback.updateBuffer(audioService.getPlaybackBuffer());
                }
            });
        });
    }

    private void updateTransportState(boolean playing) {
        if (playPauseButton != null) {
            playPauseButton.setDisable(!trackLoaded);
            if (playing) {
                playPauseButton.setText("⏸ Pause");
                playPauseButton.getStyleClass().removeAll("studio-btn-play");
                if (!playPauseButton.getStyleClass().contains("studio-btn-pause")) {
                    playPauseButton.getStyleClass().add("studio-btn-pause");
                }
            } else {
                playPauseButton.setText("▶ Lecture");
                playPauseButton.getStyleClass().removeAll("studio-btn-pause");
                if (!playPauseButton.getStyleClass().contains("studio-btn-play")) {
                    playPauseButton.getStyleClass().add("studio-btn-play");
                }
            }
        }
    }

    @FXML private void handleApplyEffects() {
        statusLabel.setText("Application des effets…");
        Executors.newSingleThreadExecutor().execute(() -> {
            audioService.applyEffectsToMaster(fx);
            Platform.runLater(() -> {
                refreshTimeline(true);
                runAnalysis();
                statusLabel.setText("Effets intégrés dans la piste");
            });
        });
    }

    @FXML private void handleStop() {
        playback.stop();
        updateTransportState(false);
        statusLabel.setText("Arrêté");
    }

  // ——— Edit ———

    @FXML private void handleUndo() {
        if (audioService.getHistory().undo(audioService.getProject())) {
            refreshTimeline(true);
            runAnalysis();
        }
    }

    @FXML private void handleRedo() {
        if (audioService.getHistory().redo(audioService.getProject())) {
            refreshTimeline(true);
            runAnalysis();
        }
    }

    @FXML private void handleCut() {
        TimeSelection sel = audioService.getProject().getSelection();
        if (sel == null || sel.isEmpty()) {
            return;
        }
        audioService.commitEdit(() -> {
            audioService.getProject().setClipboard(
                    AudioEditOperations.copyRegion(audioService.getProject().getMasterBuffer(), sel));
            audioService.getProject().setMasterBuffer(
                    AudioEditOperations.cut(audioService.getProject().getMasterBuffer(), sel));
        });
        afterEdit();
    }

    @FXML private void handleCopy() {
        TimeSelection sel = audioService.getProject().getSelection();
        if (sel == null || sel.isEmpty()) {
            return;
        }
        audioService.getProject().setClipboard(
                AudioEditOperations.copyRegion(audioService.getProject().getMasterBuffer(), sel));
        statusLabel.setText("Copié");
    }

    @FXML private void handlePaste() {
        AudioBuffer clip = audioService.getProject().getClipboard();
        if (clip == null) {
            return;
        }
        long at = audioService.getProject().getPlayheadFrame();
        audioService.commitEdit(() -> audioService.getProject().setMasterBuffer(
                AudioEditOperations.paste(audioService.getProject().getMasterBuffer(), clip, at)));
        afterEdit();
    }

    @FXML private void handleDelete() {
        TimeSelection sel = audioService.getProject().getSelection();
        if (sel == null || sel.isEmpty()) {
            return;
        }
        audioService.commitEdit(() -> audioService.getProject().setMasterBuffer(
                AudioEditOperations.deleteRegion(audioService.getProject().getMasterBuffer(), sel)));
        afterEdit();
    }

    @FXML private void handleSplit() {
        long f = audioService.getProject().getPlayheadFrame();
        long totalFrames = audioService.getProject().getMasterBuffer().getFrameCount();
        if (f <= 0 || f >= totalFrames) {
            statusLabel.setText("Placez le curseur au point de scission");
            return;
        }
        
        int targetIndex = -1;
        for (int i = 0; i < slices.size(); i++) {
            TimeSelection s = slices.get(i);
            if (f > s.getStartSample() && f < s.getEndSample()) {
                targetIndex = i;
                break;
            }
        }
        
        if (targetIndex != -1) {
            TimeSelection oldSlice = slices.get(targetIndex);
            TimeSelection part1 = new TimeSelection(oldSlice.getStartSample(), f);
            TimeSelection part2 = new TimeSelection(f, oldSlice.getEndSample());
            slices.remove(targetIndex);
            slices.add(targetIndex, part2);
            slices.add(targetIndex, part1);
            statusLabel.setText("Tranche scindée au curseur (" + formatFrames(f, audioService.getProject().getMasterBuffer().getSampleRate()) + ")");
            refreshSlicesUI();
        } else {
            statusLabel.setText("Aucune tranche correspondante au curseur");
        }
    }

    @FXML private void handleTrimStart() {
        long f = audioService.getProject().getPlayheadFrame();
        audioService.commitEdit(() -> audioService.getProject().setMasterBuffer(
                AudioEditOperations.trimStart(audioService.getProject().getMasterBuffer(), f)));
        afterEdit();
    }

    @FXML private void handleTrimEnd() {
        long f = audioService.getProject().getPlayheadFrame();
        audioService.commitEdit(() -> audioService.getProject().setMasterBuffer(
                AudioEditOperations.trimEnd(audioService.getProject().getMasterBuffer(), f)));
        afterEdit();
    }

    @FXML private void handleFadeIn() {
        applySelectionEdit((buf, sel) -> AudioEditOperations.fadeIn(buf, sel));
    }

    @FXML private void handleFadeOut() {
        applySelectionEdit((buf, sel) -> AudioEditOperations.fadeOut(buf, sel));
    }

    @FXML private void handleSilence() {
        applySelectionEdit(AudioEditOperations::silenceRegion);
    }

    @FXML private void handleReverse() {
        TimeSelection sel = audioService.getProject().getSelection();
        audioService.commitEdit(() -> {
            if (sel != null && !sel.isEmpty()) {
                AudioBuffer sub = AudioEditOperations.copyRegion(
                        audioService.getProject().getMasterBuffer(), sel);
                AudioBuffer rev = AudioEditOperations.reverse(sub);
                audioService.getProject().setMasterBuffer(
                        AudioEditOperations.paste(
                                audioService.getProject().getMasterBuffer(), rev, sel.getStartSample()));
            } else {
                audioService.getProject().setMasterBuffer(
                        AudioEditOperations.reverse(audioService.getProject().getMasterBuffer()));
            }
        });
        afterEdit();
    }

    @FXML private void handleZoomIn() {
        StudioProject p = audioService.getProject();
        p.setZoom(p.getZoom() * 1.25);
        refreshTimeline(false);
    }

    @FXML private void handleZoomOut() {
        StudioProject p = audioService.getProject();
        p.setZoom(p.getZoom() / 1.25);
        refreshTimeline(false);
    }

    @FXML private void handleReset() {
        EffectChainSettings fresh = new EffectChainSettings();
        copyEffectSettings(fresh, fx);
        notifyFxChanged();
        statusLabel.setText("Effets réinitialisés");
    }

    private void copyEffectSettings(EffectChainSettings from, EffectChainSettings to) {
        to.eqEnabled = from.eqEnabled;
        to.lowGainDb = from.lowGainDb;
        to.midGainDb = from.midGainDb;
        to.highGainDb = from.highGainDb;
        to.compressorEnabled = from.compressorEnabled;
        to.limiterEnabled = from.limiterEnabled;
        to.noiseReductionEnabled = from.noiseReductionEnabled;
        to.reverbEnabled = from.reverbEnabled;
        to.delayEnabled = from.delayEnabled;
        to.speed = from.speed;
        to.pitchSemitones = from.pitchSemitones;
    }

    @FXML private void handleImportTrack() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio", "*.wav", "*.mp3", "*.m4a"));
        File f = chooser.showOpenDialog(dialogStage);
        if (f != null) {
            audioService.importTrackAsync(f, () -> Platform.runLater(() ->
                    statusLabel.setText("Piste additionnelle : " + f.getName())),
                    err -> Platform.runLater(() -> statusLabel.setText("Import : " + err)));
        }
    }

    @FXML private void handleMergeTracks() {
        audioService.commitEdit(() -> audioService.getProject().setMasterBuffer(audioService.mixdown()));
        afterEdit();
    }

    @FXML private void handleAiCleanup() {
        var suggestions = aiService.analyzeAndSuggest(audioService.getProject());
        suggestions.stream().filter(s -> s.title().contains("Nettoyage")).findFirst()
                .ifPresent(s -> s.apply().run());
        applyAiEnhanceToBuffer();
    }

    @FXML private void handleAutoEnhance() {
        audioService.commitEdit(() -> audioService.getProject().setMasterBuffer(
                aiService.autoEnhance(audioService.getProject().getMasterBuffer())));
        afterEdit();
    }

    @FXML private void handleSave() {
        playback.stop();
        Path work = audioService.getProject().getWorkingPath();
        if (work == null) {
            work = Path.of(System.getProperty("java.io.tmpdir"), "artium-studio", "mix_final.wav");
        }
        ExportConfig cfg = buildExportConfig();
        cfg.setFormat(ExportConfig.Format.WAV);
        exportProgress.setVisible(true);
        statusLabel.setText("Export en cours…");
        Path out = work;
        audioService.exportAsync(out, cfg,
                v -> Platform.runLater(() -> exportProgress.setProgress(v)),
                err -> Platform.runLater(() -> {
                    statusLabel.setText(err);
                    exportProgress.setVisible(false);
                }),
                path -> Platform.runLater(() -> {
                    exportProgress.setVisible(false);
                    finalAudioPath = path.toString();
                    if (dialogStage != null) {
                        dialogStage.close();
                    }
                }));
    }

    @FXML private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("mix-export.wav");
        File dest = chooser.showSaveDialog(dialogStage);
        if (dest == null) {
            return;
        }
        ExportConfig cfg = buildExportConfig();
        exportProgress.setVisible(true);
        audioService.exportAsync(dest.toPath(), cfg,
                v -> Platform.runLater(() -> exportProgress.setProgress(v)),
                err -> Platform.runLater(() -> statusLabel.setText("Export: " + err)),
                path -> Platform.runLater(() -> {
                    exportProgress.setVisible(false);
                    finalAudioPath = path.toString();
                    statusLabel.setText("Exporté: " + path.getFileName());
                }));
    }

    @FXML private void handleCancel() {
        cleanup();
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void cleanup() {
        playback.stop();
        meterExecutor.shutdownNow();
        fxDebounce.shutdownNow();
    }

    private void afterEdit() {
        refreshTimeline(true);
        runAnalysis();
        statusLabel.setText("Modification appliquée");
        if (slices.isEmpty()) {
            initDefaultSlices();
        } else {
            long totalFrames = audioService.getProject().getMasterBuffer().getFrameCount();
            if (slices.get(slices.size() - 1).getEndSample() != totalFrames) {
                initDefaultSlices();
            } else {
                refreshSlicesUI();
            }
        }
    }

    private void applySelectionEdit(EditFn fn) {
        TimeSelection sel = audioService.getProject().getSelection();
        if (sel == null || sel.isEmpty()) {
            sel = new TimeSelection(0, audioService.getProject().getMasterBuffer().getFrameCount());
        }
        TimeSelection finalSel = sel;
        audioService.commitEdit(() -> audioService.getProject().setMasterBuffer(
                fn.apply(audioService.getProject().getMasterBuffer(), finalSel)));
        afterEdit();
    }

    private void applyAiEnhanceToBuffer() {
        audioService.commitEdit(() -> audioService.getProject().setMasterBuffer(
                aiService.autoEnhance(audioService.getProject().getMasterBuffer())));
        afterEdit();
    }

    @FunctionalInterface
    private interface EditFn {
        AudioBuffer apply(AudioBuffer buf, TimeSelection sel);
    }

  // ——— Panel builders ———

    private void buildEqPanel() {
        eqPanel.getChildren().add(new Label("Préréglages"));
        ComboBox<String> presets = new ComboBox<>();
        presets.getStyleClass().add("studio-combo-box");
        presets.getItems().addAll("flat", "vocal", "podcast", "music", "bass", "treble", "acoustic", "live");
        presets.setValue("flat");
        presets.setOnAction(e -> {
            AudioEffects.applyEqPreset(fx, presets.getValue());
            notifyFxChanged();
        });
        eqPanel.getChildren().add(presets);
        addToggle(eqPanel, "Égaliseur actif", fx.eqEnabled, v -> fx.eqEnabled = v);
        addSlider(eqPanel, "Graves (dB)", -12, 12, fx.lowGainDb, v -> fx.lowGainDb = v);
        addSlider(eqPanel, "Médiums (dB)", -12, 12, fx.midGainDb, v -> fx.midGainDb = v);
        addSlider(eqPanel, "Aigus (dB)", -12, 12, fx.highGainDb, v -> fx.highGainDb = v);
        addSlider(eqPanel, "Param 1 Hz", 80, 400, fx.param1Freq, v -> fx.param1Freq = v);
        addSlider(eqPanel, "Param 1 Gain", -12, 12, fx.param1GainDb, v -> fx.param1GainDb = v);
        addSlider(eqPanel, "Param 2 Hz", 800, 8000, fx.param2Freq, v -> fx.param2Freq = v);
        addSlider(eqPanel, "Param 2 Gain", -12, 12, fx.param2GainDb, v -> fx.param2GainDb = v);
    }

    private void buildDynamicsPanel() {
        addToggle(dynamicsPanel, "Compresseur", fx.compressorEnabled, v -> fx.compressorEnabled = v);
        addSlider(dynamicsPanel, "Seuil (dB)", -40, 0, fx.compressorThresholdDb, v -> fx.compressorThresholdDb = v);
        addSlider(dynamicsPanel, "Ratio", 1, 12, fx.compressorRatio, v -> fx.compressorRatio = v);
        addToggle(dynamicsPanel, "Limiteur", fx.limiterEnabled, v -> fx.limiterEnabled = v);
        addSlider(dynamicsPanel, "Plafond (dB)", -6, 0, fx.limiterCeilingDb, v -> fx.limiterCeilingDb = v);
        addToggle(dynamicsPanel, "Noise Gate", fx.gateEnabled, v -> fx.gateEnabled = v);
        addSlider(dynamicsPanel, "Gate seuil", -60, -10, fx.gateThresholdDb, v -> fx.gateThresholdDb = v);
        addToggle(dynamicsPanel, "Expander", fx.expanderEnabled, v -> fx.expanderEnabled = v);
        addToggle(dynamicsPanel, "De-Esser", fx.deEsserEnabled, v -> fx.deEsserEnabled = v);
        addSlider(dynamicsPanel, "De-Ess Hz", 4000, 10000, fx.deEsserFreq, v -> fx.deEsserFreq = v);
    }

    private void buildEffectsPanel() {
        addEffectBlock(effectsPanel, "Reverb", fx.reverbEnabled, v -> fx.reverbEnabled = v,
                "Room", 0, 1, fx.reverbRoom, v -> fx.reverbRoom = v);
        addEffectBlock(effectsPanel, "Delay", fx.delayEnabled, v -> fx.delayEnabled = v,
                "ms", 10, 1000, fx.delayMs, v -> fx.delayMs = v);
        addEffectBlock(effectsPanel, "Chorus", fx.chorusEnabled, v -> fx.chorusEnabled = v,
                "Depth", 0, 1, fx.chorusDepth, v -> fx.chorusDepth = v);
        addEffectBlock(effectsPanel, "Distortion", fx.distortionEnabled, v -> fx.distortionEnabled = v,
                "Drive", 0, 1, fx.distortionDrive, v -> fx.distortionDrive = v);
        addSlider(effectsPanel, "Pitch (demi-tons)", -12, 12, fx.pitchSemitones, v -> fx.pitchSemitones = v);
        addSlider(effectsPanel, "Vitesse", 0.5, 2, fx.speed, v -> fx.speed = v);
        addSlider(effectsPanel, "Time stretch", 0.5, 2, fx.timeStretch, v -> fx.timeStretch = v);
        addToggle(effectsPanel, "Stereo Widener", fx.widenerEnabled, v -> fx.widenerEnabled = v);
    }

    private void buildEnhancePanel() {
        addToggle(enhancePanel, "Réduction de bruit", fx.noiseReductionEnabled, v -> fx.noiseReductionEnabled = v);
        addSlider(enhancePanel, "Intensité NR", 0, 1, fx.noiseReductionAmount, v -> fx.noiseReductionAmount = v);
        addToggle(enhancePanel, "Suppression fond", fx.noiseReductionEnabled, v -> fx.noiseReductionEnabled = v);
        addToggle(enhancePanel, "Hum 50Hz", fx.humRemovalEnabled, v -> fx.humRemovalEnabled = v);
        addToggle(enhancePanel, "Clicks / Pops", fx.clickRemovalEnabled, v -> fx.clickRemovalEnabled = v);
        addToggle(enhancePanel, "Réduction souffle", fx.breathReductionEnabled, v -> fx.breathReductionEnabled = v);
        addToggle(enhancePanel, "Restauration", fx.restorationEnabled, v -> fx.restorationEnabled = v);
        addToggle(enhancePanel, "Voix améliorée", fx.voiceEnhanceEnabled, v -> fx.voiceEnhanceEnabled = v);
        addToggle(enhancePanel, "Clarté vocale", fx.vocalClarityEnabled, v -> fx.vocalClarityEnabled = v);
    }

    private void buildAiPanel() {
        Button analyze = new Button("Analyser et suggérer");
        analyze.setFocusTraversable(false);
        analyze.getStyleClass().add("studio-btn");
        analyze.setOnAction(e -> {
            aiPanel.getChildren().removeIf(n -> n instanceof Label && ((Label) n).getText().startsWith("•"));
            for (var s : aiService.analyzeAndSuggest(audioService.getProject())) {
                Label line = new Label("• " + s.title() + ": " + s.detail());
                line.setWrapText(true);
                line.setStyle("-fx-text-fill: #cbd5e1;");
                Button apply = new Button("Appliquer");
                apply.setFocusTraversable(false);
                apply.getStyleClass().add("studio-btn");
                apply.setOnAction(ev -> {
                    s.apply().run();
                    notifyFxChanged();
                    statusLabel.setText("Suggestion : " + s.title());
                });
                aiPanel.getChildren().addAll(line, apply);
            }
        });
        aiPanel.getChildren().add(analyze);
    }

    private void buildMasterPanel() {
        ComboBox<String> preset = new ComboBox<>();
        preset.getStyleClass().add("studio-combo-box");
        preset.getItems().addAll("music", "podcast", "streaming");
        preset.setValue("music");
        preset.setOnAction(e -> {
            audioService.getProject().getMastering().preset = preset.getValue();
            notifyFxChanged();
        });
        masterPanel.getChildren().addAll(new Label("Preset mastering"), preset);
        CheckBox norm = new CheckBox("Normaliser");
        norm.getStyleClass().add("studio-check-box");
        norm.setOnAction(e -> {
            audioService.getProject().getMastering().normalize = norm.isSelected();
            notifyFxChanged();
        });
        masterPanel.getChildren().add(norm);
        addSlider(masterPanel, "Cible LUFS", -20, -8, -14, v -> audioService.getProject().getMastering().targetLufs = v);
        CheckBox lim = new CheckBox("Limiteur final");
        lim.getStyleClass().add("studio-check-box");
        lim.setSelected(true);
        lim.setOnAction(e -> {
            audioService.getProject().getMastering().limiterEnabled = lim.isSelected();
            notifyFxChanged();
        });
        masterPanel.getChildren().add(lim);
        Button compare = new Button("Comparer avant / après");
        compare.setFocusTraversable(false);
        compare.getStyleClass().add("studio-btn");
        compare.setOnAction(e -> {
            AudioBuffer before = audioService.getProject().getMasterBuffer();
            AudioBuffer after = audioService.mixdown();
            statusLabel.setText(String.format(Locale.ROOT, "Avant peak %.2f → Après %.2f",
                    before.peakLevel(), after.peakLevel()));
        });
        masterPanel.getChildren().add(compare);
    }

    private void initExportCombos() {
        exportFormatCombo.getItems().addAll("WAV", "MP3", "FLAC", "AAC");
        exportFormatCombo.setValue("WAV");
        exportBitrateCombo.getItems().addAll("128", "192", "256", "320");
        exportBitrateCombo.setValue("320");
        exportSampleRateCombo.getItems().addAll("44100", "48000");
        exportSampleRateCombo.setValue("44100");
        exportChannelsCombo.getItems().addAll("1", "2");
        exportChannelsCombo.setValue("2");
    }

    private ExportConfig buildExportConfig() {
        ExportConfig cfg = new ExportConfig();
        cfg.setFormat(ExportConfig.Format.valueOf(exportFormatCombo.getValue()));
        cfg.setBitrateKbps(Integer.parseInt(exportBitrateCombo.getValue()));
        cfg.setSampleRate(Integer.parseInt(exportSampleRateCombo.getValue()));
        cfg.setChannels(Integer.parseInt(exportChannelsCombo.getValue()));
        audioService.getProject().getMastering().normalize = exportNormalizeCheck.isSelected();
        return cfg;
    }

    private void addSlider(VBox box, String label, double min, double max, double val, DoubleConsumer setter) {
        Label l = new Label(label);
        l.getStyleClass().add("title");
        Slider s = new Slider(min, max, val);
        s.getStyleClass().add("studio-slider");
        Label v = new Label(String.format(Locale.ROOT, "%.2f", val));
        v.getStyleClass().add("studio-slider-value");
        s.valueProperty().addListener((o, a, b) -> {
            setter.accept(b.doubleValue());
            v.setText(String.format(Locale.ROOT, "%.2f", b.doubleValue()));
            notifyFxChanged();
        });
        box.getChildren().addAll(l, s, v);
    }

    private void addToggle(VBox box, String label, boolean val, java.util.function.Consumer<Boolean> setter) {
        CheckBox cb = new CheckBox(label);
        cb.getStyleClass().add("studio-check-box");
        cb.setSelected(val);
        cb.selectedProperty().addListener((o, a, b) -> {
            setter.accept(b);
            notifyFxChanged();
        });
        box.getChildren().add(cb);
    }

    private void addEffectBlock(VBox box, String name, boolean enabled, java.util.function.Consumer<Boolean> en,
                                String paramLabel, double min, double max, double val, DoubleConsumer setter) {
        ToggleButton toggle = new ToggleButton(name);
        toggle.setFocusTraversable(false);
        toggle.getStyleClass().add("studio-toggle");
        toggle.setSelected(enabled);
        toggle.setOnAction(e -> {
            en.accept(toggle.isSelected());
            notifyFxChanged();
        });
        box.getChildren().add(toggle);
        addSlider(box, paramLabel, min, max, val, setter);
        Button reset = new Button("Reset " + name);
        reset.setFocusTraversable(false);
        reset.getStyleClass().add("studio-btn");
        reset.setOnAction(e -> {
            toggle.setSelected(false);
            en.accept(false);
        });
        box.getChildren().add(reset);
    }

    @FunctionalInterface
    private interface DoubleConsumer {
        void accept(double v);
    }

    private void initDefaultSlices() {
        slices.clear();
        if (audioService.getProject().getMasterBuffer() != null) {
            long frames = audioService.getProject().getMasterBuffer().getFrameCount();
            if (frames > 0) {
                slices.add(new TimeSelection(0, frames));
            }
        }
        refreshSlicesUI();
    }

    @FXML
    private void handleAutoSlice() {
        AudioBuffer buf = audioService.getProject().getMasterBuffer();
        if (buf == null || buf.getFrameCount() == 0) {
            statusLabel.setText("Aucune piste chargée");
            return;
        }
        long total = buf.getFrameCount();
        slices.clear();
        long step = total / 4;
        for (int i = 0; i < 4; i++) {
            long start = i * step;
            long end = (i == 3) ? total : (i + 1) * step;
            slices.add(new TimeSelection(start, end));
        }
        statusLabel.setText("Découpé automatiquement en 4 tranches égales");
        refreshSlicesUI();
    }

    @FXML
    private void handleClearSlices() {
        initDefaultSlices();
        statusLabel.setText("Tranches réinitialisées");
    }

    private void playSlice(TimeSelection slice) {
        StudioProject p = audioService.getProject();
        p.setPlayheadFrame(slice.getStartSample());
        p.setSelection(slice);
        refreshTimeline(false);
        
        playback.stop();
        AudioBuffer playBuf = audioService.getPlaybackBuffer();
        playback.play(playBuf, slice.getStartSample());
        updateTransportState(true);
        statusLabel.setText("Lecture de la tranche sélectionnée…");
    }

    private void deleteSlice(int index) {
        if (index < 0 || index >= slices.size()) {
            return;
        }
        TimeSelection slice = slices.get(index);
        long len = slice.length();
        
        audioService.commitEdit(() -> {
            AudioBuffer out = AudioEditOperations.deleteRegion(
                    audioService.getProject().getMasterBuffer(), slice);
            audioService.getProject().setMasterBuffer(out);
        });
        
        slices.remove(index);
        
        for (int i = index; i < slices.size(); i++) {
            TimeSelection s = slices.get(i);
            slices.set(i, new TimeSelection(
                    Math.max(0, s.getStartSample() - len),
                    Math.max(0, s.getEndSample() - len)
            ));
        }
        
        afterEdit();
        refreshSlicesUI();
        statusLabel.setText("Tranche supprimée");
    }

    private void refreshSlicesUI() {
        if (slicesListVBox == null) {
            return;
        }
        slicesListVBox.getChildren().clear();
        if (waveformPane != null) {
            waveformPane.setSlices(slices);
        }
        if (slices.isEmpty()) {
            Label label = new Label("Aucune tranche disponible.");
            label.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;");
            slicesListVBox.getChildren().add(label);
            return;
        }
        
        AudioBuffer buf = audioService.getProject().getMasterBuffer();
        int rate = buf != null ? buf.getSampleRate() : 44100;
        
        for (int i = 0; i < slices.size(); i++) {
            TimeSelection slice = slices.get(i);
            String startStr = formatFrames(slice.getStartSample(), rate);
            String endStr = formatFrames(slice.getEndSample(), rate);
            
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: #1e293b; -fx-padding: 8 12; -fx-background-radius: 6; -fx-border-color: #334155; -fx-border-radius: 6;");
            
            Label label = new Label("Tranche " + (i + 1) + "\n" + startStr + " - " + endStr);
            label.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-font-size: 11px;");
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            Button playBtn = new Button("▶");
            playBtn.setFocusTraversable(false);
            playBtn.getStyleClass().add("studio-btn");
            playBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-min-width: 32; -fx-pref-width: 32;");
            playBtn.setOnAction(ev -> playSlice(slice));
            
            Button deleteBtn = new Button("🗑");
            deleteBtn.setFocusTraversable(false);
            deleteBtn.getStyleClass().add("studio-btn");
            deleteBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-min-width: 32; -fx-pref-width: 32;");
            final int idx = i;
            deleteBtn.setOnAction(ev -> deleteSlice(idx));
            
            row.getChildren().addAll(label, spacer, playBtn, deleteBtn);
            slicesListVBox.getChildren().add(row);
        }
    }

    private static String formatFrames(long frames, int rate) {
        if (rate <= 0) {
            return "00:00.000";
        }
        double sec = frames / (double) rate;
        int m = (int) (sec / 60);
        double s = sec % 60;
        return String.format(Locale.ROOT, "%02d:%05.2f", m, s);
    }
}
