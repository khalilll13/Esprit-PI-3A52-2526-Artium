package services;

import entities.LikeEntity;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.List;

public class LikeService implements Iservice<LikeEntity>{
    private final Connection connection;

    public LikeService() {
        this.connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void add(LikeEntity likeEntity) throws SQLDataException {
        if (likeEntity == null || likeEntity.getUserId() == null || likeEntity.getOeuvreId() == null) {
            throw new SQLDataException("Like invalide.");
        }

        boolean liked = likeEntity.getLiked() != null && likeEntity.getLiked();
        String sql = "INSERT INTO `like` (liked, user_id, oeuvre_id) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setBoolean(1, liked);
            preparedStatement.setInt(2, likeEntity.getUserId());
            preparedStatement.setInt(3, likeEntity.getOeuvreId());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }

    }

    @Override
    public void delete(LikeEntity likeEntity) throws SQLDataException {
        if (likeEntity == null || likeEntity.getUserId() == null || likeEntity.getOeuvreId() == null) {
            throw new SQLDataException("Like invalide.");
        }

        String sql = "DELETE FROM `like` WHERE user_id = ? AND oeuvre_id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, likeEntity.getUserId());
            preparedStatement.setInt(2, likeEntity.getOeuvreId());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }

    }

    @Override
    public void update(LikeEntity likeEntity) throws SQLDataException {
        if (likeEntity == null || likeEntity.getUserId() == null || likeEntity.getOeuvreId() == null) {
            throw new SQLDataException("Like invalide.");
        }

        boolean liked = likeEntity.getLiked() != null && likeEntity.getLiked();
        String sql = "UPDATE `like` SET liked = ? WHERE user_id = ? AND oeuvre_id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setBoolean(1, liked);
            preparedStatement.setInt(2, likeEntity.getUserId());
            preparedStatement.setInt(3, likeEntity.getOeuvreId());
            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows == 0) {
                add(likeEntity);
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }

    }

    @Override
    public List<LikeEntity> getAll() throws SQLDataException {
        String sql = "SELECT id, liked, user_id, oeuvre_id FROM `like`";
        List<LikeEntity> likes = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                likes.add(mapLike(resultSet));
            }
            return likes;
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    @Override
    public LikeEntity getById(int id) throws SQLDataException {
        String sql = "SELECT id, liked, user_id, oeuvre_id FROM `like` WHERE id = ? LIMIT 1";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, id);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return mapLike(resultSet);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public boolean isLiked(int userId, int oeuvreId) {
        String sql = "SELECT 1 FROM `like` WHERE user_id = ? AND oeuvre_id = ? AND liked = 1 LIMIT 1";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, userId);
            preparedStatement.setInt(2, oeuvreId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException ignored) {
            // Keep false fallback.
        }
        return false;
    }

    public int countLikesByOeuvre(int oeuvreId) {
        String sql = "SELECT COUNT(DISTINCT user_id) FROM `like` WHERE oeuvre_id = ? AND liked = 1";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, oeuvreId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException ignored) {
            // Keep 0 fallback.
        }
        return 0;
    }

    public boolean toggleLike(int userId, int oeuvreId) throws SQLDataException {
        boolean currentState = isLiked(userId, oeuvreId);
        boolean newState = !currentState;

        String updateSql = "UPDATE `like` SET liked = ? WHERE user_id = ? AND oeuvre_id = ?";
        String insertSql = "INSERT INTO `like` (liked, user_id, oeuvre_id) VALUES (?, ?, ?)";

        try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
            updateStatement.setBoolean(1, newState);
            updateStatement.setInt(2, userId);
            updateStatement.setInt(3, oeuvreId);
            int updatedRows = updateStatement.executeUpdate();

            if (updatedRows == 0) {
                try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                    insertStatement.setBoolean(1, newState);
                    insertStatement.setInt(2, userId);
                    insertStatement.setInt(3, oeuvreId);
                    insertStatement.executeUpdate();
                }
            }

            // Vérifier si trending après ajout de like
            if (newState) {
                try {
                    OeuvreService oeuvreService = new OeuvreService();
                    oeuvreService.checkTrendingAndNotify(oeuvreId);
                } catch (Exception e) {
                    System.err.println("Erreur lors de la vérification trending: " + e.getMessage());
                }
            }

            return newState;
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public boolean isFavori(int userId, int oeuvreId) {
        String sql = "SELECT 1 FROM oeuvre_user WHERE user_id = ? AND oeuvre_id = ? LIMIT 1";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, userId);
            preparedStatement.setInt(2, oeuvreId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException ignored) {
            // Keep false fallback.
        }
        return false;
    }

    public int countFavorisByOeuvre(int oeuvreId) {
        String sql = "SELECT COUNT(*) FROM oeuvre_user WHERE oeuvre_id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, oeuvreId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException ignored) {
            // Keep 0 fallback.
        }
        return 0;
    }

    public boolean toggleFavori(int userId, int oeuvreId) throws SQLDataException {
        if (isFavori(userId, oeuvreId)) {
            String deleteSql = "DELETE FROM oeuvre_user WHERE user_id = ? AND oeuvre_id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(deleteSql)) {
                preparedStatement.setInt(1, userId);
                preparedStatement.setInt(2, oeuvreId);
                preparedStatement.executeUpdate();
                return false;
            } catch (SQLException e) {
                throw new SQLDataException(e.getMessage());
            }
        }

        String insertSql = "INSERT INTO oeuvre_user (user_id, oeuvre_id) VALUES (?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertSql)) {
            preparedStatement.setInt(1, userId);
            preparedStatement.setInt(2, oeuvreId);
            preparedStatement.executeUpdate();

            // Vérifier si trending après ajout de favori
            try {
                OeuvreService oeuvreService = new OeuvreService();
                oeuvreService.checkTrendingAndNotify(oeuvreId);
            } catch (Exception e) {
                System.err.println("Erreur lors de la vérification trending: " + e.getMessage());
            }

            return true;
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    private LikeEntity mapLike(ResultSet resultSet) throws SQLException {
        LikeEntity like = new LikeEntity();
        like.setId(resultSet.getInt("id"));
        like.setLiked(resultSet.getBoolean("liked"));
        like.setUserId(resultSet.getInt("user_id"));
        like.setOeuvreId(resultSet.getInt("oeuvre_id"));
        return like;
    }
}
