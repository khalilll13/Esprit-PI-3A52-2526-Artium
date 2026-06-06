package services;

import entities.Galerie;
import utils.MyDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class GalerieService implements Iservice<Galerie> {

    private static final String INSERT_SQL = "INSERT INTO galerie (nom, adresse, localisation, description, capacite_max) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE galerie SET nom = ?, adresse = ?, localisation = ?, description = ?, capacite_max = ? WHERE id = ?";
    private static final String SELECT_ALL_SQL = "SELECT id, nom, adresse, localisation, description, capacite_max FROM galerie";
    private static final String SELECT_BY_ID_SQL = "SELECT id, nom, adresse, localisation, description, capacite_max FROM galerie WHERE id = ?";
    private static final String DELETE_SQL = "DELETE FROM galerie WHERE id = ?";

    @Override
    public void add(Galerie galerie) throws SQLDataException {
        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(INSERT_SQL)) {
            statement.setString(1, galerie.getNom());
            statement.setString(2, galerie.getAdresse());
            statement.setString(3, galerie.getLocalisation());
            statement.setString(4, galerie.getDescription());
            statement.setInt(5, galerie.getCapaciteMax());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de l'ajout de galerie: " + e.getMessage());
        }
    }

    @Override
    public void delete(Galerie galerie) throws SQLDataException {
        if (galerie == null || galerie.getId() == null) {
            throw new SQLDataException("Impossible de supprimer une galerie sans ID");
        }

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(DELETE_SQL)) {
            statement.setInt(1, galerie.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la suppression de la galerie: " + e.getMessage());
        }
    }

    @Override
    public void update(Galerie galerie) throws SQLDataException {
        if (galerie == null || galerie.getId() == null) {
            throw new SQLDataException("Impossible de modifier une galerie sans ID");
        }

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(UPDATE_SQL)) {
            statement.setString(1, galerie.getNom());
            statement.setString(2, galerie.getAdresse());
            statement.setString(3, galerie.getLocalisation());
            statement.setString(4, galerie.getDescription());
            statement.setInt(5, galerie.getCapaciteMax());
            statement.setInt(6, galerie.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la modification de la galerie: " + e.getMessage());
        }
    }

    @Override
    public List<Galerie> getAll() throws SQLDataException {
        List<Galerie> galeries = new ArrayList<>();

        try (Statement statement = MyDatabase.getInstance().getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_ALL_SQL)) {

            while (resultSet.next()) {
                Galerie galerie = new Galerie();
                galerie.setId(resultSet.getInt("id"));
                galerie.setNom(resultSet.getString("nom"));
                galerie.setAdresse(resultSet.getString("adresse"));
                galerie.setLocalisation(resultSet.getString("localisation"));
                galerie.setDescription(resultSet.getString("description"));
                galerie.setCapaciteMax(resultSet.getInt("capacite_max"));
                galeries.add(galerie);
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la recuperation des galeries: " + e.getMessage());
        }

        return galeries;
    }

    @Override
    public Galerie getById(int id) throws SQLDataException {
        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Galerie galerie = new Galerie();
                    galerie.setId(resultSet.getInt("id"));
                    galerie.setNom(resultSet.getString("nom"));
                    galerie.setAdresse(resultSet.getString("adresse"));
                    galerie.setLocalisation(resultSet.getString("localisation"));
                    galerie.setDescription(resultSet.getString("description"));
                    galerie.setCapaciteMax(resultSet.getInt("capacite_max"));
                    return galerie;
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la recuperation de la galerie: " + e.getMessage());
        }

        return null;
    }
}

