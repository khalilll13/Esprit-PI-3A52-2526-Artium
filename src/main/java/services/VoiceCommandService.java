package services;

import javafx.application.Platform;
import org.vosk.LogLevel;
import org.vosk.Recognizer;
import org.vosk.Model;
import org.json.JSONObject;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URL;
import java.util.function.Consumer;

public class VoiceCommandService {

    private Model            voskModel;
    private Recognizer       recognizer;
    private Thread           listenThread;
    private volatile boolean isListening = false;
    private TargetDataLine   microphone;

    private Consumer<String> onPartialCallback;
    private Consumer<String> onFinalCallback;
    private Consumer<String> onErrorCallback;

    public void init(Consumer<String> onPartial,
                     Consumer<String> onFinal,
                     Consumer<String> onError) {
        this.onPartialCallback = onPartial;
        this.onFinalCallback   = onFinal;
        this.onErrorCallback   = onError;

        new Thread(() -> {
            try {
                org.vosk.LibVosk.setLogLevel(LogLevel.WARNINGS);
                URL modelUrl = getClass().getResource("/vosk-model-fr");
                if (modelUrl == null) {
                    Platform.runLater(() ->
                            onError.accept("Modèle Vosk introuvable dans resources/vosk-model-fr"));
                    return;
                }
                voskModel = new Model(new File(modelUrl.toURI()).getAbsolutePath());
                System.out.println("✅ Modèle Vosk chargé.");
            } catch (Exception e) {
                Platform.runLater(() ->
                        onError.accept("Erreur chargement modèle Vosk : " + e.getMessage()));
            }
        }, "vosk-model-loader").start();
    }

    public void startListening() {
        if (isListening) return;

        if (voskModel == null) {
            if (onErrorCallback != null)
                onErrorCallback.accept("Modèle Vosk pas encore chargé, patientez...");
            return;
        }

        isListening  = true;
        listenThread = new Thread(() -> {
            try {
                // ── Trouver le micro qui fonctionne vraiment ──────────────────
                MicResult micResult = findWorkingMicrophone();

                if (micResult == null) {
                    // Debug : afficher tous les mixers pour diagnostic
                    System.err.println("=== DIAGNOSTIC MIXERS ===");
                    for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                        Mixer mx = AudioSystem.getMixer(mi);
                        Line.Info[] targets = mx.getTargetLineInfo();
                        System.err.println("Mixer: " + mi.getName()
                                + " | TargetLines: " + targets.length);
                        for (Line.Info li : targets) {
                            System.err.println("   └─ " + li);
                        }
                    }
                    System.err.println("=========================");

                    Platform.runLater(() ->
                            onErrorCallback.accept("Aucun microphone détecté. Consultez la console."));
                    isListening = false;
                    return;
                }

                microphone = micResult.line;
                int nativeRate = (int) micResult.format.getSampleRate();
                System.out.println("🎙 Micro OK → " + micResult.mixerName
                        + " | " + micResult.format);

                microphone.start();
                recognizer = new Recognizer(voskModel, 16000.0f);

                // ── Resampling manuel vers 16000 Hz ───────────────────────────
                final int    IN_RATE     = nativeRate;
                final int    OUT_RATE    = 16000;
                final int    READ_SIZE   = 8192;
                byte[]       inputBuf    = new byte[READ_SIZE];
                byte[]       outputBuf   = new byte[READ_SIZE];
                double       srcPos      = 0.0;
                double       ratio       = (double) IN_RATE / OUT_RATE;

                while (isListening) {
                    int bytesRead = microphone.read(inputBuf, 0, inputBuf.length);
                    if (bytesRead <= 0) continue;

                    int inSamples = bytesRead / 2;
                    int outIdx    = 0;

                    while (srcPos < inSamples && outIdx + 1 < outputBuf.length) {
                        int   i0  = (int) srcPos;
                        int   i1  = Math.min(i0 + 1, inSamples - 1);
                        double fr = srcPos - i0;

                        short s0  = readShortLE(inputBuf, i0 * 2);
                        short s1  = readShortLE(inputBuf, i1 * 2);
                        short out = (short)(s0 + fr * (s1 - s0));

                        outputBuf[outIdx]     = (byte)(out & 0xFF);
                        outputBuf[outIdx + 1] = (byte)((out >> 8) & 0xFF);
                        outIdx += 2;
                        srcPos += ratio;
                    }

                    srcPos -= inSamples;
                    if (srcPos < 0) srcPos = 0;
                    if (outIdx <= 0) continue;

                    byte[] toVosk = new byte[outIdx];
                    System.arraycopy(outputBuf, 0, toVosk, 0, outIdx);

                    if (recognizer.acceptWaveForm(toVosk, toVosk.length)) {
                        String text = extractText(recognizer.getResult());
                        System.out.println("🗣 Vosk final : [" + text + "]");
                        if (!text.isBlank()) {
                            final String ft = text;
                            Platform.runLater(() -> {
                                if (onFinalCallback != null) onFinalCallback.accept(ft);
                            });
                        }
                    } else {
                        String partial = extractPartial(recognizer.getPartialResult());
                        if (!partial.isBlank()) {
                            System.out.println("🔄 Partiel : " + partial);
                            final String pt = partial;
                            Platform.runLater(() -> {
                                if (onPartialCallback != null) onPartialCallback.accept(pt);
                            });
                        }
                    }
                }

            } catch (Exception e) {
                isListening = false;
                final String msg = e.getMessage() != null
                        ? e.getMessage() : e.getClass().getSimpleName();
                System.err.println("❌ " + msg);
                Platform.runLater(() -> {
                    if (onErrorCallback != null)
                        onErrorCallback.accept("Erreur micro : " + msg);
                });
            } finally {
                if (microphone != null && microphone.isOpen()) {
                    microphone.stop();
                    microphone.close();
                }
                if (recognizer != null) { recognizer.close(); recognizer = null; }
            }
        }, "vosk-listener");

        listenThread.setDaemon(true);
        listenThread.start();
    }

    // ── Résultat de recherche micro ───────────────────────────────────────────
    private static class MicResult {
        TargetDataLine line;
        AudioFormat    format;
        String         mixerName;
        MicResult(TargetDataLine l, AudioFormat f, String n) {
            line = l; format = f; mixerName = n;
        }
    }

    // ── Trouver un micro qui s'ouvre vraiment ─────────────────────────────────
    private MicResult findWorkingMicrophone() {

        float[] rates    = {44100, 48000, 16000, 22050, 8000};
        int[]   bits     = {16, 8};
        int[]   channels = {1, 2};

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        System.out.println("=== Mixers ===");
        for (Mixer.Info mi : mixerInfos) System.out.println("  " + mi.getName());
        System.out.println("==============");

        for (Mixer.Info mixerInfo : mixerInfos) {
            // Ignorer les mixers de sortie (haut-parleurs)
            String name = mixerInfo.getName().toLowerCase();
            if (name.contains("haut-parleur") || name.contains("speaker")
                    || name.contains("output") || name.contains("sortie")
                    || name.contains("epson") || name.contains("projet")) {
                continue;
            }

            Mixer mixer = AudioSystem.getMixer(mixerInfo);

            // Vérifier qu'il a des lignes d'entrée
            if (mixer.getTargetLineInfo().length == 0) continue;

            for (float rate : rates) {
                for (int bit : bits) {
                    for (int ch : channels) {
                        int frameSize = (bit / 8) * ch;
                        AudioFormat fmt = new AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED,
                                rate, bit, ch, frameSize, rate, false
                        );
                        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

                        if (!mixer.isLineSupported(info)) continue;

                        try {
                            TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                            line.open(fmt, 8192 * 2);
                            return new MicResult(line, fmt, mixerInfo.getName());
                        } catch (LineUnavailableException ignored) {}
                    }
                }
            }
        }

        // Dernier recours : AudioSystem par défaut sans filtrage
        System.out.println("⚠ Essai AudioSystem par défaut...");
        for (float rate : rates) {
            for (int bit : bits) {
                for (int ch : channels) {
                    int frameSize = (bit / 8) * ch;
                    AudioFormat fmt = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            rate, bit, ch, frameSize, rate, false
                    );
                    try {
                        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(
                                new DataLine.Info(TargetDataLine.class, fmt));
                        line.open(fmt, 8192 * 2);
                        System.out.println("✅ Défaut OK : " + fmt);
                        return new MicResult(line, fmt, "default");
                    } catch (Exception ignored) {}
                }
            }
        }

        return null;
    }

    private short readShortLE(byte[] buf, int offset) {
        if (offset < 0 || offset + 1 >= buf.length) return 0;
        return (short)((buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8));
    }

    public void stopListening() {
        isListening = false;
        if (microphone != null && microphone.isOpen()) {
            microphone.stop();
            microphone.close();
        }
        if (listenThread != null) {
            listenThread.interrupt();
            listenThread = null;
        }
    }

    public boolean isListening() { return isListening; }

    private String extractText(String json) {
        try { return new JSONObject(json).optString("text", "").trim(); }
        catch (Exception e) { return ""; }
    }

    private String extractPartial(String json) {
        try { return new JSONObject(json).optString("partial", "").trim(); }
        catch (Exception e) { return ""; }
    }
}