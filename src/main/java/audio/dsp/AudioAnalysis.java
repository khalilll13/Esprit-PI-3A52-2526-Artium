package audio.dsp;

import audio.model.AudioBuffer;

import java.util.ArrayList;
import java.util.List;

public final class AudioAnalysis {
    private AudioAnalysis() {
    }

    public static class AnalysisReport {
        public float peakDb;
        public float rmsDb;
        public float estimatedLufs;
        public boolean clippingDetected;
        public int clipCount;
        public float dynamicRangeDb;
        public List<SilentRegion> silentRegions = new ArrayList<>();
        public float[] spectrum = new float[0];
    }

    public record SilentRegion(long startFrame, long endFrame, double startSec, double endSec) {
    }

    public static AnalysisReport analyze(AudioBuffer buf) {
        AnalysisReport r = new AnalysisReport();
        if (buf == null || buf.getFrameCount() == 0) {
            return r;
        }
        float peak = buf.peakLevel();
        float rms = buf.rmsLevel();
        r.peakDb = (float) (20 * Math.log10(Math.max(peak, 1e-9)));
        r.rmsDb = (float) (20 * Math.log10(Math.max(rms, 1e-9)));
        r.estimatedLufs = r.rmsDb - 3;
        float[] s = buf.getSamples();
        int clips = 0;
        float min = 1f;
        float max = 0f;
        for (float v : s) {
            float a = Math.abs(v);
            if (a >= 0.999f) {
                clips++;
            }
            if (a > max) {
                max = a;
            }
            if (a < min && a > 1e-6) {
                min = a;
            }
        }
        r.clipCount = clips;
        r.clippingDetected = clips > 0;
        r.dynamicRangeDb = (float) (20 * Math.log10(Math.max(max, 1e-9) / Math.max(min, 1e-9)));
        r.silentRegions = detectSilence(buf, -45);
        r.spectrum = computeSpectrum(buf, 128);
        return r;
    }

    public static List<SilentRegion> detectSilence(AudioBuffer buf, double thresholdDb) {
        List<SilentRegion> regions = new ArrayList<>();
        float thresh = (float) Math.pow(10, thresholdDb / 20.0);
        int ch = buf.getChannels();
        float[] s = buf.getSamples();
        long frames = buf.getFrameCount();
        long start = -1;
        for (long f = 0; f < frames; f++) {
            float peak = 0;
            for (int c = 0; c < ch; c++) {
                peak = Math.max(peak, Math.abs(s[(int) (f * ch + c)]));
            }
            if (peak < thresh) {
                if (start < 0) {
                    start = f;
                }
            } else if (start >= 0) {
                if (f - start > buf.getSampleRate() * 0.3) {
                    regions.add(new SilentRegion(start, f, start / (double) buf.getSampleRate(),
                            f / (double) buf.getSampleRate()));
                }
                start = -1;
            }
        }
        return regions;
    }

    public static float[] computeSpectrum(AudioBuffer buf, int bins) {
        long frames = Math.min(buf.getFrameCount(), 8192);
        int ch = buf.getChannels();
        float[] s = buf.getSamples();
        float[] spec = new float[bins];
        for (int b = 0; b < bins; b++) {
            double sum = 0;
            for (long f = 0; f < frames; f++) {
                float v = 0;
                for (int c = 0; c < ch; c++) {
                    v += s[(int) (f * ch + c)];
                }
                sum += v * Math.sin(2 * Math.PI * b * f / bins);
            }
            spec[b] = (float) Math.abs(sum / frames);
        }
        return spec;
    }

    public static float[][] computeSpectrogram(AudioBuffer buf, int timeSteps, int freqBins) {
        float[][] grid = new float[timeSteps][freqBins];
        long frames = buf.getFrameCount();
        int ch = buf.getChannels();
        float[] s = buf.getSamples();
        long window = Math.max(256, frames / timeSteps);
        for (int t = 0; t < timeSteps; t++) {
            long start = t * frames / timeSteps;
            for (int f = 0; f < freqBins; f++) {
                double sum = 0;
                for (long i = 0; i < window && start + i < frames; i++) {
                    float v = s[(int) ((start + i) * ch)];
                    sum += v * Math.sin(Math.PI * f * i / freqBins);
                }
                grid[t][f] = (float) Math.log10(1 + Math.abs(sum / window));
            }
        }
        return grid;
    }
}
