package services;

import entities.LocationLivre;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcLocationLivreService {
    private final Connection connection;

    public JdbcLocationLivreService() {
        this.connection = MyDatabase.getInstance().getConnection();
        if (this.connection == null) {
            throw new IllegalStateException("Database connection is not available.");
        }
    }

    public void louerLivre(int livreId, int userId, int nombreDeJours) throws SQLDataException {
        if (livreId <= 0) {
            throw new SQLDataException("Livre invalide.");
        }
        if (userId <= 0) {
            throw new SQLDataException("Utilisateur invalide.");
        }
        if (nombreDeJours <= 0) {
            throw new SQLDataException("Nombre de jours invalide.");
        }

        try {
            cloturerLocationsExpirees(livreId);
            if (!isLivreDisponible(livreId)) {
                throw new SQLDataException("Ce livre est indisponible pour le moment.");
            }

            String insertSql = "INSERT INTO location_livre (date_debut, etat, nombre_de_jours, user_id, livre_id) " +
                    "VALUES (NOW(), 'Active', ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
                stmt.setInt(1, nombreDeJours);
                stmt.setInt(2, userId);
                stmt.setInt(3, livreId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SQLDataException("Location impossible: " + e.getMessage());
        }
    }

    public boolean isLivreDisponible(int livreId) throws SQLDataException {
        String sql = "SELECT CASE WHEN EXISTS (" +
                "   SELECT 1 FROM location_livre ll " +
                "   WHERE ll.livre_id = ? " +
                "     AND LOWER(ll.etat) NOT IN ('termine', 'retourne', 'cloture') " +
                "     AND DATE_ADD(ll.date_debut, INTERVAL COALESCE(ll.nombre_de_jours, 0) DAY) > NOW()" +
                ") THEN 0 ELSE 1 END AS disponible";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, livreId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return true;
                }
                return rs.getInt("disponible") == 1;
            }
        } catch (SQLException e) {
            throw new SQLDataException("Vérification disponibilité impossible: " + e.getMessage());
        }
    }

    private void cloturerLocationsExpirees(int livreId) throws SQLException {
        String sql = "UPDATE location_livre SET etat = 'termine' " +
                "WHERE livre_id = ? " +
                "  AND LOWER(etat) NOT IN ('termine', 'retourne', 'cloture') " +
                "  AND DATE_ADD(date_debut, INTERVAL COALESCE(nombre_de_jours, 0) DAY) <= NOW()";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, livreId);
            stmt.executeUpdate();
        }
    }

    public List<Integer> getRentedLivreIds(int userId) throws SQLDataException {
        String sql = "SELECT livre_id FROM location_livre " +
                "WHERE user_id = ? " +
                "  AND LOWER(etat) NOT IN ('termine', 'retourne', 'cloture') " +
                "  AND DATE_ADD(date_debut, INTERVAL COALESCE(nombre_de_jours, 0) DAY) > NOW()";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("livre_id"));
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur chargement locations: " + e.getMessage());
        }
        return ids;
    }

    public LocationLivre getActiveLocation(int livreId, int userId) throws SQLDataException {
        String sql = "SELECT id, date_debut, etat, nombre_de_jours, user_id, livre_id FROM location_livre " +
                "WHERE livre_id = ? AND user_id = ? " +
                "  AND LOWER(etat) NOT IN ('termine', 'retourne', 'cloture') " +
                "  AND DATE_ADD(date_debut, INTERVAL COALESCE(nombre_de_jours, 0) DAY) > NOW() " +
                "ORDER BY id DESC LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, livreId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    LocationLivre loc = new LocationLivre();
                    loc.setId(rs.getInt("id"));
                    java.sql.Timestamp ts = rs.getTimestamp("date_debut");
                    if (ts != null) {
                        loc.setDateDebut(ts.toLocalDateTime());
                    }
                    loc.setEtat(rs.getString("etat"));
                    loc.setNombreDeJours(rs.getInt("nombre_de_jours"));
                    loc.setUserId(rs.getInt("user_id"));
                    loc.setLivreId(rs.getInt("livre_id"));
                    return loc;
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur chargement location: " + e.getMessage());
        }
        return null;
    }
}

