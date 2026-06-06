package entities;

import java.time.LocalDate;

public class Commentaire {
    private Integer id;
    private String texte;
    private LocalDate dateCommentaire;
    private Integer userId;
    private Integer oeuvreId;

    public Commentaire() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTexte() {
        return texte;
    }

    public void setTexte(String texte) {
        this.texte = texte;
    }

    public LocalDate getDateCommentaire() {
        return dateCommentaire;
    }

    public void setDateCommentaire(LocalDate dateCommentaire) {
        this.dateCommentaire = dateCommentaire;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getOeuvreId() {
        return oeuvreId;
    }

    public void setOeuvreId(Integer oeuvreId) {
        this.oeuvreId = oeuvreId;
    }
}

