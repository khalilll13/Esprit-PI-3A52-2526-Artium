package utils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLDataException;

/**
 * Normalizes image values before persistence so DB stores a stable absolute URL.
 */
public final class ImageUrlUtils {

    public static final String IMAGE_BASE_URL = "http://127.0.0.1/img/";
    private static final Path IMAGE_UPLOAD_DIR = Paths.get("C:\\xampp\\htdocs\\img");

    private ImageUrlUtils() {
        // Utility class.
    }

    public static String normalizeForDatabase(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String candidate = rawValue.trim();
        if (candidate.isEmpty()) {
            return null;
        }

        String fileName = extractFileName(candidate);
        if (fileName.isEmpty()) {
            return null;
        }

        return IMAGE_BASE_URL + fileName;
    }

    public static String persistToWebImageDirectoryAndNormalize(String rawValue) throws SQLDataException {
        String copiedPathOrOriginal = copyToWebImageDirectoryIfLocal(rawValue);
        return normalizeForDatabase(copiedPathOrOriginal);
    }

    private static String copyToWebImageDirectoryIfLocal(String rawValue) throws SQLDataException {
        if (rawValue == null) {
            return null;
        }

        String candidate = rawValue.trim();
        if (candidate.isEmpty()) {
            return null;
        }

        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            return candidate;
        }

        Path sourcePath = resolveLocalPath(candidate);
        if (sourcePath == null) {
            return candidate;
        }

        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new SQLDataException("Image locale introuvable: " + sourcePath);
        }

        try {
            Files.createDirectories(IMAGE_UPLOAD_DIR);
            Path targetPath = IMAGE_UPLOAD_DIR.resolve(sourcePath.getFileName().toString());
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toString();
        } catch (IOException e) {
            throw new SQLDataException("Impossible de copier l'image vers " + IMAGE_UPLOAD_DIR + ": " + e.getMessage());
        }
    }

    private static Path resolveLocalPath(String rawValue) {
        try {
            if (rawValue.startsWith("file:/")) {
                return Paths.get(URI.create(rawValue));
            }
            return Paths.get(rawValue);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractFileName(String value) {
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
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        return fileName.trim();
    }
}

