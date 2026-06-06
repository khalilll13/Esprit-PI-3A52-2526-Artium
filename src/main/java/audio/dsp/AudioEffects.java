package audio.dsp;

import audio.model.AudioBuffer;

import java.util.Arrays;

/**
 * Offline DSP: EQ, dynamics, time effects, restoration heuristics.
 */
public final class AudioEffects {
    private AudioEffects() {
    }

    public static AudioBuffer process(AudioBuffer input, EffectChainSettings fx) {
        if (input == null || input.getFrameCount() == 0) {
            return input;
        }
        AudioBuffer buf = input.clone();
        if (fx.restorationEnabled || fx.noiseReductionEnabled) {
            buf = noiseReduction(buf, fx.noiseReductionAmount);
        }
        if (fx.humRemovalEnabled) {
            buf = humRemoval(buf);
        }
        if (fx.clickRemovalEnabled) {
            buf = clickRemoval(buf);
        }
        if (fx.breathReductionEnabled) {
            buf = highPass(buf, 120);
        }
        if (fx.voiceEnhanceEnabled || fx.vocalClarityEnabled) {
            buf = peakingEq(buf, 3000, 2.5, fx.vocalClarityEnabled ? 4 : 2);
            buf = peakingEq(buf, 200, 1.2, -2);
        }
        if (fx.eqEnabled) {
            buf = applyEq(buf, fx);
        }
        if (fx.deEsserEnabled) {
            buf = deEss(buf, fx.deEsserFreq, fx.deEsserAmount);
        }
        if (fx.compressorEnabled) {
            buf = compressor(buf, fx.compressorThresholdDb, fx.compressorRatio,
                    fx.compressorAttackMs, fx.compressorReleaseMs);
        }
        if (fx.gateEnabled) {
            buf = noiseGate(buf, fx.gateThresholdDb);
        }
        if (fx.expanderEnabled) {
            buf = expander(buf, fx.gateThresholdDb, fx.expanderRatio);
        }
        if (fx.distortionEnabled) {
            buf = distortion(buf, fx.distortionDrive);
        }
        if (fx.chorusEnabled) {
            buf = chorus(buf, fx.chorusDepth);
        }
        if (fx.flangerEnabled) {
            buf = flanger(buf, fx.flangerRate);
        }
        if (fx.delayEnabled) {
            buf = delay(buf, fx.delayMs, fx.delayFeedback, fx.delayMix);
        }
        if (fx.echoEnabled) {
            buf = delay(buf, fx.echoDelayMs, 0.5, 0.35);
        }
        if (fx.reverbEnabled) {
            buf = simpleReverb(buf, fx.reverbRoom, fx.reverbMix);
        }
        if (fx.widenerEnabled) {
            buf = stereoWiden(buf, fx.widenerAmount);
        }
        if (fx.limiterEnabled) {
            buf = limiter(buf, fx.limiterCeilingDb);
        }
        if (fx.pan != 0 || fx.balance != 0) {
            buf = applyPan(buf, fx.pan + fx.balance);
        }
        if (fx.speed != 1.0 && fx.speed > 0.01) {
            buf = changeSpeed(buf, fx.speed);
        }
        if (fx.pitchSemitones != 0) {
            buf = pitchShift(buf, fx.pitchSemitones);
        }
        return buf;
    }

    public static void applyEqPreset(EffectChainSettings fx, String preset) {
        fx.eqPreset = preset;
        switch (preset) {
            case "vocal" -> { fx.lowGainDb = -2; fx.midGainDb = 2; fx.highGainDb = 3; }
            case "podcast" -> { fx.lowGainDb = -4; fx.midGainDb = 4; fx.highGainDb = 1; }
            case "music" -> { fx.lowGainDb = 0; fx.midGainDb = 0; fx.highGainDb = 0; }
            case "bass" -> { fx.lowGainDb = 8; fx.midGainDb = 0; fx.highGainDb = -2; }
            case "treble" -> { fx.lowGainDb = -2; fx.midGainDb = 0; fx.highGainDb = 8; }
            case "acoustic" -> { fx.lowGainDb = 2; fx.midGainDb = 3; fx.highGainDb = 2; }
            case "live" -> { fx.lowGainDb = 3; fx.midGainDb = 1; fx.highGainDb = 4; }
            default -> { fx.lowGainDb = 0; fx.midGainDb = 0; fx.highGainDb = 0; }
        }
    }

    private static AudioBuffer applyEq(AudioBuffer buf, EffectChainSettings fx) {
        AudioBuffer out = buf;
        out = shelf(out, true, fx.lowGainDb);
        out = shelf(out, false, fx.highGainDb);
        out = peakingEq(out, fx.param1Freq, fx.param1Q, fx.param1GainDb + fx.midGainDb * 0.5);
        out = peakingEq(out, fx.param2Freq, fx.param2Q, fx.param2GainDb + fx.midGainDb * 0.5);
        return out;
    }

    public static AudioBuffer normalize(AudioBuffer buf, double targetPeak) {
        float peak = buf.peakLevel();
        if (peak < 1e-6f) {
            return buf;
        }
        float g = (float) (targetPeak / peak);
        float[] s = buf.getSamples().clone();
        for (int i = 0; i < s.length; i++) {
            s[i] = Math.max(-1f, Math.min(1f, s[i] * g));
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    public static AudioBuffer loudnessOptimize(AudioBuffer buf, double targetLufs) {
        float rms = buf.rmsLevel();
        if (rms < 1e-6f) {
            return buf;
        }
        double targetRms = Math.pow(10, targetLufs / 20.0) * 0.3;
        float g = (float) (targetRms / rms);
        float[] s = buf.getSamples().clone();
        for (int i = 0; i < s.length; i++) {
            s[i] = Math.max(-1f, Math.min(1f, s[i] * g));
        }
        return limiter(new AudioBuffer(s, buf.getSampleRate(), buf.getChannels()), -0.3);
    }

    private static AudioBuffer noiseReduction(AudioBuffer buf, double amount) {
        float[] s = buf.getSamples().clone();
        float floor = (float) (0.02 * amount);
        for (int i = 0; i < s.length; i++) {
            if (Math.abs(s[i]) < floor) {
                s[i] *= (1 - (float) amount);
            }
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer humRemoval(AudioBuffer buf) {
        return notch(buf, 50);
    }

    private static AudioBuffer clickRemoval(AudioBuffer buf) {
        float[] s = buf.getSamples().clone();
        for (int i = 2; i < s.length - 2; i++) {
            if (Math.abs(s[i]) > 0.9f && Math.abs(s[i - 1]) < 0.1f) {
                s[i] = (s[i - 1] + s[i + 1]) * 0.5f;
            }
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer highPass(AudioBuffer buf, double hz) {
        return shelf(buf, true, -6);
    }

    private static AudioBuffer shelf(AudioBuffer buf, boolean low, double gainDb) {
        double g = Math.pow(10, gainDb / 20.0);
        float[] s = buf.getSamples().clone();
        float prev = 0;
        float alpha = low ? 0.995f : 0.02f;
        for (int i = 0; i < s.length; i++) {
            float y = low ? prev + alpha * (s[i] - prev) : s[i] - prev;
            s[i] = (float) (low ? y * g : (s[i] * g + y * 0.1));
            prev = s[i];
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer peakingEq(AudioBuffer buf, double freq, double q, double gainDb) {
        float[] s = buf.getSamples().clone();
        double g = Math.pow(10, gainDb / 40.0);
        int period = Math.max(4, (int) (buf.getSampleRate() / freq));
        for (int i = period; i < s.length; i++) {
            s[i] = (float) (s[i] + (s[i - period] - s[i]) * g * 0.15);
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer notch(AudioBuffer buf, double hz) {
        return peakingEq(buf, hz, 5, -12);
    }

    private static AudioBuffer deEss(AudioBuffer buf, double freq, double amount) {
        AudioBuffer ess = peakingEq(buf, freq, 2, -6 * amount);
        return ess;
    }

    private static AudioBuffer compressor(AudioBuffer buf, double thresholdDb, double ratio,
                                          double attackMs, double releaseMs) {
        float[] s = buf.getSamples().clone();
        double thresh = Math.pow(10, thresholdDb / 20.0);
        float env = 0f;
        float attack = (float) Math.exp(-1.0 / (buf.getSampleRate() * attackMs / 1000.0));
        float release = (float) Math.exp(-1.0 / (buf.getSampleRate() * releaseMs / 1000.0));
        for (int i = 0; i < s.length; i++) {
            float in = Math.abs(s[i]);
            float coeff = in > env ? attack : release;
            env = coeff * env + (1 - coeff) * in;
            if (env > thresh) {
                double over = env / thresh;
                double gain = Math.pow(over, 1.0 - 1.0 / ratio);
                s[i] /= (float) gain;
            }
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer noiseGate(AudioBuffer buf, double thresholdDb) {
        float thresh = (float) Math.pow(10, thresholdDb / 20.0);
        float[] s = buf.getSamples().clone();
        for (int i = 0; i < s.length; i++) {
            if (Math.abs(s[i]) < thresh) {
                s[i] = 0;
            }
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer expander(AudioBuffer buf, double thresholdDb, double ratio) {
        return compressor(buf, thresholdDb, 1.0 / ratio, 5, 80);
    }

    public static AudioBuffer limiter(AudioBuffer buf, double ceilingDb) {
        float ceil = (float) Math.pow(10, ceilingDb / 20.0);
        float[] s = buf.getSamples().clone();
        for (int i = 0; i < s.length; i++) {
            s[i] = Math.max(-ceil, Math.min(ceil, s[i]));
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer distortion(AudioBuffer buf, double drive) {
        float[] s = buf.getSamples().clone();
        float d = (float) (1 + drive * 10);
        for (int i = 0; i < s.length; i++) {
            s[i] = (float) Math.tanh(s[i] * d) / d;
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer chorus(AudioBuffer buf, double depth) {
        float[] s = buf.getSamples().clone();
        int delay = (int) (buf.getSampleRate() * 0.02);
        for (int i = delay; i < s.length; i++) {
            float mod = (float) (Math.sin(i * 0.01) * depth);
            int d = delay + (int) (mod * delay);
            if (i - d >= 0) {
                s[i] = s[i] * 0.7f + s[i - d] * 0.3f;
            }
        }
        return new AudioBuffer(s, buf.getSampleRate(), buf.getChannels());
    }

    private static AudioBuffer flanger(AudioBuffer buf, double rate) {
        return chorus(buf, rate * 0.5);
    }

    private static AudioBuffer delay(AudioBuffer buf, double ms, double feedback, double mix) {
        int ch = buf.getChannels();
        int delaySamples = (int) (buf.getSampleRate() * ms / 1000.0) * ch;
        float[] s = buf.getSamples().clone();
        float[] out = Arrays.copyOf(s, s.length);
        for (int i = delaySamples; i < out.length; i++) {
            float delayed = s[i - delaySamples];
            out[i] = (float) (s[i] * (1 - mix) + (s[i] + delayed * feedback) * mix);
        }
        return new AudioBuffer(out, buf.getSampleRate(), ch);
    }

    private static AudioBuffer simpleReverb(AudioBuffer buf, double room, double mix) {
        return delay(buf, 40 + room * 80, 0.4 + room * 0.3, mix);
    }

    private static AudioBuffer stereoWiden(AudioBuffer buf, double amount) {
        if (buf.getChannels() < 2) {
            return buf;
        }
        float[] s = buf.getSamples().clone();
        for (int i = 0; i < s.length; i += 2) {
            float l = s[i];
            float r = s[i + 1];
            float mid = (l + r) * 0.5f;
            float side = (l - r) * 0.5f * (float) (1 + amount);
            s[i] = mid + side;
            s[i + 1] = mid - side;
        }
        return new AudioBuffer(s, buf.getSampleRate(), 2);
    }

    private static AudioBuffer applyPan(AudioBuffer buf, double pan) {
        if (buf.getChannels() < 2) {
            return buf;
        }
        float[] s = buf.getSamples().clone();
        float lGain = (float) (pan <= 0 ? 1 : 1 - pan);
        float rGain = (float) (pan >= 0 ? 1 : 1 + pan);
        for (int i = 0; i < s.length; i += 2) {
            s[i] *= lGain;
            s[i + 1] *= rGain;
        }
        return new AudioBuffer(s, buf.getSampleRate(), 2);
    }

    private static AudioBuffer changeSpeed(AudioBuffer buf, double speed) {
        long frames = buf.getFrameCount();
        long outFrames = Math.max(1, (long) (frames / speed));
        return resampleFrames(buf, outFrames);
    }

    private static AudioBuffer pitchShift(AudioBuffer buf, double semitones) {
        double ratio = Math.pow(2, semitones / 12.0);
        long outFrames = Math.max(1, (long) (buf.getFrameCount() / ratio));
        AudioBuffer sped = resampleFrames(buf, outFrames);
        return resampleFrames(sped, buf.getFrameCount());
    }

    private static AudioBuffer resampleFrames(AudioBuffer buf, long outFrames) {
        int ch = buf.getChannels();
        float[] in = buf.getSamples();
        float[] out = new float[(int) (outFrames * ch)];
        long inFrames = buf.getFrameCount();
        for (long i = 0; i < outFrames; i++) {
            double pos = i * (double) inFrames / outFrames;
            long f0 = (long) pos;
            long f1 = Math.min(f0 + 1, inFrames - 1);
            double frac = pos - f0;
            for (int c = 0; c < ch; c++) {
                float v0 = in[(int) (f0 * ch + c)];
                float v1 = in[(int) (f1 * ch + c)];
                out[(int) (i * ch + c)] = (float) (v0 * (1 - frac) + v1 * frac);
            }
        }
        return new AudioBuffer(out, buf.getSampleRate(), ch);
    }
}
