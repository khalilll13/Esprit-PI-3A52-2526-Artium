package audio.dsp;

import audio.model.AudioBuffer;
import audio.model.TimeSelection;

import java.util.Arrays;

public final class AudioEditOperations {
    private AudioEditOperations() {
    }

    public static AudioBuffer cut(AudioBuffer buf, TimeSelection sel) {
        if (sel == null || sel.isEmpty()) {
            return buf.clone();
        }
        AudioBuffer copy = buf.clone();
        copy.setSamples(removeRange(copy.getSamples(), copy.getChannels(), sel));
        return copy;
    }

    public static AudioBuffer copyRegion(AudioBuffer buf, TimeSelection sel) {
        if (sel == null || sel.isEmpty()) {
            return AudioBuffer.silence(buf.getSampleRate(), buf.getChannels(), 0);
        }
        return buf.subBuffer(sel.getStartSample(), sel.getEndSample());
    }

    public static AudioBuffer paste(AudioBuffer buf, AudioBuffer clip, long atFrame) {
        AudioBuffer out = buf.clone();
        long frames = out.getFrameCount();
        int ch = out.getChannels();
        if (clip.getChannels() != ch) {
            clip = clip.resampleTo(out.getSampleRate(), ch);
        }
        long newFrames = Math.max(frames, atFrame + clip.getFrameCount());
        float[] merged = new float[(int) (newFrames * ch)];
        System.arraycopy(out.getSamples(), 0, merged, 0, out.getSamples().length);
        float[] clipSamples = clip.getSamples();
        int dest = (int) (atFrame * ch);
        System.arraycopy(clipSamples, 0, merged, dest, clipSamples.length);
        out.setSamples(merged);
        return out;
    }

    public static AudioBuffer deleteRegion(AudioBuffer buf, TimeSelection sel) {
        return cut(buf, sel);
    }

    public static AudioBuffer trimStart(AudioBuffer buf, long frame) {
        return buf.subBuffer(frame, buf.getFrameCount());
    }

    public static AudioBuffer trimEnd(AudioBuffer buf, long frame) {
        return buf.subBuffer(0, frame);
    }

    public static AudioBuffer[] split(AudioBuffer buf, long frame) {
        return new AudioBuffer[]{
                buf.subBuffer(0, frame),
                buf.subBuffer(frame, buf.getFrameCount())
        };
    }

    public static AudioBuffer merge(AudioBuffer a, AudioBuffer b) {
        AudioBuffer out = a.clone();
        if (a.getChannels() != b.getChannels() || a.getSampleRate() != b.getSampleRate()) {
            b = b.resampleTo(a.getSampleRate(), a.getChannels());
        }
        out.append(b);
        return out;
    }

    public static AudioBuffer silenceRegion(AudioBuffer buf, TimeSelection sel) {
        AudioBuffer out = buf.clone();
        float[] s = out.getSamples();
        int ch = out.getChannels();
        for (long f = sel.getStartSample(); f < sel.getEndSample(); f++) {
            for (int c = 0; c < ch; c++) {
                s[(int) (f * ch + c)] = 0f;
            }
        }
        return out;
    }

    public static AudioBuffer reverse(AudioBuffer buf) {
        AudioBuffer out = buf.clone();
        int ch = out.getChannels();
        long frames = out.getFrameCount();
        float[] s = out.getSamples();
        float[] r = new float[s.length];
        for (long f = 0; f < frames; f++) {
            long src = frames - 1 - f;
            System.arraycopy(s, (int) (src * ch), r, (int) (f * ch), ch);
        }
        out.setSamples(r);
        return out;
    }

    public static AudioBuffer fadeIn(AudioBuffer buf, TimeSelection sel) {
        return applyFade(buf, sel, true);
    }

    public static AudioBuffer fadeOut(AudioBuffer buf, TimeSelection sel) {
        return applyFade(buf, sel, false);
    }

    public static AudioBuffer crossfade(AudioBuffer a, AudioBuffer b, int overlapFrames) {
        if (a.getSampleRate() != b.getSampleRate() || a.getChannels() != b.getChannels()) {
            b = b.resampleTo(a.getSampleRate(), a.getChannels());
        }
        int ch = a.getChannels();
        long aFrames = a.getFrameCount();
        overlapFrames = (int) Math.min(overlapFrames, Math.min(aFrames, b.getFrameCount()));
        long outFrames = aFrames + b.getFrameCount() - overlapFrames;
        float[] out = new float[(int) (outFrames * ch)];
        System.arraycopy(a.getSamples(), 0, out, 0, a.getSamples().length);
        float[] bs = b.getSamples();
        for (int i = 0; i < overlapFrames; i++) {
            float t = i / (float) overlapFrames;
            long frame = aFrames - overlapFrames + i;
            for (int c = 0; c < ch; c++) {
                int idx = (int) (frame * ch + c);
                out[idx] = out[idx] * (1 - t) + bs[i * ch + c] * t;
            }
        }
        System.arraycopy(bs, overlapFrames * ch, out, (int) ((aFrames - overlapFrames) * ch),
                bs.length - overlapFrames * ch);
        return new AudioBuffer(out, a.getSampleRate(), ch);
    }

    private static AudioBuffer applyFade(AudioBuffer buf, TimeSelection sel, boolean fadeIn) {
        AudioBuffer out = buf.clone();
        float[] s = out.getSamples();
        int ch = out.getChannels();
        long len = sel.length();
        for (long i = 0; i < len; i++) {
            float g = (i + 1) / (float) len;
            if (!fadeIn) {
                g = 1f - g;
            }
            long f = sel.getStartSample() + i;
            for (int c = 0; c < ch; c++) {
                int idx = (int) (f * ch + c);
                if (idx >= 0 && idx < s.length) {
                    s[idx] *= g;
                }
            }
        }
        return out;
    }

    private static float[] removeRange(float[] samples, int channels, TimeSelection sel) {
        long frames = samples.length / channels;
        long keep = frames - sel.length();
        float[] out = new float[(int) (keep * channels)];
        System.arraycopy(samples, 0, out, 0, (int) (sel.getStartSample() * channels));
        System.arraycopy(samples, (int) (sel.getEndSample() * channels), out,
                (int) (sel.getStartSample() * channels),
                (int) ((frames - sel.getEndSample()) * channels));
        return out;
    }
}
