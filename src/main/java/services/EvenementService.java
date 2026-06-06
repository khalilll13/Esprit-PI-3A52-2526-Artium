package services;

import entities.Evenement;
import utils.ImageUrlUtils;
import utils.MyDatabase;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EvenementService implements Iservice<Evenement> {

    private static final String INSERT_SQL = "INSERT INTO evenement (titre, description, date_debut, date_fin, date_creation, type, image_couverture, statut, capacite_max, prix_ticket, galerie_id, artiste_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE evenement SET titre = ?, description = ?, date_debut = ?, date_fin = ?, date_creation = ?, type = ?, image_couverture = ?, statut = ?, capacite_max = ?, prix_ticket = ?, galerie_id = ?, artiste_id = ? WHERE id = ?";
    private static final String SELECT_BY_ID_SQL = "SELECT id, titre, description, date_debut, date_fin, date_creation, type, image_couverture, statut, capacite_max, prix_ticket, galerie_id, artiste_id FROM evenement WHERE id = ?";
    private static final String SELECT_ALL_SQL = "SELECT id, titre, description, date_debut, date_fin, date_creation, type, image_couverture, statut, capacite_max, prix_ticket, galerie_id, artiste_id FROM evenement ORDER BY id DESC";
    private static final String SELECT_BY_ARTIST_SQL = "SELECT id, titre, description, date_debut, date_fin, date_creation, type, image_couverture, statut, capacite_max, prix_ticket, galerie_id, artiste_id FROM evenement WHERE artiste_id = ? ORDER BY date_debut DESC, id DESC";
    private static final String DELETE_SQL = "DELETE FROM evenement WHERE id = ?";
    private static final String DELETE_BY_ARTIST_SQL = "DELETE FROM evenement WHERE id = ? AND artiste_id = ?";
    private static final String UPDATE_BY_ARTIST_SQL = "UPDATE evenement SET titre = ?, description = ?, date_debut = ?, date_fin = ?, date_creation = ?, type = ?, image_couverture = ?, statut = ?, capacite_max = ?, prix_ticket = ?, galerie_id = ? WHERE id = ? AND artiste_id = ?";

    @Override
    public void add(Evenement evenement) throws SQLDataException {
        validateForWrite(evenement, true);
        String imageUrl = prepareImageUrl(evenement.getImageCouverture());

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(INSERT_SQL)) {
            bindWriteFields(statement, evenement, imageUrl, false);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de l'ajout de l'evenement: " + e.getMessage());
        }
    }

    @Override
    public void delete(Evenement evenement) throws SQLDataException {
        if (evenement == null || evenement.getId() == null) {
            throw new SQLDataException("Impossible de supprimer un evenement sans ID");
        }

        // Cascade delete: supprimer les tickets associés
        TicketService ticketService = new TicketService();
        ticketService.deleteTicketsByEvent(evenement.getId());

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(DELETE_SQL)) {
            statement.setInt(1, evenement.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la suppression de l'evenement: " + e.getMessage());
        }
    }

    @Override
    public void update(Evenement evenement) throws SQLDataException {
        validateForWrite(evenement, false);
        String imageUrl = prepareImageUrl(evenement.getImageCouverture());

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(UPDATE_SQL)) {
            bindWriteFields(statement, evenement, imageUrl, true);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la modification de l'evenement: " + e.getMessage());
        }
    }

    @Override
    public List<Evenement> getAll() throws SQLDataException {
        List<Evenement> evenements = new ArrayList<>();

        try (Statement statement = MyDatabase.getInstance().getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_ALL_SQL)) {

            while (resultSet.next()) {
                evenements.add(mapEvenement(resultSet));
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la recuperation des evenements: " + e.getMessage());
        }

        return evenements;
    }

    @Override
    public Evenement getById(int id) throws SQLDataException {
        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapEvenement(resultSet);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la recuperation de l'evenement: " + e.getMessage());
        }

        return null;
    }

    public List<Evenement> getByArtisteId(int artisteId) throws SQLDataException {
        List<Evenement> evenements = new ArrayList<>();

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(SELECT_BY_ARTIST_SQL)) {
            statement.setInt(1, artisteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    evenements.add(mapEvenement(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la recuperation des evenements artiste: " + e.getMessage());
        }

        return evenements;
    }

    public void addForArtiste(Evenement evenement, int artisteId) throws SQLDataException {
        evenement.setArtisteId(artisteId);
        add(evenement);
    }

    public void updateForArtiste(Evenement evenement, int artisteId) throws SQLDataException {
        if (evenement == null || evenement.getId() == null) {
            throw new SQLDataException("Impossible de modifier un evenement sans ID");
        }

        evenement.setArtisteId(artisteId);
        validateForWrite(evenement, false);
        String imageUrl = prepareImageUrl(evenement.getImageCouverture());

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(UPDATE_BY_ARTIST_SQL)) {
            statement.setString(1, evenement.getTitre());
            statement.setString(2, evenement.getDescription());
            statement.setTimestamp(3, Timestamp.valueOf(evenement.getDateDebut()));
            statement.setTimestamp(4, Timestamp.valueOf(evenement.getDateFin()));
            statement.setDate(5, evenement.getDateCreation() == null ? null : Date.valueOf(evenement.getDateCreation()));
            statement.setString(6, evenement.getType());
            statement.setString(7, imageUrl);
            statement.setString(8, evenement.getStatut());

            if (evenement.getCapaciteMax() == null) {
                statement.setNull(9, java.sql.Types.INTEGER);
            } else {
                statement.setInt(9, evenement.getCapaciteMax());
            }

            if (evenement.getPrixTicket() == null) {
                statement.setNull(10, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(10, evenement.getPrixTicket());
            }

            if (evenement.getGalerieId() == null) {
                statement.setNull(11, java.sql.Types.INTEGER);
            } else {
                statement.setInt(11, evenement.getGalerieId());
            }

            statement.setInt(12, evenement.getId());
            statement.setInt(13, artisteId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la modification de l'evenement artiste: " + e.getMessage());
        }
    }

    public void deleteForArtiste(Evenement evenement, int artisteId) throws SQLDataException {
        if (evenement == null || evenement.getId() == null) {
            throw new SQLDataException("Impossible de supprimer un evenement sans ID");
        }

        // Cascade delete: supprimer les tickets associés
        TicketService ticketService = new TicketService();
        ticketService.deleteTicketsByEvent(evenement.getId());

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(DELETE_BY_ARTIST_SQL)) {
            statement.setInt(1, evenement.getId());
            statement.setInt(2, artisteId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la suppression de l'evenement artiste: " + e.getMessage());
        }
    }

    private Evenement mapEvenement(ResultSet resultSet) throws SQLException {
        Evenement evenement = new Evenement();
        evenement.setId(resultSet.getInt("id"));
        evenement.setTitre(resultSet.getString("titre"));
        evenement.setDescription(resultSet.getString("description"));

        Timestamp dateDebut = resultSet.getTimestamp("date_debut");
        evenement.setDateDebut(dateDebut == null ? null : dateDebut.toLocalDateTime());

        Timestamp dateFin = resultSet.getTimestamp("date_fin");
        evenement.setDateFin(dateFin == null ? null : dateFin.toLocalDateTime());

        Date dateCreation = resultSet.getDate("date_creation");
        evenement.setDateCreation(dateCreation == null ? null : dateCreation.toLocalDate());

        evenement.setType(resultSet.getString("type"));
        evenement.setImageCouverture(ImageUrlUtils.normalizeForDatabase(resultSet.getString("image_couverture")));
        evenement.setStatut(computeDynamicStatut(
                dateDebut == null ? null : dateDebut.toLocalDateTime(),
                dateFin == null ? null : dateFin.toLocalDateTime(),
                resultSet.getString("statut")
        ));

        Number capaciteMax = (Number) resultSet.getObject("capacite_max");
        evenement.setCapaciteMax(capaciteMax == null ? null : capaciteMax.intValue());

        Number prixTicket = (Number) resultSet.getObject("prix_ticket");
        evenement.setPrixTicket(prixTicket == null ? null : prixTicket.doubleValue());

        Number galerieId = (Number) resultSet.getObject("galerie_id");
        evenement.setGalerieId(galerieId == null ? null : galerieId.intValue());

        Number artisteId = (Number) resultSet.getObject("artiste_id");
        evenement.setArtisteId(artisteId == null ? null : artisteId.intValue());
        return evenement;
    }

    private void bindWriteFields(PreparedStatement statement, Evenement evenement, String imageUrl, boolean withIdAtEnd) throws SQLException {
        statement.setString(1, evenement.getTitre());
        statement.setString(2, evenement.getDescription());
        statement.setTimestamp(3, Timestamp.valueOf(evenement.getDateDebut()));
        statement.setTimestamp(4, Timestamp.valueOf(evenement.getDateFin()));
        statement.setDate(5, evenement.getDateCreation() == null ? null : Date.valueOf(evenement.getDateCreation()));
        statement.setString(6, evenement.getType());
        statement.setString(7, imageUrl);
        statement.setString(8, evenement.getStatut());

        if (evenement.getCapaciteMax() == null) {
            statement.setNull(9, java.sql.Types.INTEGER);
        } else {
            statement.setInt(9, evenement.getCapaciteMax());
        }

        if (evenement.getPrixTicket() == null) {
            statement.setNull(10, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(10, evenement.getPrixTicket());
        }

        if (evenement.getGalerieId() == null) {
            statement.setNull(11, java.sql.Types.INTEGER);
        } else {
            statement.setInt(11, evenement.getGalerieId());
        }

        if (evenement.getArtisteId() == null) {
            statement.setNull(12, java.sql.Types.INTEGER);
        } else {
            statement.setInt(12, evenement.getArtisteId());
        }

        if (withIdAtEnd) {
            statement.setInt(13, evenement.getId());
        }
    }

    private String prepareImageUrl(String rawImageValue) throws SQLDataException {
        return ImageUrlUtils.persistToWebImageDirectoryAndNormalize(rawImageValue);
    }

    private void validateForWrite(Evenement evenement, boolean isCreate) throws SQLDataException {
        if (evenement == null) {
            throw new SQLDataException("Evenement invalide");
        }
        if (!isCreate && evenement.getId() == null) {
            throw new SQLDataException("Impossible de modifier un evenement sans ID");
        }
        if (evenement.getTitre() == null || evenement.getTitre().isBlank()) {
            throw new SQLDataException("Le titre de l'evenement est obligatoire");
        }
        if (evenement.getDateDebut() == null || evenement.getDateFin() == null) {
            throw new SQLDataException("Les dates debut/fin sont obligatoires");
        }
        if (evenement.getDateFin().isBefore(evenement.getDateDebut())) {
            throw new SQLDataException("La date de fin doit etre apres la date de debut");
        }
        if (isCreate && evenement.getDateDebut().toLocalDate().isBefore(java.time.LocalDate.now().plusDays(3))) {
            throw new SQLDataException("La date de debut doit etre au moins 3 jours apres aujourd'hui");
        }
        if (evenement.getType() == null || evenement.getType().isBlank()) {
            throw new SQLDataException("Le type de l'evenement est obligatoire");
        }
        if (evenement.getStatut() == null || evenement.getStatut().isBlank()) {
            evenement.setStatut("À venir");
        }
    }

    private String computeDynamicStatut(LocalDateTime dateDebut, LocalDateTime dateFin, String fallback) {
        if (fallback != null && fallback.equalsIgnoreCase("Annulé")) {
            return "Annulé";
        }

        if (dateDebut == null || dateFin == null) {
            return fallback == null || fallback.isBlank() ? "À venir" : fallback;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dateDebut)) {
            return "À venir";
        }
        if (now.isAfter(dateFin)) {
            return "Terminé";
        }
        return "À venir";
    }
}


