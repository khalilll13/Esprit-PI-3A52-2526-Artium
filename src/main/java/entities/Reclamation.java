package entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Reclamation {
    private Integer id;
    private String texte;
    private LocalDateTime dateCreation;
    private String statut;
    private String type;
    private String fileName;
    private LocalDateTime updatedAt;
    private Integer userId;
    private Boolean isArchived = false;

    public Reclamation() {
    }

    public Reclamation(String texte, LocalDateTime dateCreation, String statut, String type, String fileName, LocalDateTime updatedAt, Integer userId) {
        this.texte = texte;
        this.dateCreation = dateCreation;
        this.statut = statut;
        this.type = type;
        this.fileName = fileName;
        this.updatedAt = updatedAt;
        this.userId = userId;
        this.isArchived = false;
    }

    public Reclamation(Integer id, String texte, LocalDateTime dateCreation, String statut, String type, String fileName, LocalDateTime updatedAt, Integer userId) {
        this.id = id;
        this.texte = texte;
        this.dateCreation = dateCreation;
        this.statut = statut;
        this.type = type;
        this.fileName = fileName;
        this.updatedAt = updatedAt;
        this.userId = userId;
        this.isArchived = false;
    }

    public Reclamation(Integer id, String texte, LocalDateTime dateCreation, String statut, String type, String fileName, LocalDateTime updatedAt, Integer userId, Boolean isArchived) {
        this.id = id;
        this.texte = texte;
        this.dateCreation = dateCreation;
        this.statut = statut;
        this.type = type;
        this.fileName = fileName;
        this.updatedAt = updatedAt;
        this.userId = userId;
        this.isArchived = isArchived == null ? false : isArchived;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) { this.id = id; }

    public String getTexte() {
        return texte;
    }

    public void setTexte(String texte) {
        this.texte = texte;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Boolean getIsArchived() {
        return isArchived;
    }

    public void setIsArchived(Boolean isArchived) {
        this.isArchived = isArchived == null ? false : isArchived;
    }
}

