package audio.model;

/**
 * Sample-accurate selection range on the timeline.
 */
public final class TimeSelection {
    private final long startSample;
    private final long endSample;

    public TimeSelection(long startSample, long endSample) {
        if (endSample < startSample) {
            long t = startSample;
            startSample = endSample;
            endSample = t;
        }
        this.startSample = startSample;
        this.endSample = endSample;
    }

    public long getStartSample() {
        return startSample;
    }

    public long getEndSample() {
        return endSample;
    }

    public long length() {
        return endSample - startSample;
    }

    public boolean isEmpty() {
        return endSample <= startSample;
    }

    public boolean contains(long sample) {
        return sample >= startSample && sample < endSample;
    }
}
