package entities;

import java.time.LocalDate;

public class Reponse {
    private Integer id;
    private String contenu;
    private LocalDate dateReponse;
    private Integer reclamationId;
    private Integer userAdminId;

    public Reponse() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public LocalDate getDateReponse() {
        return dateReponse;
    }

    public void setDateReponse(LocalDate dateReponse) {
        this.dateReponse = dateReponse;
    }

    public Integer getReclamationId() {
        return reclamationId;
    }

    public void setReclamationId(Integer reclamationId) {
        this.reclamationId = reclamationId;
    }

    public Integer getUserAdminId() {
        return userAdminId;
    }

    public void setUserAdminId(Integer userAdminId) {
        this.userAdminId = userAdminId;
    }
}

