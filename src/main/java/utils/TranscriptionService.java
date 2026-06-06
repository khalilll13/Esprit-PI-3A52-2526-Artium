package utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple wrapper that calls an external whisper/whisper.cpp binary to transcribe audio files.
 * - Uses the executable name from system property 'whisper.cmd' or defaults to 'whisper'.
 * - Reads the produced .txt output and returns its content.
 */
public class TranscriptionService {
    // Command for whisper CLI; can be overridden with -Dwhisper.cmd="C:\\path\\to\\whisper.exe"
    private static final String WHISPER_CMD = System.getProperty("whisper.cmd", "whisper");

    /**
     * Transcribe the given audio file with whisper CLI.
     * Returns the transcript text, or null on failure.
     */
    public static String transcribe(String audioFilePath) {
        if (audioFilePath == null || audioFilePath.isBlank()) {
            return null;
        }

        try {
            Path audio = Paths.get(audioFilePath);
            if (!Files.exists(audio)) {
                return null;
            }

            Path outDir = Files.createTempDirectory("whisper-out-");

            ProcessBuilder pb = new ProcessBuilder(
                    WHISPER_CMD,
                    audio.toAbsolutePath().toString(),
                    "--model", "small",
                    "--task", "transcribe",
                    "--language", "auto",
                    "--output_format", "txt",
                    "--output_dir", outDir.toAbsolutePath().toString()
            );

            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    // Print process output to stdout for debugging
                    System.out.println("[whisper] " + line);
                }
            }

            int exit = p.waitFor();
            if (exit != 0) {
                return null;
            }

            String baseName = audio.getFileName().toString();
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) baseName = baseName.substring(0, dot);
            Path transcriptFile = outDir.resolve(baseName + ".txt");

            if (!Files.exists(transcriptFile)) {
                try (var stream = Files.list(outDir)) {
                    for (Path pth : (Iterable<Path>) stream::iterator) {
                        if (pth.toString().toLowerCase().endsWith(".txt")) {
                            transcriptFile = pth;
                            break;
                        }
                    }
                } catch (IOException ignored) {
                }
            }

            if (!Files.exists(transcriptFile)) {
                return null;
            }

            String content = Files.readString(transcriptFile, StandardCharsets.UTF_8);
            // cleanup temp output (best-effort)
            deleteRecursively(outDir.toFile());
            return content.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursively(c);
            }
        }
        if (!f.delete()) {
            System.err.println("Could not delete file: " + f.getAbsolutePath());
        }
    }
}



