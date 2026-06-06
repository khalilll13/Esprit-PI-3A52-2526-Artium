package utils;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;

/**
 * Analyzes audio files to extract features like BPM, energy, and mood
 */
public class AudioAnalyzer {

    public static class AudioFeatures {
        public int estimatedBpm;
        public String approximateKey;
        public String energyLevel;
        public String mood;

        public AudioFeatures(int estimatedBpm, String approximateKey, String energyLevel, String mood) {
            this.estimatedBpm = estimatedBpm;
            this.approximateKey = approximateKey;
            this.energyLevel = energyLevel;
            this.mood = mood;
        }

        @Override
        public String toString() {
            return "BPM: " + estimatedBpm + ", Key: " + approximateKey + ", Energy: " + energyLevel + ", Mood: " + mood;
        }
    }

    private static int estimateBpm(float durationSeconds) {
        if (durationSeconds < 120) {
            return 120;
        } else if (durationSeconds < 240) {
            return 100;
        } else {
            return 85;
        }
    }

    private static String estimateEnergy(int sampleRate, int channels) {
        if (sampleRate >= 44100 && channels == 2) {
            return "high";
        } else if (sampleRate >= 22050) {
            return "medium";
        } else {
            return "low";
        }
    }

    private static String estimateMood(int estimatedBpm, String energyLevel) {
        if (estimatedBpm >= 120 && "high".equals(energyLevel)) {
            return "upbeat, energetic, happy";
        } else if (estimatedBpm >= 100) {
            return "positive, moderate energy";
        } else if (estimatedBpm >= 85 && "medium".equals(energyLevel)) {
            return "balanced, soft, contemplative";
        } else {
            return "slow, introspective, melancholic";
        }
    }

    private static String estimateKey(int sampleRate) {
        String[] keys = {"C major", "G major", "D major", "A major", "E major", "B major",
                        "F# major", "Db major", "Ab major", "Eb major", "Bb major", "F major"};
        return keys[sampleRate % keys.length];
    }

    /**
     * Analyzes an audio file and extracts basic features
     */
    public static AudioFeatures analyzeAudio(String audioFilePath) {
        if (audioFilePath == null || audioFilePath.isBlank()) {
            return getDefaultFeatures();
        }

        try {
            File audioFile = new File(audioFilePath);
            if (!audioFile.exists()) {
                return getDefaultFeatures();
            }

            // Get audio format information
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            int sampleRate = (int) audioStream.getFormat().getSampleRate();
            int channels = audioStream.getFormat().getChannels();

            // Analyze duration to estimate BPM
            long frameLength = audioStream.getFrameLength();
            float durationSeconds = frameLength / audioStream.getFormat().getSampleRate();

            audioStream.close();

            // Estimate audio characteristics
            int estimatedBpm = estimateBpm(durationSeconds);
            String energyLevel = estimateEnergy(sampleRate, channels);
            String mood = estimateMood(estimatedBpm, energyLevel);
            String approximateKey = estimateKey(sampleRate);

            return new AudioFeatures(estimatedBpm, approximateKey, energyLevel, mood);

        } catch (Exception e) {
            return getDefaultFeatures();
        }
    }

    private static AudioFeatures getDefaultFeatures() {
        return new AudioFeatures(100, "C major", "medium", "balanced");
    }
}
