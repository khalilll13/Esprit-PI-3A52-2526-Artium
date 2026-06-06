package entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Musique extends Oeuvre {
    private String genre;
    private String audio;
    private LocalDateTime updatedAt;

    // Relation many-to-many via playlist_musique (loaded by service layer).
    private List<Playlist> playlists = new ArrayList<>();

    public Musique() {
        setClasse("musique");
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getAudio() {
        return audio;
    }

    public void setAudio(String audio) {
        this.audio = audio;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Playlist> getPlaylists() {
        return playlists;
    }

    public void setPlaylists(List<Playlist> playlists) {
        this.playlists = playlists != null ? playlists : new ArrayList<>();
    }
}
