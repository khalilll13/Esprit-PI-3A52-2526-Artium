package services;

import entities.CollectionOeuvre;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class OeuvreCollectionService implements services.Iservice<CollectionOeuvre> {
    private static final int DESCRIPTION_MIN_LENGTH = 10;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final Connection connection;

    public OeuvreCollectionService() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void add(CollectionOeuvre collectionOeuvre) throws SQLDataException {
        if (collectionOeuvre.getArtisteId() == null) {
            throw new SQLDataException("L'identifiant de l'artiste est obligatoire.");
        }

        String titreNormalise = collectionOeuvre.getTitre() == null ? "" : collectionOeuvre.getTitre().trim();
        if (titreNormalise.isEmpty()) {
            throw new SQLDataException("Le titre est obligatoire.");
        }

        String descriptionNormalisee = collectionOeuvre.getDescription() == null ? "" : collectionOeuvre.getDescription().trim();
        if (descriptionNormalisee.isEmpty()) {
            throw new SQLDataException("La description est obligatoire.");
        }
        if (descriptionNormalisee.length() < DESCRIPTION_MIN_LENGTH || descriptionNormalisee.length() > DESCRIPTION_MAX_LENGTH) {
            throw new SQLDataException("La description doit contenir entre 10 et 500 caracteres.");
        }

        try {
            int artisteId = collectionOeuvre.getArtisteId();
            if (existsByTitreAndArtisteId(titreNormalise, artisteId)) {
                throw new SQLDataException("Cette collection existe deja.");
            }


            String sql = "INSERT INTO collections (titre, description, artiste_id) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, titreNormalise);
                preparedStatement.setString(2, descriptionNormalisee);
                preparedStatement.setInt(3, artisteId);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public boolean existsByTitreAndArtisteId(String titre, int artisteId) throws SQLException {
        String sql = "SELECT 1 FROM collections WHERE artiste_id = ? AND LOWER(TRIM(titre)) = LOWER(TRIM(?)) LIMIT 1";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, artisteId);
            preparedStatement.setString(2, titre);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public void delete(CollectionOeuvre collectionOeuvre) throws SQLDataException {
        try {
            // Cascade delete: supprimer toutes les oeuvres de la collection
            // (et leurs commentaires associés)
            OeuvreService oeuvreService = new OeuvreService();
            oeuvreService.deleteByCollectionId(collectionOeuvre.getId());

            // Puis supprimer la collection elle-même
            String sql = "DELETE FROM collections WHERE id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setInt(1, collectionOeuvre.getId());
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    @Override
    public void update(CollectionOeuvre collectionOeuvre) throws SQLDataException {
        String sql = "UPDATE collections SET titre = ?, description = ? WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, collectionOeuvre.getTitre());
            preparedStatement.setString(2, collectionOeuvre.getDescription());
            preparedStatement.setInt(3, collectionOeuvre.getId());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    @Override
    public List<CollectionOeuvre> getAll() throws SQLDataException {
        List<CollectionOeuvre> collections = new ArrayList<>();
        String sql = "SELECT id, titre, description, artiste_id FROM collections";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                collections.add(mapCollection(resultSet));
            }
            return collections;
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    @Override
    public CollectionOeuvre getById(int id) throws SQLDataException {
        return null;
    }

    public List<CollectionOeuvre> getCollectionsByArtisteId(int artisteId) throws SQLException {
        List<CollectionOeuvre> collections = new ArrayList<>();
        String sql = "SELECT id, titre, description, artiste_id FROM collections WHERE artiste_id = ? ORDER BY id DESC";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, artisteId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    collections.add(mapCollection(resultSet));
                }
            }
        }
        return collections;
    }

    public CollectionOeuvre getCollectionById(int collectionId) throws SQLException {
        String sql = "SELECT id, titre, description, artiste_id FROM collections WHERE id = ? LIMIT 1";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, collectionId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return mapCollection(resultSet);
                }
            }
        }
        return null;
    }

    public List<String> getOeuvreImagesByCollectionId(int collectionId, int limit) throws SQLException {
        List<String> images = new ArrayList<>();
        int safeLimit = limit <= 0 ? 6 : limit;
        String sql = "SELECT image FROM oeuvre WHERE collection_id = ? AND image IS NOT NULL ORDER BY id DESC LIMIT ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, collectionId);
            preparedStatement.setInt(2, safeLimit);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String image = resultSet.getString("image");
                    if (image != null && !image.isBlank()) {
                        images.add(image);
                    }
                }
            }
        }

        return images;
    }

    private CollectionOeuvre mapCollection(ResultSet resultSet) throws SQLException {
        CollectionOeuvre collection = new CollectionOeuvre();
        collection.setId(resultSet.getInt("id"));
        collection.setTitre(resultSet.getString("titre"));
        collection.setDescription(resultSet.getString("description"));
        collection.setArtisteId(resultSet.getInt("artiste_id"));
        return collection;
    }
}
