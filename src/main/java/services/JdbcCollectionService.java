package services;

import entities.CollectionOeuvre;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcCollectionService implements CollectionService {

    private Connection getConnection() {
        Connection conn = MyDatabase.getInstance().getConnection();
        if (conn == null) {
            throw new IllegalStateException("Database connection is not available. Please check MyDatabase.java and your MySQL server.");
        }
        return conn;
    }

    @Override
    public List<CollectionOeuvre> getAll() throws SQLDataException {
        String sql = "SELECT id, titre, description, artiste_id FROM collections ORDER BY id ASC";
        List<CollectionOeuvre> collections = new ArrayList<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                CollectionOeuvre collection = new CollectionOeuvre();
                collection.setId(rs.getInt("id"));
                collection.setTitre(rs.getString("titre"));
                collection.setDescription(rs.getString("description"));
                collection.setArtisteId(rs.getInt("artiste_id"));
                collections.add(collection);
            }
        } catch (SQLException e) {
            throw new SQLDataException("Failed to load collections: " + e.getMessage());
        }
        return collections;
    }

    @Override
    public List<CollectionOeuvre> getByArtist(int artistId) throws SQLDataException {
        String sql = "SELECT id, titre, description, artiste_id FROM collections WHERE artiste_id = ? ORDER BY id ASC";
        List<CollectionOeuvre> collections = new ArrayList<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, artistId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CollectionOeuvre collection = new CollectionOeuvre();
                    collection.setId(rs.getInt("id"));
                    collection.setTitre(rs.getString("titre"));
                    collection.setDescription(rs.getString("description"));
                    collection.setArtisteId(rs.getInt("artiste_id"));
                    collections.add(collection);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Failed to load artist's collections: " + e.getMessage());
        }
        return collections;
    }
}
