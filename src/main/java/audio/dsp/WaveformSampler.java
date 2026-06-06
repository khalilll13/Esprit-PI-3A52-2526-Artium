package audio.dsp;

import audio.model.AudioBuffer;
import audio.model.WaveformPeaks;

public final class WaveformSampler {
    private WaveformSampler() {
    }

    public static WaveformPeaks compute(AudioBuffer buffer, int bucketCount) {
        if (buffer == null || buffer.getFrameCount() == 0) {
            return new WaveformPeaks(new float[0], new float[0], 0);
        }
        int buckets = Math.max(64, Math.min(8192, bucketCount));
        long frames = buffer.getFrameCount();
        int ch = buffer.getChannels();
        float[] samples = buffer.getSamples();
        float[] mins = new float[buckets];
        float[] maxs = new float[buckets];
        for (int b = 0; b < buckets; b++) {
            long start = b * frames / buckets;
            long end = (b + 1) * frames / buckets;
            float min = 0f;
            float max = 0f;
            for (long f = start; f < end; f++) {
                float v = 0f;
                for (int c = 0; c < ch; c++) {
                    v += samples[(int) (f * ch + c)];
                }
                v /= ch;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            mins[b] = min;
            maxs[b] = max;
        }
        return new WaveformPeaks(mins, maxs, frames);
    }
}
