package audio.model;

import audio.dsp.EffectChainSettings;

/**
 * One track lane in the multi-track timeline.
 */
public final class StudioTrack {
    private final String id;
    private String name;
    private AudioBuffer buffer;
    private long timelineOffsetFrames;
    private double volume = 1.0;
    private double pan = 0.0;
    private boolean muted;
    private boolean solo;
    private final EffectChainSettings effects = new EffectChainSettings();

    public StudioTrack(String id, String name, AudioBuffer buffer) {
        this.id = id;
        this.name = name;
        this.buffer = buffer;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AudioBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(AudioBuffer buffer) {
        this.buffer = buffer;
    }

    public long getTimelineOffsetFrames() {
        return timelineOffsetFrames;
    }

    public void setTimelineOffsetFrames(long timelineOffsetFrames) {
        this.timelineOffsetFrames = Math.max(0, timelineOffsetFrames);
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0, Math.min(2, volume));
    }

    public double getPan() {
        return pan;
    }

    public void setPan(double pan) {
        this.pan = Math.max(-1, Math.min(1, pan));
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isSolo() {
        return solo;
    }

    public void setSolo(boolean solo) {
        this.solo = solo;
    }

    public EffectChainSettings getEffects() {
        return effects;
    }
}
