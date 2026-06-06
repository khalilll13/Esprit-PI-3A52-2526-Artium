package audio.model;

import audio.dsp.EffectChainSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Root editing session: master bus + timeline tracks + clipboard.
 */
public final class StudioProject {
    private final List<StudioTrack> tracks = new ArrayList<>();
    private AudioBuffer clipboard;
    private TimeSelection selection;
    private long playheadFrame;
    private double zoom = 1.0;
    private Path sourcePath;
    private Path workingPath;
    private final EffectChainSettings masterEffects = new EffectChainSettings();
    private final MasteringSettings mastering = new MasteringSettings();

    public StudioProject() {
    }

    public List<StudioTrack> getTracks() {
        return tracks;
    }

    public StudioTrack getPrimaryTrack() {
        return tracks.isEmpty() ? null : tracks.get(0);
    }

    public void setPrimaryBuffer(AudioBuffer buffer, String name) {
        tracks.clear();
        tracks.add(new StudioTrack(UUID.randomUUID().toString(), name, buffer));
    }

    public AudioBuffer getMasterBuffer() {
        if (tracks.isEmpty()) {
            return AudioBuffer.silence(44100, 2, 0);
        }
        return tracks.get(0).getBuffer();
    }

    public void setMasterBuffer(AudioBuffer buffer) {
        if (tracks.isEmpty()) {
            tracks.add(new StudioTrack(UUID.randomUUID().toString(), "Track 1", buffer));
        } else {
            tracks.get(0).setBuffer(buffer);
        }
    }

    public AudioBuffer getClipboard() {
        return clipboard;
    }

    public void setClipboard(AudioBuffer clipboard) {
        this.clipboard = clipboard;
    }

    public TimeSelection getSelection() {
        return selection;
    }

    public void setSelection(TimeSelection selection) {
        this.selection = selection;
    }

    public long getPlayheadFrame() {
        return playheadFrame;
    }

    public void setPlayheadFrame(long playheadFrame) {
        this.playheadFrame = Math.max(0, playheadFrame);
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.25, Math.min(64, zoom));
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public Path getWorkingPath() {
        return workingPath;
    }

    public void setWorkingPath(Path workingPath) {
        this.workingPath = workingPath;
    }

    public EffectChainSettings getMasterEffects() {
        return masterEffects;
    }

    public MasteringSettings getMastering() {
        return mastering;
    }

    public long getTotalFrames() {
        long max = 0;
        for (StudioTrack t : tracks) {
            if (t.getBuffer() != null) {
                max = Math.max(max, t.getTimelineOffsetFrames() + t.getBuffer().getFrameCount());
            }
        }
        return max;
    }

    public static final class MasteringSettings {
        public boolean normalize = false;
        public double targetLufs = -14;
        public String preset = "music";
        public boolean limiterEnabled = true;
        public double limiterCeilingDb = -0.3;
    }
}
