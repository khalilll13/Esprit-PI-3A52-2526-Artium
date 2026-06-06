package entities;

public class CollectionOeuvre {
    private Integer id;
    private String titre;
    private String description;
    private Integer artisteId;

    public CollectionOeuvre() {
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

    public Integer getArtisteId() {
        return artisteId;
    }

    public void setArtisteId(Integer artisteId) {
        this.artisteId = artisteId;
    }
}
