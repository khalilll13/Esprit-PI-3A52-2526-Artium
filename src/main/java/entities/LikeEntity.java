package entities;

public class LikeEntity {
    private Integer id;
    private Boolean liked;
    private Integer userId;
    private Integer oeuvreId;

    public LikeEntity() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean getLiked() {
        return liked;
    }

    public void setLiked(Boolean liked) {
        this.liked = liked;
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

