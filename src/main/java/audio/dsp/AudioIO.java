package audio.dsp;

import audio.model.AudioBuffer;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Load/save PCM — WAV, MP3 (SPI), M4A/AAC via ffmpeg si disponible.
 */
public final class AudioIO {
    private static final Logger LOG = Logger.getLogger(AudioIO.class.getName());

    private AudioIO() {
    }

    public static String stripQuery(String path) {
        if (path == null) {
            return "";
        }
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }

    public static AudioBuffer load(Path path) throws IOException {
        IOException last = null;
        try {
            return loadPcm(path);
        } catch (UnsupportedAudioFileException e) {
            last = new IOException("Format non supporté nativement : " + path.getFileName(), e);
        } catch (IOException e) {
            last = e;
        }

        Path decoded = decodeWithFfmpeg(path);
        if (decoded != null) {
            try {
                AudioBuffer buf = loadPcm(decoded);
                LOG.log(Level.INFO, "Audio décodé via ffmpeg : {0}", path.getFileName());
                return buf;
            } catch (UnsupportedAudioFileException e) {
                throw new IOException("Échec lecture après décodage ffmpeg", e);
            } finally {
                try {
                    Files.deleteIfExists(decoded);
                } catch (IOException ignored) {
                }
            }
        }
        throw last != null ? last : new IOException("Impossible de charger : " + path);
    }

    private static AudioBuffer loadPcm(Path path) throws IOException, UnsupportedAudioFileException {
        File file = path.toFile();
        if (!file.exists()) {
            throw new IOException("Fichier introuvable: " + path);
        }
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            AudioFormat base = in.getFormat();
            float sampleRate = base.getSampleRate() > 0 ? base.getSampleRate() : 44100f;
            int channels = base.getChannels() > 0 ? base.getChannels() : 2;
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate, 16, channels, channels * 2, sampleRate, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
                byte[] bytes = pcm.readAllBytes();
                return bytesToBuffer(bytes, (int) target.getSampleRate(), target.getChannels());
            }
        }
    }

    private static Path decodeWithFfmpeg(Path source) {
        try {
            Path out = Files.createTempFile("artium-decode-", ".wav");
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-loglevel", "error",
                    "-i", source.toAbsolutePath().toString(),
                    "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2",
                    out.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            if (code == 0 && Files.size(out) > 44) {
                return out;
            }
            Files.deleteIfExists(out);
        } catch (Exception e) {
            LOG.log(Level.FINE, "ffmpeg indisponible pour {0}: {1}",
                    new Object[]{source.getFileName(), e.getMessage()});
        }
        return null;
    }

    private static AudioBuffer bytesToBuffer(byte[] bytes, int sampleRate, int channels) {
        int frameBytes = 2 * channels;
        int frames = bytes.length / frameBytes;
        float[] samples = new float[frames * channels];
        for (int i = 0; i < frames * channels; i++) {
            int lo = bytes[i * 2] & 0xff;
            int hi = bytes[i * 2 + 1];
            short s = (short) ((hi << 8) | lo);
            samples[i] = s / 32768f;
        }
        return new AudioBuffer(samples, sampleRate, channels);
    }

    public static void saveWav(AudioBuffer buffer, Path out) throws IOException {
        Files.createDirectories(out.getParent() != null ? out.getParent() : Path.of("."));
        byte[] bytes = bufferToPcm16(buffer);
        AudioFormat format = new AudioFormat(
                buffer.getSampleRate(), 16, buffer.getChannels(), true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(bytes), format, buffer.getFrameCount())) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out.toFile());
        }
        LOG.log(Level.INFO, "Exported WAV: {0}", out);
    }

    public static void save(Path out, AudioBuffer buffer, audio.model.ExportConfig.Format format,
                            int bitrateKbps) throws IOException {
        switch (format) {
            case WAV -> saveWav(buffer, out);
            case MP3, AAC, FLAC -> {
                Path wav = out.resolveSibling(out.getFileName().toString() + ".tmp.wav");
                saveWav(buffer, wav);
                boolean ok = tryFfmpegConvert(wav, out, format, bitrateKbps);
                Files.deleteIfExists(wav);
                if (!ok) {
                    Path fallback = out.resolveSibling(
                            replaceExt(out.getFileName().toString(), ".wav"));
                    saveWav(buffer, fallback);
                    throw new IOException(
                            "Export " + format + " nécessite ffmpeg. Fichier WAV créé : " + fallback.getFileName());
                }
            }
        }
    }

    private static boolean tryFfmpegConvert(Path wav, Path out,
                                           audio.model.ExportConfig.Format format, int bitrate) {
        String codec = switch (format) {
            case MP3 -> "libmp3lame";
            case FLAC -> "flac";
            case AAC -> "aac";
            default -> "copy";
        };
        String ext = switch (format) {
            case MP3 -> "mp3";
            case FLAC -> "flac";
            case AAC -> "m4a";
            default -> "wav";
        };
        Path target = out.toString().endsWith("." + ext) ? out : Path.of(out + "." + ext);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", wav.toString(),
                    "-codec:a", codec, "-b:a", bitrate + "k", target.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            if (code == 0 && Files.exists(target)) {
                if (!target.equals(out)) {
                    Files.move(target, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ffmpeg non disponible: {0}", e.getMessage());
        }
        return false;
    }

    private static String replaceExt(String name, String ext) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + ext;
    }

    private static byte[] bufferToPcm16(AudioBuffer buffer) {
        float[] s = buffer.getSamples();
        byte[] bytes = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) {
            int v = (int) (Math.max(-1, Math.min(1, s[i])) * 32767);
            bytes[i * 2] = (byte) (v & 0xff);
            bytes[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
        }
        return bytes;
    }
}
