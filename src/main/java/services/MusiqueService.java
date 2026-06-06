package services;

import entities.Musique;
import utils.ImageUrlUtils;
import utils.MyDatabase;

import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class MusiqueService implements Iservice<Musique> {

    private static final Path IMAGE_UPLOAD_DIR = Paths.get("C:\\xampp\\htdocs\\img");

    private final Connection connection;

    public MusiqueService() {
        this.connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void add(Musique musique) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }

        String oeuvreInsert = "INSERT INTO oeuvre (titre, description, date_creation, image, type, collection_id, classe, is_public) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String musiqueInsert = "INSERT INTO musique (id, genre, audio, updated_at) VALUES (?, ?, ?, ?)";

        String imageUrl = prepareImageUrl(musique.getImage());

        try {
            connection.setAutoCommit(false);
            Integer effectiveCollectionId = resolveCollectionId(musique.getCollectionId());

            int oeuvreId;
            try (PreparedStatement oeuvreStatement = connection.prepareStatement(oeuvreInsert, Statement.RETURN_GENERATED_KEYS)) {
                oeuvreStatement.setString(1, musique.getTitre());
                oeuvreStatement.setString(2, musique.getDescription());
                oeuvreStatement.setDate(3, Date.valueOf(musique.getDateCreation()));
                oeuvreStatement.setString(4, imageUrl);
                oeuvreStatement.setString(5, musique.getType() != null ? musique.getType() : "Musique");
                oeuvreStatement.setInt(6, effectiveCollectionId);
                oeuvreStatement.setString(7, musique.getClasse() != null ? musique.getClasse() : "musique");
                oeuvreStatement.setBoolean(8, true);
                oeuvreStatement.executeUpdate();

                try (var keys = oeuvreStatement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLDataException("Impossible de recuperer l'identifiant de l'oeuvre creee.");
                    }
                    oeuvreId = keys.getInt(1);
                }
            }

            try (PreparedStatement musiqueStatement = connection.prepareStatement(musiqueInsert)) {
                musiqueStatement.setInt(1, oeuvreId);
                musiqueStatement.setString(2, musique.getGenre());
                musiqueStatement.setString(3, musique.getAudio());
                if (musique.getUpdatedAt() != null) {
                    musiqueStatement.setTimestamp(4, Timestamp.valueOf(musique.getUpdatedAt()));
                } else {
                    musiqueStatement.setTimestamp(4, null);
                }
                musiqueStatement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                throw new SQLDataException("Echec lors du rollback apres erreur d'ajout musique: " + rollbackException.getMessage());
            }
            throw new SQLDataException("Impossible d'ajouter la musique: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // Restore auto-commit best effort for next service calls.
            }
        }
    }

    private Integer resolveCollectionId(Integer requestedCollectionId) throws SQLException {
        if (requestedCollectionId != null) {
            return requestedCollectionId;
        }

        String randomCollectionQuery = "SELECT id FROM collections ORDER BY RAND() LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(randomCollectionQuery);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("id");
            }
        }

        throw new SQLException("Aucune collection disponible pour ajouter une musique.");
    }

    @Override
    public void delete(Musique musique) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }
        if (musique == null || musique.getId() == null) {
            throw new SQLDataException("Identifiant musique manquant pour la suppression.");
        }

        String deleteMusique = "DELETE FROM musique WHERE id = ?";
        String deleteOeuvre = "DELETE FROM oeuvre WHERE id = ?";

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement musiqueStatement = connection.prepareStatement(deleteMusique);
                 PreparedStatement oeuvreStatement = connection.prepareStatement(deleteOeuvre)) {
                musiqueStatement.setInt(1, musique.getId());
                musiqueStatement.executeUpdate();

                oeuvreStatement.setInt(1, musique.getId());
                int deleted = oeuvreStatement.executeUpdate();
                if (deleted == 0) {
                    throw new SQLDataException("Aucune musique trouvee avec l'identifiant " + musique.getId());
                }
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                throw new SQLDataException("Echec lors du rollback apres erreur de suppression musique: " + rollbackException.getMessage());
            }
            throw new SQLDataException("Impossible de supprimer la musique: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // Restore auto-commit best effort for next service calls.
            }
        }
    }

    @Override
    public void update(Musique musique) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }
        if (musique == null || musique.getId() == null) {
            throw new SQLDataException("Identifiant musique manquant pour la mise a jour.");
        }

        String oeuvreUpdate = "UPDATE oeuvre SET titre = ?, description = ?, date_creation = ?, image = ?, type = ?, collection_id = ?, classe = ? WHERE id = ?";
        String musiqueUpdate = "UPDATE musique SET genre = ?, audio = ?, updated_at = ? WHERE id = ?";

        String imageUrl = prepareImageUrl(musique.getImage());

        try {
            connection.setAutoCommit(false);
            Integer effectiveCollectionId = resolveCollectionIdForUpdate(musique.getCollectionId(), musique.getId());

            try (PreparedStatement oeuvreStatement = connection.prepareStatement(oeuvreUpdate);
                 PreparedStatement musiqueStatement = connection.prepareStatement(musiqueUpdate)) {

                LocalDate dateCreation = musique.getDateCreation() != null ? musique.getDateCreation() : LocalDate.now();

                oeuvreStatement.setString(1, musique.getTitre());
                oeuvreStatement.setString(2, musique.getDescription());
                oeuvreStatement.setDate(3, Date.valueOf(dateCreation));
                oeuvreStatement.setString(4, imageUrl);
                oeuvreStatement.setString(5, musique.getType() != null ? musique.getType() : "Musique");
                oeuvreStatement.setInt(6, effectiveCollectionId);
                oeuvreStatement.setString(7, musique.getClasse() != null ? musique.getClasse() : "musique");
                oeuvreStatement.setInt(8, musique.getId());
                int oeuvreUpdated = oeuvreStatement.executeUpdate();

                musiqueStatement.setString(1, musique.getGenre());
                musiqueStatement.setString(2, musique.getAudio());
                LocalDateTime updatedAt = musique.getUpdatedAt() != null ? musique.getUpdatedAt() : LocalDateTime.now();
                musiqueStatement.setTimestamp(3, Timestamp.valueOf(updatedAt));
                musiqueStatement.setInt(4, musique.getId());
                int musiqueUpdated = musiqueStatement.executeUpdate();

                if (oeuvreUpdated == 0 || musiqueUpdated == 0) {
                    throw new SQLDataException("Aucune musique trouvee avec l'identifiant " + musique.getId());
                }
            }

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                throw new SQLDataException("Echec lors du rollback apres erreur de mise a jour musique: " + rollbackException.getMessage());
            }
            throw new SQLDataException("Impossible de mettre a jour la musique: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // Restore auto-commit best effort for next service calls.
            }
        }
    }

    private Integer resolveCollectionIdForUpdate(Integer requestedCollectionId, Integer oeuvreId) throws SQLException {
        if (requestedCollectionId != null) {
            return requestedCollectionId;
        }

        String currentCollectionQuery = "SELECT collection_id FROM oeuvre WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(currentCollectionQuery)) {
            statement.setInt(1, oeuvreId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int collectionId = resultSet.getInt("collection_id");
                    if (!resultSet.wasNull()) {
                        return collectionId;
                    }
                }
            }
        }

        return resolveCollectionId(null);
    }

    private String prepareImageUrl(String rawImageValue) throws SQLDataException {
        String copiedPathOrOriginal = copyImageToWebRootIfLocal(rawImageValue);
        return ImageUrlUtils.normalizeForDatabase(copiedPathOrOriginal);
    }

    private String copyImageToWebRootIfLocal(String rawImageValue) throws SQLDataException {
        if (rawImageValue == null || rawImageValue.isBlank()) {
            return rawImageValue;
        }

        String trimmedValue = rawImageValue.trim();
        if (trimmedValue.startsWith("http://") || trimmedValue.startsWith("https://")) {
            return trimmedValue;
        }

        Path sourcePath = resolveLocalPath(trimmedValue);
        if (sourcePath == null) {
            return trimmedValue;
        }

        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new SQLDataException("Image locale introuvable: " + sourcePath);
        }

        try {
            Files.createDirectories(IMAGE_UPLOAD_DIR);
            Path targetPath = IMAGE_UPLOAD_DIR.resolve(sourcePath.getFileName().toString());
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toString();
        } catch (IOException e) {
            throw new SQLDataException("Impossible de copier l'image vers " + IMAGE_UPLOAD_DIR + ": " + e.getMessage());
        }
    }

    private Path resolveLocalPath(String rawValue) {
        try {
            if (rawValue.startsWith("file:/")) {
                return Paths.get(URI.create(rawValue));
            }
            return Paths.get(rawValue);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public List<Musique> getAll() throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }

        String query = "SELECT o.id, o.titre, o.description, o.date_creation, o.collection_id, o.type, o.classe, o.image, o.is_public, m.genre, m.audio, m.updated_at "
                + "FROM musique m INNER JOIN oeuvre o ON o.id = m.id ORDER BY o.id DESC";

        List<Musique> musiques = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Musique musique = new Musique();
                musique.setId(resultSet.getInt("id"));
                musique.setTitre(resultSet.getString("titre"));
                musique.setDescription(resultSet.getString("description"));
                Date dateCreation = resultSet.getDate("date_creation");
                if (dateCreation != null) {
                    musique.setDateCreation(dateCreation.toLocalDate());
                }
                musique.setCollectionId(resultSet.getInt("collection_id"));
                musique.setType(resultSet.getString("type"));
                musique.setClasse(resultSet.getString("classe"));
                musique.setImage(ImageUrlUtils.normalizeForDatabase(resultSet.getString("image")));
                musique.setGenre(resultSet.getString("genre"));
                musique.setAudio(resultSet.getString("audio"));
                Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                if (updatedAt != null) {
                    musique.setUpdatedAt(updatedAt.toLocalDateTime());
                }
                
                // Set is_public (default to true if column doesn't exist or is NULL)
                try {
                    musique.setPublic(resultSet.getBoolean("is_public"));
                } catch (SQLException e) {
                    // If column doesn't exist, default to true
                    musique.setPublic(true);
                }

                musiques.add(musique);
            }
        } catch (SQLException e) {
            throw new SQLDataException("Impossible de charger les musiques: " + e.getMessage());
        }

        return musiques;
    }

    public List<Musique> getByArtistId(int artistId) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }

        String query = "SELECT o.id, o.titre, o.description, o.date_creation, o.collection_id, o.type, o.classe, o.image, o.is_public, m.genre, m.audio, m.updated_at "
                + "FROM musique m "
                + "INNER JOIN oeuvre o ON o.id = m.id "
                + "INNER JOIN collections c ON c.id = o.collection_id "
                + "WHERE c.artiste_id = ? "
                + "ORDER BY o.id DESC";

        List<Musique> musiques = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, artistId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Musique musique = new Musique();
                    musique.setId(resultSet.getInt("id"));
                    musique.setTitre(resultSet.getString("titre"));
                    musique.setDescription(resultSet.getString("description"));
                    Date dateCreation = resultSet.getDate("date_creation");
                    if (dateCreation != null) {
                        musique.setDateCreation(dateCreation.toLocalDate());
                    }
                    musique.setCollectionId(resultSet.getInt("collection_id"));
                    musique.setType(resultSet.getString("type"));
                    musique.setClasse(resultSet.getString("classe"));
                    musique.setImage(ImageUrlUtils.normalizeForDatabase(resultSet.getString("image")));
                    musique.setGenre(resultSet.getString("genre"));
                    musique.setAudio(resultSet.getString("audio"));
                    Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                    if (updatedAt != null) {
                        musique.setUpdatedAt(updatedAt.toLocalDateTime());
                    }
                    
                    // Set is_public (default to true if column doesn't exist or is NULL)
                    try {
                        musique.setPublic(resultSet.getBoolean("is_public"));
                    } catch (SQLException e) {
                        // If column doesn't exist, default to true
                        musique.setPublic(true);
                    }
                    
                    musiques.add(musique);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Impossible de charger les musiques de l'artiste: " + e.getMessage());
        }

        return musiques;
    }

    @Override
    public Musique getById(int id) throws SQLDataException {
        return null;
    }
}


