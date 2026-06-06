package entities;

public class Galerie {
    private Integer id;
    private String nom;
    private String adresse;
    private String localisation;
    private String description;
    private Integer capaciteMax;

    public Galerie() {
    }

    public Galerie(String nom, String adresse, String localisation, String description, Integer capaciteMax) {
        this.nom = nom;
        this.adresse = adresse;
        this.localisation = localisation;
        this.description = description;
        this.capaciteMax = capaciteMax;
    }

    public Galerie(Integer id, String nom, String adresse, String localisation, String description, Integer capaciteMax) {
        this.id = id;
        this.nom = nom;
        this.adresse = adresse;
        this.localisation = localisation;
        this.description = description;
        this.capaciteMax = capaciteMax;
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

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getLocalisation() {
        return localisation;
    }

    public void setLocalisation(String localisation) {
        this.localisation = localisation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getCapaciteMax() {
        return capaciteMax;
    }

    public void setCapaciteMax(Integer capaciteMax) {
        this.capaciteMax = capaciteMax;
    }
}

