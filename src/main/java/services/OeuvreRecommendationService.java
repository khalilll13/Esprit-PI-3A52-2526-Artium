package services;

import entities.Oeuvre;
import entities.User;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OeuvreRecommendationService {

    private final Connection connection;
    private final OeuvreService oeuvreService;

    public OeuvreRecommendationService() {
        this.connection = MyDatabase.getInstance().getConnection();
        this.oeuvreService = new OeuvreService();
    }

    public List<Oeuvre> getRecommendedOeuvresByImage(User user, int topN) throws SQLDataException {
        if (user == null || user.getId() == null || topN <= 0) {
            return new ArrayList<>();
        }

        List<Oeuvre> allOeuvres = oeuvreService.getAll();
        if (allOeuvres == null || allOeuvres.isEmpty()) {
            return new ArrayList<>();
        }

        // Exclude private artworks (is_public = false)
        allOeuvres.removeIf(o -> !o.isPublic());

        Set<Integer> interactedIds = loadInteractedOeuvreIds(user.getId());
        if (interactedIds.isEmpty()) {
            return recommendByCentreInteret(user.getCentreInteret(), allOeuvres);
        }

        List<List<Double>> interactedEmbeddings = new ArrayList<>();
        for (Oeuvre oeuvre : allOeuvres) {
            if (oeuvre == null || oeuvre.getId() == null || !interactedIds.contains(oeuvre.getId())) {
                continue;
            }
            List<Double> embedding = parseEmbedding(oeuvre.getImageEmbedding());
            if (!embedding.isEmpty()) {
                interactedEmbeddings.add(embedding);
            }
        }

        List<Double> userProfile = averageEmbeddings(interactedEmbeddings);
        if (userProfile == null || userProfile.isEmpty()) {
            return new ArrayList<>();
        }

        List<ScoredOeuvre> scored = new ArrayList<>();
        for (Oeuvre oeuvre : allOeuvres) {
            if (oeuvre == null || oeuvre.getId() == null || interactedIds.contains(oeuvre.getId())) {
                continue;
            }

            List<Double> candidateEmbedding = parseEmbedding(oeuvre.getImageEmbedding());
            if (candidateEmbedding.isEmpty() || candidateEmbedding.size() != userProfile.size()) {
                continue;
            }

            double score = cosineSimilarity(userProfile, candidateEmbedding);
            if (score > 0.0d) {
                scored.add(new ScoredOeuvre(oeuvre, score));
            }
        }

        if (scored.isEmpty()) {
            return new ArrayList<>();
        }

        scored.sort((left, right) -> Double.compare(right.score, left.score));
        List<Oeuvre> recommendations = new ArrayList<>();
        for (int i = 0; i < scored.size() && i < topN; i++) {
            recommendations.add(scored.get(i).oeuvre);
        }
        return recommendations;
    }

    private List<Oeuvre> recommendByCentreInteret(String centreInteret, List<Oeuvre> allOeuvres) {
        if (centreInteret == null || centreInteret.trim().isEmpty() || allOeuvres == null || allOeuvres.isEmpty()) {
            return new ArrayList<>();
        }

        List<Oeuvre> recommendations = new ArrayList<>();
        for (Oeuvre oeuvre : allOeuvres) {
            if (oeuvre == null || oeuvre.getId() == null) {
                continue;
            }

            if (matchesCentreInteret(centreInteret, oeuvre.getType())) {
                recommendations.add(oeuvre);
            }
        }

        return recommendations;
    }

    private boolean matchesCentreInteret(String centreInteret, String type) {
        String interest = normalizeText(centreInteret);
        String oeuvreType = normalizeText(type);
        if (interest.isEmpty() || oeuvreType.isEmpty()) {
            return false;
        }

        return oeuvreType.equals(interest);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private Set<Integer> loadInteractedOeuvreIds(int userId) throws SQLDataException {
        Set<Integer> oeuvreIds = new HashSet<>();

        String likesSql = "SELECT DISTINCT oeuvre_id FROM `like` WHERE user_id = ? AND liked = 1";
        String favorisSql = "SELECT DISTINCT oeuvre_id FROM oeuvre_user WHERE user_id = ?";

        try (PreparedStatement likesStatement = connection.prepareStatement(likesSql);
             PreparedStatement favorisStatement = connection.prepareStatement(favorisSql)) {

            likesStatement.setInt(1, userId);
            try (ResultSet resultSet = likesStatement.executeQuery()) {
                while (resultSet.next()) {
                    oeuvreIds.add(resultSet.getInt("oeuvre_id"));
                }
            }

            favorisStatement.setInt(1, userId);
            try (ResultSet resultSet = favorisStatement.executeQuery()) {
                while (resultSet.next()) {
                    oeuvreIds.add(resultSet.getInt("oeuvre_id"));
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException(e.getMessage());
        }

        oeuvreIds.addAll(loadCommentedOeuvreIds(userId));

        return oeuvreIds;
    }

    private Set<Integer> loadCommentedOeuvreIds(int userId) throws SQLDataException {
        Set<Integer> oeuvreIds = new HashSet<>();

        String[] tableCandidates = new String[] {"commentaire", "commentaires", "comments"};
        String[] oeuvreColumnCandidates = new String[] {"oeuvre_id", "oeuvreId"};
        String[] userColumnCandidates = new String[] {"user_id", "userId", "auteur_id"};

        for (String table : tableCandidates) {
            for (String oeuvreColumn : oeuvreColumnCandidates) {
                for (String userColumn : userColumnCandidates) {
                    String sql = "SELECT DISTINCT " + oeuvreColumn + " AS oeuvre_id FROM " + table + " WHERE " + userColumn + " = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                        preparedStatement.setInt(1, userId);
                        try (ResultSet resultSet = preparedStatement.executeQuery()) {
                            while (resultSet.next()) {
                                oeuvreIds.add(resultSet.getInt("oeuvre_id"));
                            }
                        }
                        if (!oeuvreIds.isEmpty()) {
                            return oeuvreIds;
                        }
                    } catch (SQLException ignored) {
                        // Essayer la prochaine combinaison table/colonnes.
                    }
                }
            }
        }

        return oeuvreIds;
    }

    private List<Double> averageEmbeddings(List<List<Double>> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return null;
        }

        List<Double> first = embeddings.get(0);
        if (first == null || first.isEmpty()) {
            return null;
        }

        int dimension = first.size();
        double[] sum = new double[dimension];
        int validCount = 0;

        for (List<Double> embedding : embeddings) {
            if (embedding == null || embedding.size() != dimension) {
                continue;
            }

            for (int i = 0; i < dimension; i++) {
                Double value = embedding.get(i);
                if (value == null || value.isNaN() || value.isInfinite()) {
                    continue;
                }
                sum[i] += value;
            }
            validCount++;
        }

        if (validCount == 0) {
            return null;
        }

        List<Double> average = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            average.add(sum[i] / validCount);
        }
        return average;
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0.0d;
        }

        double dot = 0.0d;
        double normA = 0.0d;
        double normB = 0.0d;

        for (int i = 0; i < a.size(); i++) {
            double av = safeValue(a.get(i));
            double bv = safeValue(b.get(i));
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }

        if (normA == 0.0d || normB == 0.0d) {
            return 0.0d;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<Double> parseEmbedding(String raw) {
        if (raw == null) {
            return Collections.emptyList();
        }

        String cleaned = raw.trim();
        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }

        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }

        String[] tokens = cleaned.split(",");
        List<Double> values = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String candidate = token.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                values.add(Double.parseDouble(candidate));
            } catch (NumberFormatException ignored) {
                return Collections.emptyList();
            }
        }
        return values;
    }

    private double safeValue(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0d;
        }
        return value;
    }

    private static final class ScoredOeuvre {
        private final Oeuvre oeuvre;
        private final double score;

        private ScoredOeuvre(Oeuvre oeuvre, double score) {
            this.oeuvre = oeuvre;
            this.score = score;
        }
    }
}


