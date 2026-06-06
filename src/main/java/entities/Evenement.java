package entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Evenement {
    private Integer id;
    private String titre;
    private String description;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private LocalDate dateCreation;
    private String type;
    private String imageCouverture;
    private String statut;
    private Integer capaciteMax;
    private Double prixTicket;
    private Integer galerieId;
    private Integer artisteId;

    public Evenement() {
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

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDateTime dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDateTime dateFin) {
        this.dateFin = dateFin;
    }

    public LocalDate getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDate dateCreation) {
        this.dateCreation = dateCreation;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageCouverture() {
        return imageCouverture;
    }

    public void setImageCouverture(String imageCouverture) {
        this.imageCouverture = imageCouverture;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public Integer getCapaciteMax() {
        return capaciteMax;
    }

    public void setCapaciteMax(Integer capaciteMax) {
        this.capaciteMax = capaciteMax;
    }

    public Double getPrixTicket() {
        return prixTicket;
    }

    public void setPrixTicket(Double prixTicket) {
        this.prixTicket = prixTicket;
    }

    public Integer getGalerieId() {
        return galerieId;
    }

    public void setGalerieId(Integer galerieId) {
        this.galerieId = galerieId;
    }

    public Integer getArtisteId() {
        return artisteId;
    }

    public void setArtisteId(Integer artisteId) {
        this.artisteId = artisteId;
    }
}

