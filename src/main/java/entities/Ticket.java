package entities;

import java.time.LocalDate;

public class Ticket {
    private Integer id;
    private String codeQr;
    private LocalDate dateAchat;
    private Integer evenementId;
    private Integer userId;

    public Ticket() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCodeQr() {
        return codeQr;
    }

    public void setCodeQr(String codeQr) {
        this.codeQr = codeQr;
    }

    public LocalDate getDateAchat() {
        return dateAchat;
    }

    public void setDateAchat(LocalDate dateAchat) {
        this.dateAchat = dateAchat;
    }

    public Integer getEvenementId() {
        return evenementId;
    }

    public void setEvenementId(Integer evenementId) {
        this.evenementId = evenementId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
}

