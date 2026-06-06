package services;

import entities.Reponse;
import utils.MyDatabase;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public class ReponseService implements Iservice<Reponse> {

    private final Connection connection;

    private static final String INSERT_SQL = "INSERT INTO reponse (contenu, date_reponse, reclamation_id, user_admin_id) VALUES (?,?,?,?)";

    private static final String SELECT_BY_RECLAMATION_SQL = "SELECT id, contenu, date_reponse, reclamation_id, user_admin_id FROM reponse WHERE reclamation_id = ? ORDER BY id ASC";

    private static final String DELETE_BY_RECLAMATION_SQL = "DELETE FROM reponse WHERE reclamation_id = ?";

    private static final String UPDATE_SQL = "UPDATE reponse SET contenu = ?, date_reponse = ?, user_admin_id = ? WHERE id = ?";

    private static final String DELETE_BY_ID_SQL = "DELETE FROM reponse WHERE id = ?";

    private static final String SELECT_ALL_SQL = "SELECT id, contenu, date_reponse, reclamation_id, user_admin_id FROM reponse ORDER BY id ASC";

    private static final String SELECT_BY_ID_SQL = "SELECT id, contenu, date_reponse, reclamation_id, user_admin_id FROM reponse WHERE id = ?";

    public ReponseService() {
        connection = MyDatabase.getInstance().getConnection();
    }

    // ---------------- Iservice<Reponse> ----------------

    @Override
    public void add(Reponse reponse) throws SQLDataException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
            ps.setString(1, reponse.getContenu());

            LocalDate d = reponse.getDateReponse() != null ? reponse.getDateReponse() : LocalDate.now();
            ps.setDate(2, Date.valueOf(d));

            if (reponse.getReclamationId() == null) {
                throw new SQLDataException("reclamationId est obligatoire");
            }
            ps.setInt(3, reponse.getReclamationId());

            if (reponse.getUserAdminId() != null) {
                ps.setInt(4, reponse.getUserAdminId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de l'ajout de reponse: " + e.getMessage());
        }
    }

    @Override
    public void delete(Reponse reponse) throws SQLDataException {
        if (reponse == null || reponse.getId() == null) {
            throw new SQLDataException("Impossible de supprimer une reponse sans ID");
        }
        deleteById(reponse.getId());
    }

    @Override
    public void update(Reponse reponse) throws SQLDataException {
        if (reponse == null || reponse.getId() == null) {
            throw new SQLDataException("Impossible de modifier une reponse sans ID");
        }

        try (PreparedStatement ps = connection.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, reponse.getContenu());

            LocalDate d = reponse.getDateReponse() != null ? reponse.getDateReponse() : LocalDate.now();
            ps.setDate(2, Date.valueOf(d));

            if (reponse.getUserAdminId() != null) {
                ps.setInt(3, reponse.getUserAdminId());
            } else {
                ps.setNull(3, Types.INTEGER);
            }

            ps.setInt(4, reponse.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la modification de reponse: " + e.getMessage());
        }
    }

    @Override
    public List<Reponse> getAll() throws SQLDataException {
        List<Reponse> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(SELECT_ALL_SQL)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la recuperation des reponses: " + e.getMessage());
        }
        return list;
    }

    @Override
    public Reponse getById(int id) throws SQLDataException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la recuperation de la reponse: " + e.getMessage());
        }
        return null;
    }

    // ---------------- Methodes specifiques ----------------

    public List<Reponse> getByReclamationId(int reclamationId) throws SQLDataException {
        List<Reponse> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_RECLAMATION_SQL)) {
            ps.setInt(1, reclamationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la recuperation des reponses: " + e.getMessage());
        }
        return list;
    }

    public void deleteByReclamationId(Integer reclamationId) throws SQLDataException {
        if (reclamationId == null) return;
        try (PreparedStatement ps = connection.prepareStatement(DELETE_BY_RECLAMATION_SQL)) {
            ps.setInt(1, reclamationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la suppression des reponses: " + e.getMessage());
        }
    }

    public void deleteById(Integer id) throws SQLDataException {
        if (id == null) return;
        try (PreparedStatement ps = connection.prepareStatement(DELETE_BY_ID_SQL)) {
            ps.setInt(1, id);
            ps.executeUpdate();

            // In some configurations autoCommit may be disabled.
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la suppression de reponse: " + e.getMessage());
        }
    }

    private Reponse mapRow(ResultSet rs) throws SQLException {
        Reponse r = new Reponse();
        int rid = rs.getInt("id");
        if (!rs.wasNull()) {
            r.setId(rid);
        }
        r.setContenu(rs.getString("contenu"));

        Date d = rs.getDate("date_reponse");
        if (d != null) r.setDateReponse(d.toLocalDate());

        int recId = rs.getInt("reclamation_id");
        if (!rs.wasNull()) r.setReclamationId(recId);

        int adminId = rs.getInt("user_admin_id");
        if (!rs.wasNull()) r.setUserAdminId(adminId);

        return r;
    }
}



