package audio.service;

import audio.model.AudioBuffer;
import javafx.application.Platform;

import javax.sound.sampled.*;
import java.util.function.LongConsumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lecture audio non bloquante via SourceDataLine.
 */
public final class StudioPlaybackService {
    private static final Logger LOG = Logger.getLogger(StudioPlaybackService.class.getName());

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicLong playheadFrame = new AtomicLong(0);
    private Thread playThread;
    private LongConsumer playheadListener;
    private Runnable onEndedListener;
    private volatile AudioBuffer currentBuffer;

    public void setPlayheadListener(LongConsumer listener) {
        this.playheadListener = listener;
    }

    public void setOnEndedListener(Runnable listener) {
        this.onEndedListener = listener;
    }

    public long getPlayheadFrame() {
        return playheadFrame.get();
    }

    public void setPlayheadFrame(long frame) {
        playheadFrame.set(Math.max(0, frame));
    }

    public boolean isPlaying() {
        return active.get() && !paused.get();
    }

    public boolean isPaused() {
        return active.get() && paused.get();
    }

    public boolean isActive() {
        return active.get();
    }

    public void play(AudioBuffer buffer, long fromFrame) {
        stop();
        if (buffer == null || buffer.getFrameCount() == 0) {
            return;
        }
        currentBuffer = buffer;
        playheadFrame.set(fromFrame);
        active.set(true);
        paused.set(false);
        playThread = new Thread(() -> runPlayback(buffer, fromFrame), "studio-playback");
        playThread.setDaemon(true);
        playThread.start();
    }

    public void updateBuffer(AudioBuffer newBuffer) {
        if (newBuffer != null) {
            this.currentBuffer = newBuffer;
        }
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        if (active.get()) {
            paused.set(false);
            synchronized (this) {
                notifyAll();
            }
        }
    }

    public void stop() {
        active.set(false);
        paused.set(false);
        if (playThread != null) {
            playThread.interrupt();
            try {
                playThread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            playThread = null;
        }
    }

    public void togglePause() {
        if (!active.get()) {
            return;
        }
        if (paused.get()) {
            resume();
        } else {
            pause();
        }
    }

    private void runPlayback(AudioBuffer buffer, long startFrame) {
        SourceDataLine line = null;
        try {
            int rate = buffer.getSampleRate();
            int ch = buffer.getChannels();
            AudioFormat format = new AudioFormat(rate, 16, ch, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                LOG.warning("SourceDataLine non supporté");
                return;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, 8192);
            line.start();
            int chunkFrames = 2048;
            byte[] chunk = new byte[chunkFrames * ch * 2];
            long f = startFrame;
            while (active.get()) {
                AudioBuffer activeBuf = currentBuffer;
                if (activeBuf == null) {
                    break;
                }
                float[] samples = activeBuf.getSamples();
                long frames = activeBuf.getFrameCount();
                if (f >= frames) {
                    break;
                }
                
                long currentPlayhead = playheadFrame.get();
                if (currentPlayhead != f) {
                    f = currentPlayhead;
                    if (line != null) {
                        line.flush();
                    }
                }
                
                if (paused.get()) {
                    synchronized (this) {
                        try {
                            wait(80);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    continue;
                }
                int nFrames = (int) Math.min(chunkFrames, frames - f);
                int byteLen = nFrames * ch * 2;
                for (int i = 0; i < nFrames; i++) {
                    for (int c = 0; c < ch; c++) {
                        int idx = (int) ((f + i) * ch + c);
                        if (idx >= samples.length) {
                            break;
                        }
                        short v = (short) (Math.max(-1, Math.min(1, samples[idx])) * 32767);
                        int bi = (i * ch + c) * 2;
                        chunk[bi] = (byte) (v & 0xff);
                        chunk[bi + 1] = (byte) ((v >> 8) & 0xff);
                    }
                }
                long oldF = f;
                line.write(chunk, 0, byteLen);
                f += nFrames;
                if (playheadFrame.compareAndSet(oldF, f)) {
                    long pos = f;
                    if (playheadListener != null) {
                        Platform.runLater(() -> playheadListener.accept(pos));
                    }
                }
            }
        } catch (LineUnavailableException e) {
            LOG.log(Level.WARNING, "Erreur lecture", e);
        } finally {
            if (line != null) {
                line.drain();
                line.stop();
                line.close();
            }
            active.set(false);
            paused.set(false);
            Platform.runLater(() -> {
                if (playheadListener != null) {
                    playheadListener.accept(playheadFrame.get());
                }
                if (onEndedListener != null) {
                    onEndedListener.run();
                }
            });
        }
    }
}
