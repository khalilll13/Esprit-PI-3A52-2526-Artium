package audio.model;

/**
 * Downsampled min/max peaks for waveform rendering.
 */
public final class WaveformPeaks {
    private final float[] mins;
    private final float[] maxs;
    private final long totalFrames;

    public WaveformPeaks(float[] mins, float[] maxs, long totalFrames) {
        this.mins = mins;
        this.maxs = maxs;
        this.totalFrames = totalFrames;
    }

    public float[] getMins() {
        return mins;
    }

    public float[] getMaxs() {
        return maxs;
    }

    public long getTotalFrames() {
        return totalFrames;
    }

    public int getBucketCount() {
        return mins.length;
    }
}
