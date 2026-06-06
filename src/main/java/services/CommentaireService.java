package services;

import entities.Commentaire;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.List;

public class CommentaireService implements services.Iservice<Commentaire> {

    private final Connection connection;

    public CommentaireService() {
        this.connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void add(Commentaire commentaire) throws SQLDataException {
        if (commentaire == null) {
            throw new SQLDataException("Commentaire invalide.");
        }

        String text = trimOrEmpty(commentaire.getTexte());
        if (text.isEmpty()) {
            throw new SQLDataException("Le commentaire est obligatoire.");
        }
        if (commentaire.getOeuvreId() == null) {
            throw new SQLDataException("L'oeuvre est obligatoire.");
        }
        if (commentaire.getUserId() == null) {
            throw new SQLDataException("L'utilisateur est obligatoire.");
        }

        Date commentDate = Date.valueOf(commentaire.getDateCommentaire() == null
                ? java.time.LocalDate.now()
                : commentaire.getDateCommentaire());

        String[] tableCandidates = new String[] {"commentaire", "commentaires", "comments"};
        String[] textColumnCandidates = new String[] {"texte", "contenu", "content", "commentaire"};
        String[] oeuvreColumnCandidates = new String[] {"oeuvre_id", "oeuvreId"};
        String[] userColumnCandidates = new String[] {"user_id", "userId", "auteur_id"};
        String[] dateColumnCandidates = new String[] {"date_commentaire", "date_creation", "created_at", "createdAt"};

        for (String table : tableCandidates) {
            for (String textColumn : textColumnCandidates) {
                for (String oeuvreColumn : oeuvreColumnCandidates) {
                    for (String userColumn : userColumnCandidates) {
                        for (String dateColumn : dateColumnCandidates) {
                            String sql = "INSERT INTO " + table + " (" + textColumn + ", " + oeuvreColumn + ", " + userColumn + ", " + dateColumn + ") VALUES (?, ?, ?, ?)";
                            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                                preparedStatement.setString(1, text);
                                preparedStatement.setInt(2, commentaire.getOeuvreId());
                                preparedStatement.setInt(3, commentaire.getUserId());
                                preparedStatement.setDate(4, commentDate);
                                preparedStatement.executeUpdate();

                                // Vérifier si trending après ajout de commentaire
                                try {
                                    OeuvreService oeuvreService = new OeuvreService();
                                    oeuvreService.checkTrendingAndNotify(commentaire.getOeuvreId());
                                } catch (Exception e) {
                                    System.err.println("Erreur lors de la vérification trending: " + e.getMessage());
                                }

                                return;
                            } catch (SQLException ignored) {
                                // Essayer prochaine combinaison table/colonnes.
                            }
                        }
                    }
                }
            }
        }

        throw new SQLDataException("Impossible d'ajouter le commentaire.");
    }

    @Override
    public void delete(Commentaire commentaire) throws SQLDataException {
        if (commentaire == null || commentaire.getId() == null) {
            throw new SQLDataException("L'identifiant du commentaire est obligatoire.");
        }

        String[] tableCandidates = new String[] {"commentaire", "commentaires", "comments"};
        for (String table : tableCandidates) {
            String sql = "DELETE FROM " + table + " WHERE id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setInt(1, commentaire.getId());
                preparedStatement.executeUpdate();
                return;
            } catch (SQLException ignored) {
                // Essayer la prochaine table.
            }
        }

        throw new SQLDataException("Impossible de supprimer le commentaire.");
    }

    @Override
    public void update(Commentaire commentaire) throws SQLDataException {
        if (commentaire == null || commentaire.getId() == null) {
            throw new SQLDataException("L'identifiant du commentaire est obligatoire.");
        }

        String text = trimOrEmpty(commentaire.getTexte());
        if (text.isEmpty()) {
            throw new SQLDataException("Le commentaire est obligatoire.");
        }

        Date commentDate = Date.valueOf(commentaire.getDateCommentaire() == null
                ? java.time.LocalDate.now()
                : commentaire.getDateCommentaire());

        String[] tableCandidates = new String[] {"commentaire", "commentaires", "comments"};
        String[] textColumnCandidates = new String[] {"texte", "contenu", "content", "commentaire"};
        String[] dateColumnCandidates = new String[] {"date_commentaire", "date_creation", "created_at", "createdAt"};

        for (String table : tableCandidates) {
            for (String textColumn : textColumnCandidates) {
                for (String dateColumn : dateColumnCandidates) {
                    String sql = "UPDATE " + table + " SET " + textColumn + " = ?, " + dateColumn + " = ? WHERE id = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                        preparedStatement.setString(1, text);
                        preparedStatement.setDate(2, commentDate);
                        preparedStatement.setInt(3, commentaire.getId());
                        preparedStatement.executeUpdate();
                        return;
                    } catch (SQLException ignored) {
                        // Essayer la prochaine combinaison table/colonnes.
                    }
                }
            }
        }

        throw new SQLDataException("Impossible de modifier le commentaire.");
    }

    @Override
    public java.util.List<entities.Commentaire> getAll() throws java.sql.SQLDataException {
        // TODO: implement
        return new ArrayList<>();
    }

    @Override
    public Commentaire getById(int id) throws SQLDataException {
        return null;
    }

    /**
     * Retourne tous les commentaires d'une oeuvre donnée.
     */
    public List<Commentaire> getCommentsByOeuvreId(int oeuvreId) throws SQLDataException {
        String[] tableCandidates = new String[] {"commentaire", "commentaires", "comments"};
        String[] oeuvreColumnCandidates = new String[] {"oeuvre_id", "oeuvreId"};
        String[] textColumnCandidates = new String[] {"texte", "contenu", "content", "commentaire"};
        String[] dateColumnCandidates = new String[] {"date_commentaire", "date_creation", "created_at", "createdAt"};
        String[] userColumnCandidates = new String[] {"user_id", "userId", "auteur_id"};

        for (String table : tableCandidates) {
            for (String oeuvreColumn : oeuvreColumnCandidates) {
                for (String textColumn : textColumnCandidates) {
                    for (String dateColumn : dateColumnCandidates) {
                        for (String userColumn : userColumnCandidates) {
                            String sql = "SELECT id, " + oeuvreColumn + " AS oeuvre_id, " + textColumn + " AS comment_text, "
                                    + dateColumn + " AS comment_date, " + userColumn + " AS comment_user_id "
                                    + "FROM " + table + " WHERE " + oeuvreColumn + " = ? "
                                    + "ORDER BY " + dateColumn + " DESC, id DESC";

                            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                                preparedStatement.setInt(1, oeuvreId);
                                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                                    List<Commentaire> comments = new ArrayList<>();
                                    while (resultSet.next()) {
                                        Commentaire comment = new Commentaire();
                                        comment.setId(resultSet.getInt("id"));
                                        comment.setOeuvreId(resultSet.getInt("oeuvre_id"));
                                        comment.setTexte(trimOrEmpty(resultSet.getString("comment_text")));

                                        Date sqlDate = resultSet.getDate("comment_date");
                                        if (sqlDate != null) {
                                            comment.setDateCommentaire(sqlDate.toLocalDate());
                                        }

                                        int userId = resultSet.getInt("comment_user_id");
                                        if (!resultSet.wasNull()) {
                                            comment.setUserId(userId);
                                        }
                                        comments.add(comment);
                                    }
                                    return comments;
                                }
                            } catch (SQLException ignored) {
                                // Essayer prochaine combinaison table/colonnes.
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>();
    }

    /**
     * Supprime tous les commentaires associés à une oeuvre.
     * Utilisé lors de la suppression d'une oeuvre pour cascade delete.
     */
    public void deleteByOeuvreId(int oeuvreId) throws SQLDataException {
        String[] tableCandidates = new String[] {"commentaire", "commentaires", "comments"};
        String[] oeuvreColumnCandidates = new String[] {"oeuvre_id", "oeuvreId"};

        for (String table : tableCandidates) {
            for (String oeuvreColumn : oeuvreColumnCandidates) {
                String sql = "DELETE FROM " + table + " WHERE " + oeuvreColumn + " = ?";
                try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                    preparedStatement.setInt(1, oeuvreId);
                    preparedStatement.executeUpdate();
                    return;
                } catch (SQLException ignored) {
                    // Essayer la prochaine combinaison table/colonne.
                }
            }
        }
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
