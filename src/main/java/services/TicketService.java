package services;

import entities.Evenement;
import entities.Ticket;
import utils.MyDatabase;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TicketService {

    private static final String INSERT_SQL = "INSERT INTO ticket (code_qr, date_achat, evenement_id, user_id) VALUES (?, ?, ?, ?)";
    private static final String SELECT_BY_EVENT_AND_USER_SQL = "SELECT code_qr, date_achat, evenement_id, user_id FROM ticket WHERE evenement_id = ? AND user_id = ? ORDER BY date_achat DESC";
    private static final String SELECT_BY_EVENT_SQL = "SELECT code_qr, date_achat, evenement_id, user_id FROM ticket WHERE evenement_id = ? ORDER BY date_achat DESC";
    private static final String DELETE_BY_EVENT_SQL = "DELETE FROM ticket WHERE evenement_id = ?";

    public Ticket purchaseTicket(Evenement evenement, int userId) throws SQLDataException {
        if (evenement == null || evenement.getId() == null) {
            throw new SQLDataException("Evenement invalide pour l'achat du ticket");
        }

        Ticket ticket = new Ticket();
        ticket.setEvenementId(evenement.getId());
        ticket.setUserId(userId);
        ticket.setDateAchat(LocalDate.now());
        ticket.setCodeQr(generateQrCode(evenement, userId));

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(INSERT_SQL)) {
            statement.setString(1, ticket.getCodeQr());
            statement.setDate(2, Date.valueOf(ticket.getDateAchat()));
            statement.setInt(3, ticket.getEvenementId());
            statement.setInt(4, ticket.getUserId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de l'achat du ticket: " + e.getMessage());
        }

        return ticket;
    }

    private String generateQrCode(Evenement evenement, int userId) {
        return "TCK_" + UUID.randomUUID();
    }

    public List<Ticket> getTicketsByEventAndUser(int eventId, int userId) throws SQLDataException {
        List<Ticket> tickets = new ArrayList<>();

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(SELECT_BY_EVENT_AND_USER_SQL)) {
            statement.setInt(1, eventId);
            statement.setInt(2, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Ticket ticket = new Ticket();
                    ticket.setCodeQr(resultSet.getString("code_qr"));

                    Date dateAchat = resultSet.getDate("date_achat");
                    ticket.setDateAchat(dateAchat == null ? null : dateAchat.toLocalDate());

                    ticket.setEvenementId(resultSet.getInt("evenement_id"));
                    ticket.setUserId(resultSet.getInt("user_id"));
                    tickets.add(ticket);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors du chargement des tickets: " + e.getMessage());
        }

        return tickets;
    }

    public List<Ticket> getTicketsByEvent(int eventId) throws SQLDataException {
        List<Ticket> tickets = new ArrayList<>();

        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(SELECT_BY_EVENT_SQL)) {
            statement.setInt(1, eventId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tickets.add(mapTicket(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors du chargement des tickets de l'evenement: " + e.getMessage());
        }

        return tickets;
    }

    public int deleteTicketsByEvent(int eventId) throws SQLDataException {
        try (PreparedStatement statement = MyDatabase.getInstance().getConnection().prepareStatement(DELETE_BY_EVENT_SQL)) {
            statement.setInt(1, eventId);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors du remboursement des tickets: " + e.getMessage());
        }
    }

    private Ticket mapTicket(ResultSet resultSet) throws SQLException {
        Ticket ticket = new Ticket();
        ticket.setCodeQr(resultSet.getString("code_qr"));

        Date dateAchat = resultSet.getDate("date_achat");
        ticket.setDateAchat(dateAchat == null ? null : dateAchat.toLocalDate());

        ticket.setEvenementId(resultSet.getInt("evenement_id"));
        ticket.setUserId(resultSet.getInt("user_id"));
        return ticket;
    }
}

