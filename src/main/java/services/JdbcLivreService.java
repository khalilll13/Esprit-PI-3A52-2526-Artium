package services;

import entities.Livre;
import utils.ImageUrlUtils;
import utils.MyDatabase;
import utils.PdfUrlUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JdbcLivreService implements LivreService {

    private Connection getConnection() {
        Connection conn = MyDatabase.getInstance().getConnection();
        if (conn == null) {
            throw new IllegalStateException("Database connection is not available. Please check MyDatabase.java and your MySQL server.");
        }
        return conn;
    }

    @Override
    public void add(Livre livre) throws SQLDataException {
        String insertOeuvreSql = "INSERT INTO oeuvre (titre, description, date_creation, image, type, embedding, image_embedding, collection_id, classe, is_public) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String insertLivreSql = "INSERT INTO livre (categorie, prix_location, fichier_pdf, id) VALUES (?, ?, ?, ?)";

        try (PreparedStatement insertOeuvre = getConnection().prepareStatement(insertOeuvreSql, Statement.RETURN_GENERATED_KEYS)) {
            insertOeuvre.setString(1, livre.getTitre());
            insertOeuvre.setString(2, safeString(livre.getDescription()));
            insertOeuvre.setDate(3, java.sql.Date.valueOf(safeDate(livre.getDateCreation())));
            insertOeuvre.setString(4, safeImageUrl(livre.getImage()));
            insertOeuvre.setString(5, "Livre");
            insertOeuvre.setNull(6, Types.LONGVARCHAR);
            insertOeuvre.setNull(7, Types.LONGVARCHAR);
            insertOeuvre.setInt(8, safeCollectionId(livre.getCollectionId()));
            insertOeuvre.setString(9, "livre");
            insertOeuvre.setBoolean(10, true);

            int affectedRows = insertOeuvre.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLDataException("Failed to insert oeuvre.");
            }

            try (ResultSet keys = insertOeuvre.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLDataException("Failed to retrieve generated id for oeuvre.");
                }
                int generatedId = keys.getInt(1);

                try (PreparedStatement insertLivre = getConnection().prepareStatement(insertLivreSql)) {
                    insertLivre.setString(1, safeString(livre.getCategorie()));
                    insertLivre.setDouble(2, safePrix(livre.getPrixLocation()));
                    insertLivre.setString(3, safePdfUrl(livre.getFichierPdf()));
                    insertLivre.setInt(4, generatedId);
                    insertLivre.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Failed to add livre: " + e.getMessage());
        }
    }

    @Override
    public void update(Livre livre) throws SQLDataException {
        if (livre.getId() == null) {
            throw new SQLDataException("Livre id is required for update.");
        }

        String updateOeuvreSql = "UPDATE oeuvre SET titre = ?, description = ?, date_creation = ?, collection_id = ?, image = ? WHERE id = ?";
        String updateLivreSql = "UPDATE livre SET categorie = ?, prix_location = ?, fichier_pdf = ? WHERE id = ?";

        try (PreparedStatement updateOeuvre = getConnection().prepareStatement(updateOeuvreSql);
             PreparedStatement updateLivre = getConnection().prepareStatement(updateLivreSql)) {
            updateOeuvre.setString(1, livre.getTitre());
            updateOeuvre.setString(2, safeString(livre.getDescription()));
            updateOeuvre.setDate(3, java.sql.Date.valueOf(safeDate(livre.getDateCreation())));
            updateOeuvre.setInt(4, safeCollectionId(livre.getCollectionId()));
            updateOeuvre.setString(5, safeImageUrl(livre.getImage()));
            updateOeuvre.setInt(6, livre.getId());
            updateOeuvre.executeUpdate();

            updateLivre.setString(1, safeString(livre.getCategorie()));
            updateLivre.setDouble(2, safePrix(livre.getPrixLocation()));
            updateLivre.setString(3, safePdfUrl(livre.getFichierPdf()));
            updateLivre.setInt(4, livre.getId());
            updateLivre.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Failed to update livre: " + e.getMessage());
        }
    }

    @Override
    public void delete(int livreId) throws SQLDataException {
        String deleteSql = "DELETE FROM oeuvre WHERE id = ?";
        try (PreparedStatement deleteStmt = getConnection().prepareStatement(deleteSql)) {
            deleteStmt.setInt(1, livreId);
            deleteStmt.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Failed to delete livre: " + e.getMessage());
        }
    }

    @Override
    public List<Livre> getAll() throws SQLDataException {
        return search(null, null);
    }

    @Override
    public List<Livre> getByArtist(int artistId) throws SQLDataException {
        String baseSql = "SELECT o.id, o.titre, o.description, o.date_creation, o.collection_id, o.image, o.is_public, " +
                "l.categorie, l.prix_location, l.fichier_pdf, " +
                "CONCAT(u.prenom, ' ', u.nom) AS auteur, " +
                "CASE WHEN EXISTS (" +
                "   SELECT 1 FROM location_livre ll " +
                "   WHERE ll.livre_id = o.id " +
                "     AND LOWER(ll.etat) NOT IN ('termine', 'retourne', 'cloture') " +
                "     AND DATE_ADD(ll.date_debut, INTERVAL COALESCE(ll.nombre_de_jours, 0) DAY) > NOW()" +
                ") THEN 0 ELSE 1 END AS disponible " +
                "FROM oeuvre o " +
                "JOIN livre l ON l.id = o.id " +
                "LEFT JOIN collections c ON c.id = o.collection_id " +
                "LEFT JOIN `user` u ON u.id = c.artiste_id " +
                "WHERE o.classe = 'livre' AND c.artiste_id = ? " +
                "ORDER BY o.id DESC";

        try (PreparedStatement stmt = getConnection().prepareStatement(baseSql)) {
            stmt.setInt(1, artistId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Livre> livres = new ArrayList<>();
                while (rs.next()) {
                    livres.add(mapRowToLivre(rs));
                }
                return livres;
            }
        } catch (SQLException e) {
            throw new SQLDataException("Failed to load artist's livres: " + e.getMessage());
        }
    }

    @Override
    public List<Livre> search(String searchText, String categorie) throws SQLDataException {
        String baseSql = "SELECT o.id, o.titre, o.description, o.date_creation, o.collection_id, o.image, o.is_public, " +
                "l.categorie, l.prix_location, l.fichier_pdf, " +
                "CONCAT(u.prenom, ' ', u.nom) AS auteur, " +
                "CASE WHEN EXISTS (" +
                "   SELECT 1 FROM location_livre ll " +
                "   WHERE ll.livre_id = o.id " +
                "     AND LOWER(ll.etat) NOT IN ('termine', 'retourne', 'cloture') " +
                "     AND DATE_ADD(ll.date_debut, INTERVAL COALESCE(ll.nombre_de_jours, 0) DAY) > NOW()" +
                ") THEN 0 ELSE 1 END AS disponible " +
                "FROM oeuvre o " +
                "JOIN livre l ON l.id = o.id " +
                "LEFT JOIN collections c ON c.id = o.collection_id " +
                "LEFT JOIN `user` u ON u.id = c.artiste_id " +
                "WHERE o.classe = 'livre'";

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(baseSql);

        if (searchText != null && !searchText.isBlank()) {
            sql.append(" AND (LOWER(o.titre) LIKE ? OR LOWER(l.categorie) LIKE ?)");
            String like = "%" + searchText.toLowerCase() + "%";
            params.add(like);
            params.add(like);
        }

        if (categorie != null && !categorie.isBlank()) {
            sql.append(" AND LOWER(l.categorie) = ?");
            params.add(categorie.toLowerCase());
        }

        sql.append(" ORDER BY o.id DESC");

        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<Livre> livres = new ArrayList<>();
                while (rs.next()) {
                    livres.add(mapRowToLivre(rs));
                }
                return livres;
            }
        } catch (SQLException e) {
            throw new SQLDataException("Failed to load livres: " + e.getMessage());
        }
    }

    private Livre mapRowToLivre(ResultSet rs) throws SQLException {
        Livre livre = new Livre();
        livre.setId(rs.getInt("id"));
        livre.setTitre(rs.getString("titre"));
        livre.setDescription(rs.getString("description"));
        java.sql.Date date = rs.getDate("date_creation");
        if (date != null) {
            livre.setDateCreation(date.toLocalDate());
        }
        livre.setCollectionId(rs.getInt("collection_id"));
        livre.setCategorie(rs.getString("categorie"));
        livre.setPrixLocation(rs.getDouble("prix_location"));
        livre.setAuteur(rs.getString("auteur"));
        livre.setDisponibilite(rs.getInt("disponible") == 1);
        livre.setImage(ImageUrlUtils.normalizeForDatabase(rs.getString("image")));
        livre.setFichierPdf(PdfUrlUtils.normalizeForDatabase(rs.getString("fichier_pdf")));
        
        // Set is_public (default to true if column doesn't exist or is NULL)
        try {
            livre.setPublic(rs.getBoolean("is_public"));
        } catch (SQLException e) {
            // If column doesn't exist, default to true
            livre.setPublic(true);
        }
        
        return livre;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static LocalDate safeDate(LocalDate value) {
        return value == null ? LocalDate.now() : value;
    }


    private static String safeImageUrl(String imageValue) throws SQLDataException {
        return ImageUrlUtils.persistToWebImageDirectoryAndNormalize(imageValue);
    }

    private static String safePdfUrl(String pdfValue) throws SQLDataException {
        return PdfUrlUtils.persistToWebPdfDirectoryAndNormalize(pdfValue);
    }

    private static double safePrix(Double prix) {
        return prix == null ? 0.0 : prix;
    }

    private static int safeCollectionId(Integer collectionId) {
        return collectionId == null || collectionId <= 0 ? 1 : collectionId;
    }
}
