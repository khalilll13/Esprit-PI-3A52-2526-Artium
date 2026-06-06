package utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Résout les chemins audio locaux, XAMPP et URLs distantes pour le studio.
 */
public final class AudioPathResolver {
    private static final Logger LOG = Logger.getLogger(AudioPathResolver.class.getName());

    private static final File[] FALLBACK_DIRS = {
            new File("C:\\xampp\\htdocs\\uploads\\audio"),
            new File("C:\\xampp\\htdocs\\uploads\\music"),
            new File("C:\\xampp\\htdocs\\audio"),
            new File("C:\\xampp\\htdocs\\music"),
            new File("C:\\xampp\\htdocs\\img"),
            new File("c:\\Work\\3eme\\Semestre2\\PI_Dev\\Artium(final)\\ARTIUM\\public\\uploads\\music"),
            new File("c:\\Work\\3eme\\Semestre2\\PI_Dev\\Artium(final)\\ARTIUM\\public\\audio")
    };

    private AudioPathResolver() {
    }

    public static String stripQuery(String path) {
        if (path == null) {
            return "";
        }
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }

    /**
     * Retourne un fichier local lisible, ou null si introuvable.
     */
    public static File resolve(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            return null;
        }
        String trimmed = stripQuery(audioPath.trim());
        if (trimmed.length() > 1 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("/") && trimmed.length() > 2 && trimmed.charAt(2) == ':') {
            trimmed = trimmed.substring(1);
        }

        if (trimmed.startsWith("file:")) {
            try {
                File f = new File(new URI(trimmed));
                if (f.exists() && f.isFile()) {
                    return f;
                }
            } catch (Exception ignored) {
                String raw = trimmed.substring("file:".length());
                if (raw.startsWith("//")) {
                    raw = raw.substring(2);
                }
                if (raw.startsWith("/") && raw.length() > 2 && raw.charAt(2) == ':') {
                    raw = raw.substring(1);
                }
                File f = new File(raw);
                if (f.exists() && f.isFile()) {
                    return f;
                }
            }
        }

        File direct = new File(trimmed);
        if (direct.exists() && direct.isFile()) {
            return direct;
        }

        String fileName = extractFileName(trimmed);
        if (!fileName.isEmpty()) {
            for (File dir : FALLBACK_DIRS) {
                File candidate = new File(dir, fileName);
                if (candidate.exists() && candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Résout ou télécharge l'audio pour le studio (fichier local garanti si possible).
     */
    public static File resolveForStudio(String audioPath) throws IOException {
        File local = resolve(audioPath);
        if (local != null) {
            return local;
        }
        String trimmed = stripQuery(audioPath.trim());
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return downloadRemote(trimmed);
        }
        throw new IOException("Fichier audio introuvable : " + audioPath);
    }

    private static File downloadRemote(String url) throws IOException {
        String name = extractFileName(url);
        if (name.isEmpty()) {
            name = "remote-audio.mp3";
        }
        Path temp = Files.createTempFile("artium-studio-", "-" + name);
        LOG.log(Level.INFO, "Téléchargement audio : {0}", url);
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        return temp.toFile();
    }

    public static String extractFileName(String value) {
        String normalized = value.replace('\\', '/');
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int lastSlash = normalized.lastIndexOf('/');
        return (lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized).trim();
    }
}
