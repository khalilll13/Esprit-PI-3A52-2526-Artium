package entities;

public class Livre extends Oeuvre {
    private String categorie;
    private Double prixLocation;
    private String fichierPdf;
    private String auteur;
    private Boolean disponibilite;

    public Livre() {
        setClasse("livre");
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }

    public Double getPrixLocation() {
        return prixLocation;
    }

    public void setPrixLocation(Double prixLocation) {
        this.prixLocation = prixLocation;
    }

    public String getFichierPdf() {
        return fichierPdf;
    }

    public void setFichierPdf(String fichierPdf) {
        this.fichierPdf = fichierPdf;
    }

    public String getAuteur() {
        return auteur;
    }

    public void setAuteur(String auteur) {
        this.auteur = auteur;
    }

    public Boolean getDisponibilite() {
        return disponibilite;
    }

    public void setDisponibilite(Boolean disponibilite) {
        this.disponibilite = disponibilite;
    }
}
