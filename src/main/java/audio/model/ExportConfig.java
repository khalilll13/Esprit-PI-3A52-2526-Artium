package audio.model;

public final class ExportConfig {
    public enum Format { WAV, MP3, FLAC, AAC }

    private Format format = Format.WAV;
    private int sampleRate = 44100;
    private int channels = 2;
    private int bitrateKbps = 320;
    private String quality = "high";

    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        this.format = format != null ? format : Format.WAV;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = Math.max(1, Math.min(2, channels));
    }

    public int getBitrateKbps() {
        return bitrateKbps;
    }

    public void setBitrateKbps(int bitrateKbps) {
        this.bitrateKbps = bitrateKbps;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }
}
