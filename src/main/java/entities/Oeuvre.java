package entities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Oeuvre {
    private Integer id;
    private String titre;
    private String description;
    private LocalDate dateCreation;
    private String image;
    private String type;
    private String embedding;
    private String imageEmbedding;
    private Integer collectionId;
    private String classe;
    private boolean isPublic = true;

    // Relation many-to-many via oeuvre_user (loaded by service layer).
    private List<User> favusers = new ArrayList<>();

    // Relation one-to-many avec les commentaires (loaded by service layer).
    private List<Commentaire> comments = new ArrayList<>();

    public Oeuvre() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public String getImageEmbedding() {
        return imageEmbedding;
    }

    public void setImageEmbedding(String imageEmbedding) {
        this.imageEmbedding = imageEmbedding;
    }

    public Integer getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Integer collectionId) {
        this.collectionId = collectionId;
    }

    public String getClasse() {
        return classe;
    }

    public void setClasse(String classe) {
        this.classe = classe;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public List<User> getUsers() {
        return favusers;
    }

    public void setUsers(List<User> users) {
        this.favusers = users != null ? users : new ArrayList<>();
    }

    public List<Commentaire> getComments() {
        return comments;
    }

    public void setComments(List<Commentaire> comments) {
        this.comments = comments != null ? comments : new ArrayList<>();
    }
}
