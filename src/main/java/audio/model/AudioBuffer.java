package audio.model;

import java.util.Arrays;

/**
 * In-memory PCM audio (interleaved stereo or mono floats, -1..1).
 */
public final class AudioBuffer implements Cloneable {
    private float[] samples;
    private int sampleRate;
    private int channels;

    public AudioBuffer(float[] samples, int sampleRate, int channels) {
        this.samples = samples != null ? samples : new float[0];
        this.sampleRate = Math.max(8000, sampleRate);
        this.channels = Math.max(1, Math.min(2, channels));
    }

    public static AudioBuffer silence(int sampleRate, int channels, long frameCount) {
        int ch = Math.max(1, Math.min(2, channels));
        return new AudioBuffer(new float[(int) (frameCount * ch)], sampleRate, ch);
    }

    public float[] getSamples() {
        return samples;
    }

    public void setSamples(float[] samples) {
        this.samples = samples != null ? samples : new float[0];
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = Math.max(8000, sampleRate);
    }

    public int getChannels() {
        return channels;
    }

    public long getFrameCount() {
        return channels > 0 ? samples.length / channels : 0;
    }

    public double getDurationSeconds() {
        return sampleRate > 0 ? (double) getFrameCount() / sampleRate : 0;
    }

    public AudioBuffer clone() {
        return new AudioBuffer(Arrays.copyOf(samples, samples.length), sampleRate, channels);
    }

    public AudioBuffer subBuffer(long startFrame, long endFrame) {
        long frames = getFrameCount();
        startFrame = Math.max(0, Math.min(startFrame, frames));
        endFrame = Math.max(startFrame, Math.min(endFrame, frames));
        int len = (int) ((endFrame - startFrame) * channels);
        float[] sub = new float[len];
        System.arraycopy(samples, (int) (startFrame * channels), sub, 0, len);
        return new AudioBuffer(sub, sampleRate, channels);
    }

    public void append(AudioBuffer other) {
        if (other == null || other.getFrameCount() == 0) {
            return;
        }
        if (channels != other.channels || sampleRate != other.sampleRate) {
            other = other.resampleTo(sampleRate, channels);
        }
        float[] merged = Arrays.copyOf(samples, samples.length + other.samples.length);
        System.arraycopy(other.samples, 0, merged, samples.length, other.samples.length);
        samples = merged;
    }

    public AudioBuffer resampleTo(int targetRate, int targetChannels) {
        if (targetRate == sampleRate && targetChannels == channels) {
            return clone();
        }
        // Simple linear resample + channel mix/duplicate
        double ratio = (double) targetRate / sampleRate;
        long outFrames = Math.max(1, (long) (getFrameCount() * ratio));
        float[] out = new float[(int) (outFrames * targetChannels)];
        for (long i = 0; i < outFrames; i++) {
            double srcPos = i / ratio;
            long f0 = (long) srcPos;
            long f1 = Math.min(f0 + 1, getFrameCount() - 1);
            double frac = srcPos - f0;
            for (int c = 0; c < targetChannels; c++) {
                int srcCh = Math.min(c, channels - 1);
                float v0 = sampleAtFrame(f0, srcCh);
                float v1 = sampleAtFrame(f1, srcCh);
                out[(int) (i * targetChannels + c)] = (float) (v0 * (1 - frac) + v1 * frac);
            }
        }
        return new AudioBuffer(out, targetRate, targetChannels);
    }

    private float sampleAtFrame(long frame, int channel) {
        if (frame < 0 || frame >= getFrameCount()) {
            return 0f;
        }
        return samples[(int) (frame * channels + channel)];
    }

    public float peakLevel() {
        float peak = 0f;
        for (float s : samples) {
            float a = Math.abs(s);
            if (a > peak) {
                peak = a;
            }
        }
        return peak;
    }

    public float rmsLevel() {
        if (samples.length == 0) {
            return 0f;
        }
        double sum = 0;
        for (float s : samples) {
            sum += s * s;
        }
        return (float) Math.sqrt(sum / samples.length);
    }
}
