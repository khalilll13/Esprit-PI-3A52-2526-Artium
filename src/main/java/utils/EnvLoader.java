package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility to load environment variables from .env file
 */
public class EnvLoader {
    private static final Map<String, String> envMap = new HashMap<>();
    private static boolean loaded = false;

    static {
        loadEnv();
    }

    /**
     * Load .env file from project root
     */
    public static void loadEnv() {
        loadEnv(false);
    }

    /**
     * Load .env file with option to force reload
     */
    public static void loadEnv(boolean force) {
        if (loaded && !force) return;
        
        if (force) {
            envMap.clear();
        }

        String envPath = System.getProperty("user.dir") + File.separator + ".env";
        File envFile = new File(envPath);

        if (!envFile.exists()) {
            System.err.println("Warning: .env file not found at " + envPath);
            loaded = true;
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Parse KEY=VALUE
                int eqIndex = line.indexOf('=');
                if (eqIndex > 0) {
                    String key = line.substring(0, eqIndex).trim();
                    String value = line.substring(eqIndex + 1).trim();
                    envMap.put(key, value);
                }
            }
            loaded = true;
        } catch (IOException e) {
            System.err.println("Error loading .env file: " + e.getMessage());
            loaded = true;
        }
    }

    /**
     * Get environment variable by key
     */
    public static String get(String key) {
        return envMap.getOrDefault(key, System.getenv(key));
    }

    /**
     * Get environment variable with default value
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Check if key exists
     */
    public static boolean has(String key) {
        return envMap.containsKey(key) || System.getenv(key) != null;
    }
}

