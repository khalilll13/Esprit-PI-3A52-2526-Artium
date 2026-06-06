package entities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Playlist {
    private Integer id;
    private String nom;
    private String description;
    private LocalDate dateCreation;
    private String image;
    private Integer userId;

    // Relation many-to-many via playlist_musique (loaded by service layer).
    private List<Musique> musiques = new ArrayList<>();

    public Playlist() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDate dateCreation) {
        this.dateCreation = dateCreation;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public List<Musique> getMusiques() {
        return musiques;
    }

    public void setMusiques(List<Musique> musiques) {
        this.musiques = musiques != null ? musiques : new ArrayList<>();
    }
}
