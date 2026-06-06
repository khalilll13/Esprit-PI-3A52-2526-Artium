package services;

import entities.User;
import entities.Oeuvre;
import entities.Commentaire;
import utils.ImageUrlUtils;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class OeuvreService implements services.Iservice<Oeuvre> {

    private final Connection connection;

    public OeuvreService() {
        this.connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void add(Oeuvre t) throws SQLDataException {
        String titre = t.getTitre() == null ? "" : t.getTitre().trim();
        String description = t.getDescription() == null ? "" : t.getDescription().trim();
        Integer collectionId = t.getCollectionId();

        if (titre.isEmpty()) {
            throw new SQLDataException("Le titre de l'oeuvre est obligatoire.");
        }
        if (description.isEmpty()) {
            throw new SQLDataException("La description est obligatoire.");
        }
        if (collectionId == null) {
            throw new SQLDataException("La collection est obligatoire.");
        }
        String imageUrl = ImageUrlUtils.persistToWebImageDirectoryAndNormalize(t.getImage());
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new SQLDataException("L'image est obligatoire.");
        }

        String type = t.getType() == null || t.getType().trim().isEmpty() ? "Oeuvre" : t.getType().trim();
        LocalDate dateCreation = t.getDateCreation() == null ? LocalDate.now() : t.getDateCreation();
        boolean isPublic = t.isPublic();

        String sql = "INSERT INTO oeuvre (titre, description, date_creation, image, type, embedding, image_embedding, collection_id, classe, is_public) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, titre);
            preparedStatement.setString(2, description);
            preparedStatement.setDate(3, Date.valueOf(dateCreation));
            preparedStatement.setString(4, imageUrl);
            preparedStatement.setString(5, type);
            preparedStatement.setString(6, t.getEmbedding());
            preparedStatement.setString(7, t.getImageEmbedding());
            preparedStatement.setInt(8, collectionId);
            preparedStatement.setString(9, "oeuvre");
            preparedStatement.setBoolean(10, isPublic);
            preparedStatement.executeUpdate();

            try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    t.setId(generatedKeys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public void updateImageEmbedding(int oeuvreId, String imageEmbedding) throws SQLDataException {
        if (oeuvreId <= 0) {
            throw new SQLDataException("L'identifiant de l'oeuvre est obligatoire.");
        }

        String sql = "UPDATE oeuvre SET image_embedding = ? WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, imageEmbedding);
            preparedStatement.setInt(2, oeuvreId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    @Override
    public void delete(Oeuvre t) throws SQLDataException {
        if (t == null || t.getId() == null) {
            throw new SQLDataException("L'identifiant de l'oeuvre est obligatoire.");
        }

        try {
            // Supprimer d'abord les relations utilisateur -> oeuvre.
            String deleteFavorisSql = "DELETE FROM oeuvre_user WHERE oeuvre_id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(deleteFavorisSql)) {
                preparedStatement.setInt(1, t.getId());
                preparedStatement.executeUpdate();
            }

            String deleteLikesSql = "DELETE FROM `like` WHERE oeuvre_id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(deleteLikesSql)) {
                preparedStatement.setInt(1, t.getId());
                preparedStatement.executeUpdate();
            }

            // Cascade delete: supprimer les commentaires associés
            CommentaireService commentService = new CommentaireService();
            commentService.deleteByOeuvreId(t.getId());

            // Puis supprimer l'oeuvre elle-même
            String sql = "DELETE FROM oeuvre WHERE id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setInt(1, t.getId());
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    /**
     * Supprime toutes les oeuvres d'une collection (cascade delete).
     * Supprime aussi les commentaires associés à chaque oeuvre.
     * Utilisé lors de la suppression d'une collection.
     */
    public void deleteByCollectionId(int collectionId) throws SQLDataException {
        try {
            // Récupérer toutes les oeuvres de la collection
            String selectSql = "SELECT id FROM oeuvre WHERE collection_id = ?";
            List<Integer> oeuvreIds = new ArrayList<>();

            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                selectStatement.setInt(1, collectionId);
                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    while (resultSet.next()) {
                        oeuvreIds.add(resultSet.getInt("id"));
                    }
                }
            }

            // Pour chaque oeuvre, réutiliser la suppression standard.
            for (Integer oeuvreId : oeuvreIds) {
                Oeuvre oeuvre = new Oeuvre();
                oeuvre.setId(oeuvreId);
                delete(oeuvre);
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    @Override
    public void update(Oeuvre t) throws SQLDataException {
        if (t == null || t.getId() == null) {
            throw new SQLDataException("L'identifiant de l'oeuvre est obligatoire.");
        }

        String titre = t.getTitre() == null ? "" : t.getTitre().trim();
        String description = t.getDescription() == null ? "" : t.getDescription().trim();
        Integer collectionId = t.getCollectionId();

        if (titre.isEmpty()) {
            throw new SQLDataException("Le titre de l'oeuvre est obligatoire.");
        }
        if (description.isEmpty()) {
            throw new SQLDataException("La description est obligatoire.");
        }
        if (collectionId == null) {
            throw new SQLDataException("La collection est obligatoire.");
        }
        String imageUrl = ImageUrlUtils.persistToWebImageDirectoryAndNormalize(t.getImage());
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new SQLDataException("L'image est obligatoire.");
        }

        String type = t.getType() == null || t.getType().trim().isEmpty() ? "Oeuvre" : t.getType().trim();
        LocalDate dateCreation = t.getDateCreation() == null ? LocalDate.now() : t.getDateCreation();
        boolean isPublic = t.isPublic();

        String sql = "UPDATE oeuvre SET titre = ?, description = ?, date_creation = ?, image = ?, type = ?, collection_id = ?, classe = ?, is_public = ? WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, titre);
            preparedStatement.setString(2, description);
            preparedStatement.setDate(3, Date.valueOf(dateCreation));
            preparedStatement.setString(4, imageUrl);
            preparedStatement.setString(5, type);
            preparedStatement.setInt(6, collectionId);
            preparedStatement.setString(7, "oeuvre");
            preparedStatement.setBoolean(8, isPublic);
            preparedStatement.setInt(9, t.getId());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    @Override
    public List<Oeuvre> getAll() throws SQLDataException {
        String sql = "SELECT id, titre, description, date_creation, image, type, embedding, image_embedding, collection_id, is_public "
                + "FROM oeuvre "
                + "WHERE classe NOT IN ('livre', 'musique') "
                + "ORDER BY date_creation DESC, id DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            List<Oeuvre> oeuvres = new ArrayList<>();
            while (resultSet.next()) {
                oeuvres.add(mapOeuvre(resultSet));
            }
            return oeuvres;
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public List<Oeuvre> getAllAmteur() throws SQLDataException {

        String sql = "SELECT id, titre, description, date_creation, image, type, embedding, image_embedding, collection_id, is_public "
                + "FROM oeuvre "
                + "WHERE type IN ('Peinture', 'Sculpture', 'Photographie') "
                + "ORDER BY date_creation DESC, id DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            List<Oeuvre> oeuvres = new ArrayList<>();

            while (resultSet.next()) {
                oeuvres.add(mapOeuvre(resultSet));
            }

            return oeuvres;

        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    @Override
    public Oeuvre getById(int id) throws SQLDataException {
        return getOeuvreById(id);
    }

    /**
     * Retourne la liste des oeuvres d'un artiste (simple, sans engagement data).
     */
    public List<Oeuvre> getOeuvresByArtisteId(int artisteId) throws SQLDataException {
        String sql = "SELECT o.id, o.titre, o.description, o.date_creation, o.image, o.type, o.embedding, o.image_embedding, o.collection_id, o.is_public "
                + "FROM oeuvre o "
                + "INNER JOIN collections c ON c.id = o.collection_id "
                + "WHERE c.artiste_id = ? "
                + "ORDER BY o.date_creation DESC, o.id DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, artisteId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                List<Oeuvre> oeuvres = new ArrayList<>();
                while (resultSet.next()) {
                    oeuvres.add(mapOeuvre(resultSet));
                }
                return oeuvres;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public List<Oeuvre> getOeuvresByCollectionId(int collectionId) throws SQLDataException {
        String sql = "SELECT id, titre, description, date_creation, image, type, embedding, image_embedding, collection_id, is_public "
                + "FROM oeuvre "
                + "WHERE collection_id = ? "
                + "ORDER BY id DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, collectionId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                List<Oeuvre> oeuvres = new ArrayList<>();
                while (resultSet.next()) {
                    oeuvres.add(mapOeuvre(resultSet));
                }
                return oeuvres;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public Oeuvre getOeuvreById(int oeuvreId) throws SQLDataException {
        String sql = "SELECT id, titre, description, date_creation, image, type, embedding, image_embedding, collection_id, is_public "
                + "FROM oeuvre WHERE id = ? LIMIT 1";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, oeuvreId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return mapOeuvre(resultSet);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }


    /**
     * Retourne un user par son ID (pour affichage auteur commentaire, etc.).
     */
    public User getUserById(int userId) {
        String sql = "SELECT id, nom, prenom, photo_profil AS photoProfil, specialite FROM `user` WHERE id = ? LIMIT 1";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User();
                    user.setNom(trimOrEmpty(resultSet.getString("nom")));
                    user.setPrenom(trimOrEmpty(resultSet.getString("prenom")));
                    user.setPhotoProfil(ImageUrlUtils.normalizeForDatabase(resultSet.getString("photoProfil")));
                    user.setSpecialite(trimOrEmpty(resultSet.getString("specialite")));
                    return user;
                }
            }
        } catch (SQLException ignored) {
            // Keep null fallback for caller-side graceful handling.
        }

        return null;
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Oeuvre mapOeuvre(ResultSet resultSet) throws SQLException {
        Oeuvre oeuvre = new Oeuvre();
        oeuvre.setId(resultSet.getInt("id"));
        oeuvre.setTitre(resultSet.getString("titre"));
        oeuvre.setDescription(resultSet.getString("description"));

        Date sqlDate = resultSet.getDate("date_creation");
        if (sqlDate != null) {
            oeuvre.setDateCreation(sqlDate.toLocalDate());
        }

        oeuvre.setImage(ImageUrlUtils.normalizeForDatabase(resultSet.getString("image")));
        oeuvre.setType(resultSet.getString("type"));
        oeuvre.setEmbedding(resultSet.getString("embedding"));
        oeuvre.setImageEmbedding(resultSet.getString("image_embedding"));

        int collectionId = resultSet.getInt("collection_id");
        if (!resultSet.wasNull()) {
            oeuvre.setCollectionId(collectionId);
        }

        // Set is_public (default to true if column doesn't exist or is NULL)
        try {
            oeuvre.setPublic(resultSet.getBoolean("is_public"));
        } catch (SQLException e) {
            // If column doesn't exist, default to true
            oeuvre.setPublic(true);
        }

        return oeuvre;
    }

    /**
     * Obtient les stats de chaque collection de l'artiste
     * Retourne: collectionId, collectionTitre, oeuvresCount, likesCount, favorisCount, commentairesCount
     */
    public List<Map<String, Object>> getCollectionsStatsForArtiste(int artisteId) throws SQLDataException {
        String sql = "SELECT " +
                "c.id as collection_id, " +
                "c.titre as collection_titre, " +
                "COUNT(DISTINCT o.id) as oeuvres_count, " +
                "COUNT(DISTINCT CASE WHEN l.liked = true THEN l.user_id END) as likes_count, " +
                "COUNT(DISTINCT ou.user_id) as favoris_count, " +
                "COUNT(DISTINCT cm.id) as commentaires_count " +
                "FROM collections c " +
                "LEFT JOIN oeuvre o ON o.collection_id = c.id " +
                "LEFT JOIN `like` l ON l.oeuvre_id = o.id AND l.liked = true " +
                "LEFT JOIN oeuvre_user ou ON ou.oeuvre_id = o.id " +
                "LEFT JOIN commentaire cm ON cm.oeuvre_id = o.id " +
                "WHERE c.artiste_id = ? " +
                "GROUP BY c.id, c.titre " +
                "ORDER BY oeuvres_count DESC";

        List<Map<String, Object>> stats = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, artisteId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("collectionId", rs.getInt("collection_id"));
                    row.put("collectionTitre", rs.getString("collection_titre"));
                    row.put("oeuvresCount", rs.getInt("oeuvres_count"));
                    row.put("likesCount", rs.getInt("likes_count"));
                    row.put("favorisCount", rs.getInt("favoris_count"));
                    row.put("commentairesCount", rs.getInt("commentaires_count"));
                    stats.add(row);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
        return stats;
    }

    public int getTotalOeuvresForArtiste(int artisteId) throws SQLDataException {
        String sql = "SELECT COUNT(*) as count FROM oeuvre o " +
                "INNER JOIN collections c ON c.id = o.collection_id " +
                "WHERE c.artiste_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, artisteId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public int getTotalLikesForArtiste(int artisteId) throws SQLDataException {
        String sql = "SELECT COUNT(*) as count FROM `like` l " +
                "INNER JOIN oeuvre o ON o.id = l.oeuvre_id " +
                "INNER JOIN collections c ON c.id = o.collection_id " +
                "WHERE c.artiste_id = ? AND l.liked = true";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, artisteId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public int getTotalFavorisForArtiste(int artisteId) throws SQLDataException {
        String sql = "SELECT COUNT(*) as count FROM oeuvre_user ou " +
                "INNER JOIN oeuvre o ON o.id = ou.oeuvre_id " +
                "INNER JOIN collections c ON c.id = o.collection_id " +
                "WHERE c.artiste_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, artisteId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public int getTotalCommentsForArtiste(int artisteId) throws SQLDataException {
        String sql = "SELECT COUNT(*) as count FROM commentaire cm " +
                "INNER JOIN oeuvre o ON o.id = cm.oeuvre_id " +
                "INNER JOIN collections c ON c.id = o.collection_id " +
                "WHERE c.artiste_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, artisteId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public int getTotalCollectionsForArtiste(int artisteId) throws SQLDataException {
        String sql = "SELECT COUNT(*) as count FROM collections WHERE artiste_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, artisteId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
    }

    public Map<Integer, Integer> getDailyCommentsForArtisteByMonth(int artisteId, YearMonth month) throws SQLDataException {
        YearMonth safeMonth = month == null ? YearMonth.now() : month;
        LocalDate startDate = safeMonth.atDay(1);
        LocalDate endDateExclusive = safeMonth.plusMonths(1).atDay(1);

        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int day = 1; day <= safeMonth.lengthOfMonth(); day++) {
            result.put(day, 0);
        }

        String sql = "SELECT DAY(cm.date_commentaire) AS day_of_month, COUNT(*) AS total_comments " +
                "FROM commentaire cm " +
                "INNER JOIN oeuvre o ON o.id = cm.oeuvre_id " +
                "INNER JOIN collections c ON c.id = o.collection_id " +
                "WHERE c.artiste_id = ? " +
                "AND cm.date_commentaire >= ? " +
                "AND cm.date_commentaire < ? " +
                "GROUP BY DAY(cm.date_commentaire) " +
                "ORDER BY day_of_month ASC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, artisteId);
            stmt.setDate(2, Date.valueOf(startDate));
            stmt.setDate(3, Date.valueOf(endDateExclusive));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int day = rs.getInt("day_of_month");
                    int count = rs.getInt("total_comments");
                    if (result.containsKey(day)) {
                        result.put(day, count);
                    }
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
        return result;
    }

    /**
     * Obtient les 3 utilisateurs les plus actifs (likes, commentaires, favoris) pour un artiste.
     */
    public List<Map<String, Object>> getTop3AmateursForArtiste(int artisteId) throws SQLDataException {
        String sql = "SELECT " +
                "u.id, u.nom, u.prenom, u.photo_profil, " +
                "stats.likes_count, stats.comments_count, stats.favoris_count, " +
                "(stats.likes_count + stats.comments_count + stats.favoris_count) as total_score " +
                "FROM `user` u " +
                "JOIN ( " +
                "    SELECT " +
                "        user_id, " +
                "        SUM(likes) as likes_count, " +
                "        SUM(comments) as comments_count, " +
                "        SUM(favoris) as favoris_count " +
                "    FROM ( " +
                "        SELECT user_id, 1 as likes, 0 as comments, 0 as favoris FROM `like` l INNER JOIN oeuvre o ON o.id = l.oeuvre_id INNER JOIN collections c ON c.id = o.collection_id WHERE c.artiste_id = ? AND l.liked = true " +
                "        UNION ALL " +
                "        SELECT user_id, 0 as likes, 1 as comments, 0 as favoris FROM commentaire cm INNER JOIN oeuvre o ON o.id = cm.oeuvre_id INNER JOIN collections c ON c.id = o.collection_id WHERE c.artiste_id = ? " +
                "        UNION ALL " +
                "        SELECT user_id, 0 as likes, 0 as comments, 1 as favoris FROM oeuvre_user ou INNER JOIN oeuvre o ON o.id = ou.oeuvre_id INNER JOIN collections c ON c.id = o.collection_id WHERE c.artiste_id = ? " +
                "    ) as combined " +
                "    GROUP BY user_id " +
                ") as stats ON u.id = stats.user_id " +
                "WHERE u.id != ? " +
                "ORDER BY total_score DESC " +
                "LIMIT 3";

        List<Map<String, Object>> topAmateurs = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, artisteId);
            stmt.setInt(2, artisteId);
            stmt.setInt(3, artisteId);
            stmt.setInt(4, artisteId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> amateur = new HashMap<>();
                    amateur.put("id", rs.getInt("id"));
                    amateur.put("nom", rs.getString("nom"));
                    amateur.put("prenom", rs.getString("prenom"));
                    amateur.put("photo_profil", ImageUrlUtils.normalizeForDatabase(rs.getString("photo_profil")));
                    amateur.put("likes_count", rs.getInt("likes_count"));
                    amateur.put("comments_count", rs.getInt("comments_count"));
                    amateur.put("favoris_count", rs.getInt("favoris_count"));
                    amateur.put("total_score", rs.getInt("total_score"));
                    topAmateurs.add(amateur);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }
        return topAmateurs;
    }

    /**
     * Calcule le score de tendance d'une oeuvre
     * Score = likes + (favoris * 3) + score_commentaires_pondérés
     * Utilisé pour déterminer si une oeuvre est "trending" (score > seuil)
     */
    public double computeScore(int oeuvreId) throws SQLDataException {
        try {
            LikeService likeService = new LikeService();
            CommentaireService commentService = new CommentaireService();

            // Compter les likes
            int likes = likeService.countLikesByOeuvre(oeuvreId);

            // Compter les favoris
            int favoris = likeService.countFavorisByOeuvre(oeuvreId);

            // Récupérer les commentaires avec calcul de score pondéré par récence
            List<Commentaire> commentaires = commentService.getCommentsByOeuvreId(oeuvreId);

            double scoreCommentaires = 0;
            LocalDate maintenant = LocalDate.now();

            for (Commentaire c : commentaires) {
                LocalDate dateCommentaire = c.getDateCommentaire();
                if (dateCommentaire != null) {
                    long joursEcoules = java.time.temporal.ChronoUnit.DAYS.between(dateCommentaire, maintenant);

                    if (joursEcoules <= 1) {
                        scoreCommentaires += 2;
                    } else if (joursEcoules <= 3) {
                        scoreCommentaires += 1;
                    } else {
                        scoreCommentaires += 0.5;
                    }
                }
            }

            double scoreTotal = likes + (favoris * 2) + scoreCommentaires;

            System.out.println("Score oeuvre #" + oeuvreId + ": " + scoreTotal +
                             " (likes=" + likes + ", favoris=" + favoris +
                             ", commentScore=" + scoreCommentaires + ")");

            return scoreTotal;

        } catch (SQLException e) {
            throw new SQLDataException("Erreur calcul score: " + e.getMessage());
        }
    }

    /**
     * Vérifie si une oeuvre est trending et envoie SMS si seuil dépassé
     * SEUIL_TRENDING = 10.0
     * Utilise le numéro fixe de l'API SMS
     */
    public void checkTrendingAndNotify(int oeuvreId) throws SQLDataException {
        final double SEUIL_TRENDING = 15.0;
        final String SMS_PHONE = "+21624340520"; // Numéro fixe de l'API

        try {
            double score = computeScore(oeuvreId);

            if (score >= SEUIL_TRENDING) {
                // Récupérer l'oeuvre et sa collection
                Oeuvre oeuvre = getOeuvreById(oeuvreId);
                if (oeuvre != null && oeuvre.getCollectionId() != null) {
                    // Récupérer la collection pour trouver l'artiste
                    String sqlCollection = "SELECT artiste_id FROM collections WHERE id = ?";
                    try (PreparedStatement stmt = connection.prepareStatement(sqlCollection)) {
                        stmt.setInt(1, oeuvre.getCollectionId());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                int artisteId = rs.getInt("artiste_id");

                                // Récupérer l'artiste pour ses infos
                                User artiste = getUserArtiste(artisteId);

                                if (artiste != null) {
                                    // Envoyer SMS au numéro fixe
                                    String message = String.format(
                                        "🎉 Bravo %s! Votre oeuvre '%s' atteint un niveau élevé d’engagement !",
                                        artiste.getPrenom() != null ? artiste.getPrenom() : artiste.getNom(),
                                        oeuvre.getTitre(),
                                        score
                                    );

                                    SmsService smsService = new SmsService();
                                    smsService.sendSms(SMS_PHONE, message);

                                    System.out.println("SMS envoyé pour oeuvre: " + oeuvre.getTitre() + " (artiste: " + artiste.getNom() + ", score: " + score + ")");
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Erreur lors de la vérification trending: " + e.getMessage());
        }
    }

    /**
     * Récupère l'artiste (User avec numTel)
     */
    private User getUserArtiste(int userId) {
        String sql = "SELECT id, nom, prenom, num_tel, email FROM `user` WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setNom(rs.getString("nom"));
                    user.setPrenom(rs.getString("prenom"));
                    user.setNumTel(rs.getString("num_tel"));
                    user.setEmail(rs.getString("email"));
                    return user;
                }
            }
        } catch (SQLException ignored) {
            // Fallback gracieux
        }
        return null;
    }
}
