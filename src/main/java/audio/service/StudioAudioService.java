package audio.service;

import audio.dsp.*;
import audio.model.*;
import javafx.concurrent.Task;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facade : chargement, édition, effets, mixage, export.
 */
public final class StudioAudioService {
    private static final Logger LOG = Logger.getLogger(StudioAudioService.class.getName());

    private final StudioProject project = new StudioProject();
    private final StudioEditHistory history = new StudioEditHistory();

    public StudioProject getProject() {
        return project;
    }

    public StudioEditHistory getHistory() {
        return history;
    }

    public void loadFile(File file, Runnable onSuccess, Consumer<String> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AudioBuffer buf = AudioIO.load(file.toPath());
                if (buf.getChannels() == 1) {
                    buf = buf.resampleTo(buf.getSampleRate(), 2);
                }
                project.setSourcePath(file.toPath());
                project.setPrimaryBuffer(buf, file.getName());
                project.setPlayheadFrame(0);
                project.setSelection(null);
                history.clear();

                Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "artium-studio");
                Files.createDirectories(workDir);
                Path work = workDir.resolve(stripExt(file.getName()) + "_session.wav");
                AudioIO.saveWav(buf, work);
                project.setWorkingPath(work);

                LOG.log(Level.INFO, "Studio chargé : {0} ({1} frames)",
                        new Object[]{file.getName(), buf.getFrameCount()});
                return null;
            }
        };
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            LOG.log(Level.SEVERE, "Chargement échoué", ex);
            if (onError != null) {
                onError.accept(ex != null ? ex.getMessage() : "Erreur de chargement");
            }
        });
        task.setOnSucceeded(e -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
        Thread t = new Thread(task, "studio-load");
        t.setDaemon(true);
        t.start();
    }

    public void syncWorkingFile() {
        try {
            Path work = project.getWorkingPath();
            if (work == null) {
                work = Path.of(System.getProperty("java.io.tmpdir"), "artium-studio",
                        UUID.randomUUID() + "_session.wav");
                project.setWorkingPath(work);
            }
            Files.createDirectories(work.getParent());
            AudioIO.saveWav(project.getMasterBuffer(), work);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Sync working file", e);
        }
    }

    public void importTrackAsync(File file, Runnable onDone, Consumer<String> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AudioBuffer buf = AudioIO.load(file.toPath());
                project.getTracks().add(new StudioTrack(
                        UUID.randomUUID().toString(), file.getName(), buf));
                return null;
            }
        };
        task.setOnFailed(e -> {
            if (onError != null) {
                onError.accept(task.getException() != null ? task.getException().getMessage() : "Import échoué");
            }
        });
        task.setOnSucceeded(e -> {
            if (onDone != null) {
                onDone.run();
            }
        });
        new Thread(task, "studio-import").start();
    }

    public WaveformPeaks buildPeaks(int width) {
        return WaveformSampler.compute(project.getMasterBuffer(), Math.max(256, width));
    }

    /** Buffer avec effets pour lecture et affichage waveform. */
    public AudioBuffer getPlaybackBuffer() {
        AudioBuffer raw = project.getMasterBuffer();
        if (raw.getFrameCount() == 0) {
            return raw;
        }
        EffectChainSettings chain = project.getMasterEffects();
        AudioBuffer processed = AudioEffects.process(raw, chain);
        StudioTrack t = project.getPrimaryTrack();
        if (t != null && Math.abs(t.getVolume() - 1.0) > 0.001) {
            float[] s = processed.getSamples().clone();
            float vol = (float) t.getVolume();
            for (int i = 0; i < s.length; i++) {
                s[i] *= vol;
            }
            processed = new AudioBuffer(s, processed.getSampleRate(), processed.getChannels());
        }
        return processed;
    }

    public void commitEdit(Runnable edit) {
        history.push(project);
        edit.run();
        syncWorkingFile();
    }

    /** Applique définitivement les effets sur la piste principale. */
    public void applyEffectsToMaster(EffectChainSettings fx) {
        commitEdit(() -> {
            AudioBuffer processed = AudioEffects.process(project.getMasterBuffer(), fx);
            project.setMasterBuffer(processed);
            resetEffectSettings(fx);
            if (project.getPrimaryTrack() != null) {
                project.getPrimaryTrack().setBuffer(processed);
                resetEffectSettings(project.getPrimaryTrack().getEffects());
            }
            resetEffectSettings(project.getMasterEffects());
        });
    }

    private void resetEffectSettings(EffectChainSettings fx) {
        fx.eqEnabled = false;
        fx.compressorEnabled = false;
        fx.limiterEnabled = false;
        fx.reverbEnabled = false;
        fx.delayEnabled = false;
        fx.noiseReductionEnabled = false;
        fx.pitchSemitones = 0;
        fx.speed = 1.0;
        fx.pan = 0;
        fx.balance = 0;
    }

    public AudioAnalysis.AnalysisReport analyze() {
        return AudioAnalysis.analyze(getPlaybackBuffer());
    }

    public AudioBuffer mixdown() {
        var tracks = project.getTracks();
        if (tracks.isEmpty()) {
            return AudioEffects.process(project.getMasterBuffer(), project.getMasterEffects());
        }
        boolean anySolo = tracks.stream().anyMatch(StudioTrack::isSolo);
        int rate = project.getMasterBuffer().getSampleRate();
        int ch = 2;
        long maxFrames = project.getTotalFrames();
        if (maxFrames == 0) {
            return AudioBuffer.silence(rate, ch, 0);
        }
        float[] mix = new float[(int) (maxFrames * ch)];
        for (StudioTrack t : tracks) {
            if (t.isMuted() || (anySolo && !t.isSolo())) {
                continue;
            }
            AudioBuffer buf = AudioEffects.process(t.getBuffer(), t.getEffects());
            float[] s = buf.getSamples();
            int tch = buf.getChannels();
            int trackRate = buf.getSampleRate();
            if (trackRate != rate) {
                buf = buf.resampleTo(rate, tch);
                s = buf.getSamples();
            }
            long offset = t.getTimelineOffsetFrames();
            double vol = t.getVolume();
            double pan = t.getPan();
            for (long f = 0; f < buf.getFrameCount(); f++) {
                long outF = f + offset;
                if (outF >= maxFrames) {
                    break;
                }
                for (int c = 0; c < ch; c++) {
                    int srcC = Math.min(c, tch - 1);
                    float sample = s[(int) (f * tch + srcC)] * (float) vol;
                    if (ch == 2) {
                        sample *= (float) (c == 0
                                ? (pan <= 0 ? 1 : 1 - pan)
                                : (pan >= 0 ? 1 : 1 + pan));
                    }
                    int idx = (int) (outF * ch + c);
                    if (idx < mix.length) {
                        mix[idx] += sample;
                    }
                }
            }
        }
        return AudioEffects.process(new AudioBuffer(mix, rate, ch), project.getMasterEffects());
    }

    public void exportAsync(Path out, ExportConfig config, Consumer<Double> progress,
                            Consumer<String> onError, Consumer<Path> onSuccess) {
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                updateProgress(0.1, 1);
                AudioBuffer buf = mixdown();
                var m = project.getMastering();
                if (m.normalize || config.getQuality() != null) {
                    buf = AudioEffects.normalize(buf, 0.98);
                }
                if (m.limiterEnabled) {
                    buf = AudioEffects.limiter(buf, m.limiterCeilingDb);
                }
                buf = switch (m.preset) {
                    case "podcast" -> AudioEffects.loudnessOptimize(buf, -16);
                    case "streaming" -> AudioEffects.loudnessOptimize(buf, -14);
                    default -> AudioEffects.loudnessOptimize(buf, m.targetLufs);
                };
                if (config.getSampleRate() > 0 && config.getSampleRate() != buf.getSampleRate()) {
                    buf = buf.resampleTo(config.getSampleRate(), config.getChannels());
                }
                updateProgress(0.6, 1);
                AudioIO.save(out, buf, config.getFormat(), config.getBitrateKbps());
                updateProgress(1, 1);
                project.setWorkingPath(out);
                return out;
            }
        };
        task.progressProperty().addListener((o, a, b) -> {
            if (progress != null) {
                progress.accept(b.doubleValue());
            }
        });
        task.setOnFailed(e -> {
            if (onError != null) {
                onError.accept(task.getException() != null ? task.getException().getMessage() : "Export échoué");
            }
        });
        task.setOnSucceeded(e -> {
            if (onSuccess != null) {
                onSuccess.accept(task.getValue());
            }
        });
        new Thread(task, "studio-export").start();
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
