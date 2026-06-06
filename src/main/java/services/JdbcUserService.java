package services;

import entities.User;
import org.mindrot.jbcrypt.BCrypt;
import utils.ImageUrlUtils;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;

public class JdbcUserService implements UserServicee {
    private final Connection connection;

    public JdbcUserService() {
        this.connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public User login(String email, String password) throws SQLDataException {
        String sql = "SELECT * FROM user WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hashed = rs.getString("mdp");
                    // PHP's password_hash uses $2y$ which BCrypt can handle after replacement to $2a$ if needed
                    // but usually jBcrypt handles $2y$ correctly if we use the right version or prefix.
                    // Actually jBcrypt 0.4 handles $2y$ natively.
                    if (BCrypt.checkpw(password, hashed.replaceFirst("^\\$2y\\$", "\\$2a\\$"))) {
                        return mapRowToUser(rs);
                    }
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Login failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public User getById(int id) throws SQLDataException {
        String sql = "SELECT * FROM user WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Failed to fetch user: " + e.getMessage());
        }
        return null;
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setNom(rs.getString("nom"));
        user.setPrenom(rs.getString("prenom"));
        user.setEmail(rs.getString("email"));
        user.setRole(rs.getString("role"));
        user.setStatut(rs.getString("statut"));
        user.setPhotoProfil(ImageUrlUtils.normalizeForDatabase(rs.getString("photo_profil")));
        user.setPhotoReferencePath(ImageUrlUtils.normalizeForDatabase(rs.getString("photo_reference_path")));
        user.setSpecialite(rs.getString("specialite"));
        return user;
    }
}
