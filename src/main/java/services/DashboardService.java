package services;

import utils.MyDatabase;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.FRANCE);

    private final Connection connection;

    public DashboardService() {
        this.connection = MyDatabase.getInstance().getConnection();
    }

    public DashboardData loadDashboardData() throws SQLDataException {
        ensureConnection();

        int totalUsers        = loadTotalUsers();
        int totalArtistes     = loadRoleCount("artiste");
        int totalAmateurs     = loadRoleCount("amateur");
        int totalOeuvres      = loadSimpleTableCount("oeuvre", "oeuvres");
        int totalReclamations = loadSimpleTableCount("reclamation", "reclamations");
        int totalEvenements   = loadSimpleTableCount("evenement", "evenements");

        List<MonthlySignupPoint> signupsByMonth     = loadSignupsByMonth();
        Map<String, Integer>     roleDistribution   = loadRoleDistribution();
        List<String>             recentSignups      = loadRecentSignups();
        List<String>             recentReclamations = loadRecentReclamations();
        List<String>             topArtistes        = loadTopArtistes();

        return new DashboardData(
                totalUsers, totalArtistes, totalAmateurs,
                totalOeuvres, totalReclamations, totalEvenements,
                signupsByMonth, roleDistribution,
                recentSignups, recentReclamations, topArtistes
        );
    }

    private int loadTotalUsers() throws SQLDataException {
        String sql = "SELECT COUNT(*) FROM `user`";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new SQLDataException("Lecture total utilisateurs impossible: " + e.getMessage());
        }
    }

    private int loadRoleCount(String role) throws SQLDataException {
        String sql = "SELECT COUNT(*) FROM `user` WHERE LOWER(TRIM(`role`)) = LOWER(TRIM(?))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture role " + role + " impossible: " + e.getMessage());
        }
    }

    private int loadSimpleTableCount(String... tableCandidates) throws SQLDataException {
        String table = resolveTableName(tableCandidates);
        if (table == null) return 0;
        String sql = "SELECT COUNT(*) FROM `" + table + "`";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new SQLDataException("Lecture total table " + table + " impossible: " + e.getMessage());
        }
    }

    private List<MonthlySignupPoint> loadSignupsByMonth() throws SQLDataException {
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth   = currentMonth.minusMonths(5);
        LocalDate startDate    = startMonth.atDay(1);

        Map<YearMonth, Integer> valuesByMonth = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            valuesByMonth.put(startMonth.plusMonths(i), 0);
        }

        String sql = "SELECT YEAR(`date_inscription`) AS y, MONTH(`date_inscription`) AS m, COUNT(*) AS total " +
                "FROM `user` WHERE `date_inscription` IS NOT NULL AND `date_inscription` >= ? " +
                "GROUP BY YEAR(`date_inscription`), MONTH(`date_inscription`)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(startDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    YearMonth month = YearMonth.of(rs.getInt("y"), rs.getInt("m"));
                    if (valuesByMonth.containsKey(month)) {
                        valuesByMonth.put(month, rs.getInt("total"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture courbe inscriptions impossible: " + e.getMessage());
        }

        List<MonthlySignupPoint> points = new ArrayList<>();
        for (Map.Entry<YearMonth, Integer> entry : valuesByMonth.entrySet()) {
            String monthLabel = entry.getKey().format(MONTH_FORMAT);
            if (!monthLabel.isEmpty()) {
                monthLabel = monthLabel.substring(0, 1).toUpperCase(Locale.FRANCE) + monthLabel.substring(1);
            }
            points.add(new MonthlySignupPoint(monthLabel, entry.getValue()));
        }
        return points;
    }

    private Map<String, Integer> loadRoleDistribution() throws SQLDataException {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("Admin",    0);
        distribution.put("Artistes", 0);
        distribution.put("Amateurs", 0);

        String sql = "SELECT LOWER(TRIM(`role`)) AS role_name, COUNT(*) AS total " +
                "FROM `user` GROUP BY LOWER(TRIM(`role`))";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String role  = rs.getString("role_name");
                int    total = rs.getInt("total");
                if ("admin".equals(role))        distribution.put("Admin",    total);
                else if ("artiste".equals(role)) distribution.put("Artistes", total);
                else if ("amateur".equals(role)) distribution.put("Amateurs", total);
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture repartition des roles impossible: " + e.getMessage());
        }
        return distribution;
    }

    // ✅ MÉTHODE CORRIGÉE — plus de fallback sans filtre de date
    private List<String> loadRecentSignups() throws SQLDataException {
        String table      = resolveTableName("user");
        String dateColumn = table == null ? null : resolveColumn(table, "date_inscription", "dateinscription");
        if (table == null || dateColumn == null) {
            return List.of();
        }

        String dateType = resolveColumnType(table, dateColumn);

        // Filtre strict 24h glissantes selon le type de colonne
        String recentFilter = isTimestampLike(dateType)
                ? "`" + dateColumn + "` >= DATE_SUB(NOW(), INTERVAL 24 HOUR)"
                : "`" + dateColumn + "` >= DATE_SUB(CURDATE(), INTERVAL 1 DAY)";

        String sql = "SELECT `nom`, `prenom`, `" + dateColumn + "` AS signup_date FROM `" + table + "` " +
                "WHERE `" + dateColumn + "` IS NOT NULL AND " + recentFilter + " " +
                "ORDER BY `" + dateColumn + "` DESC LIMIT 8";

        List<String> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String fullName = (safeTrim(rs.getString("prenom")) + " " + safeTrim(rs.getString("nom"))).trim();
                rows.add(fullName + " - " + formatDateValue(rs.getObject("signup_date")));
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture inscriptions recentes impossible: " + e.getMessage());
        }

        // ⚠ PAS DE FALLBACK — liste vide = "Aucune inscription récente" affiché dans le controller
        return rows;
    }

    private List<String> loadRecentReclamations() throws SQLDataException {
        String table = resolveTableName("reclamation", "reclamations");
        if (table == null) {
            return List.of();
        }

        String textColumn   = resolveColumn(table, "texte", "description", "objet", "titre", "contenu");
        String dateColumn   = resolveColumn(table, "updated_at", "date_creation", "created_at", "date", "createdat");
        String dateType     = dateColumn == null ? null : resolveColumnType(table, dateColumn);
        String recentFilter = dateColumn == null ? null : (isTimestampLike(dateType)
                ? "`" + dateColumn + "` >= DATE_SUB(NOW(), INTERVAL 1 DAY)"
                : "`" + dateColumn + "` >= CURDATE()");

        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(textColumn != null ? "`" + textColumn + "`" : "NULL");
        sql.append(" AS text_col");
        sql.append(dateColumn != null ? ", `" + dateColumn + "` AS date_col" : ", NULL AS date_col");
        sql.append(" FROM `").append(table).append("`");
        if (recentFilter != null) {
            sql.append(" WHERE `").append(dateColumn).append("` IS NOT NULL AND ").append(recentFilter);
        }
        if (dateColumn != null) {
            sql.append(" ORDER BY `").append(dateColumn).append("` DESC");
        }
        sql.append(" LIMIT 8");

        List<String> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String text = safeTrim(rs.getString("text_col"));
                if (text.isEmpty()) text = "Reclamation";
                if (text.length() > 55) text = text.substring(0, 55) + "...";
                Object rawDate = rs.getObject("date_col");
                String date    = rawDate != null ? formatDateValue(rawDate) : "";
                rows.add(date.isBlank() ? text : text + " - " + date);
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture reclamations recentes impossible: " + e.getMessage());
        }

        // Fallback : si aucune réclamation récente, prendre les 8 dernières
        if (rows.isEmpty() && dateColumn != null) {
            StringBuilder fallbackSql = new StringBuilder("SELECT ");
            fallbackSql.append(textColumn != null ? "`" + textColumn + "`" : "NULL");
            fallbackSql.append(" AS text_col");
            fallbackSql.append(", `").append(dateColumn).append("` AS date_col");
            fallbackSql.append(" FROM `").append(table).append("`");
            fallbackSql.append(" ORDER BY `").append(dateColumn).append("` DESC LIMIT 8");

            try (PreparedStatement ps = connection.prepareStatement(fallbackSql.toString());
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String text = safeTrim(rs.getString("text_col"));
                    if (text.isEmpty()) text = "Reclamation";
                    if (text.length() > 55) text = text.substring(0, 55) + "...";
                    Object rawDate = rs.getObject("date_col");
                    String date    = rawDate != null ? formatDateValue(rawDate) : "";
                    rows.add(date.isBlank() ? text : text + " - " + date);
                }
            } catch (SQLException e) {
                throw new SQLDataException("Lecture reclamations recentes (fallback) impossible: " + e.getMessage());
            }
        }

        return rows;
    }

    private List<String> loadTopArtistes() throws SQLDataException {
        String oeuvreTable    = resolveTableName("oeuvre", "oeuvres");
        String oeuvreFkArtist = oeuvreTable == null ? null : resolveColumn(oeuvreTable,
                "artiste_id", "artist_id", "user_id", "id_user", "userid", "artisteid");
        String userIdColumn   = resolveColumn("user", "id", "id_user");

        if (oeuvreTable != null && oeuvreFkArtist != null && userIdColumn != null) {
            String sql = "SELECT u.`nom`, u.`prenom`, COUNT(o.`" + oeuvreFkArtist + "`) AS total_oeuvres " +
                    "FROM `user` u LEFT JOIN `" + oeuvreTable + "` o ON o.`" + oeuvreFkArtist + "` = u.`" + userIdColumn + "` " +
                    "WHERE LOWER(TRIM(u.`role`)) = 'artiste' " +
                    "GROUP BY u.`" + userIdColumn + "`, u.`nom`, u.`prenom` " +
                    "ORDER BY total_oeuvres DESC, u.`nom` ASC LIMIT 5";

            List<String> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fullName = (safeTrim(rs.getString("prenom")) + " " + safeTrim(rs.getString("nom"))).trim();
                    int    total    = rs.getInt("total_oeuvres");
                    rows.add(fullName + " - " + total + " oeuvres");
                }
            } catch (SQLException e) {
                throw new SQLDataException("Lecture top artistes impossible: " + e.getMessage());
            }
            if (!rows.isEmpty()) return rows;
        }

        return loadTopArtistesFallback();
    }

    private List<String> loadTopArtistesFallback() throws SQLDataException {
        List<String> rows = new ArrayList<>();
        String sql = "SELECT `nom`, `prenom` FROM `user` " +
                "WHERE LOWER(TRIM(`role`)) = 'artiste' ORDER BY `nom` ASC LIMIT 5";

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String fullName = (safeTrim(rs.getString("prenom")) + " " + safeTrim(rs.getString("nom"))).trim();
                rows.add(fullName + " - oeuvres N/A");
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture top artistes (fallback) impossible: " + e.getMessage());
        }

        return rows;
    }

    // ── Helpers metadata ─────────────────────────────────────────────────────

    private String resolveTableName(String... candidates) throws SQLDataException {
        Map<String, String> existingTables = loadTablesByLowerName();
        for (String candidate : candidates) {
            String resolved = existingTables.get(candidate.toLowerCase(Locale.ROOT));
            if (resolved != null) return resolved;
        }
        return null;
    }

    private Map<String, String> loadTablesByLowerName() throws SQLDataException {
        Map<String, String> tables = new HashMap<>();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName != null) tables.put(tableName.toLowerCase(Locale.ROOT), tableName);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture metadata tables impossible: " + e.getMessage());
        }
        return tables;
    }

    private String resolveColumn(String tableName, String... candidates) throws SQLDataException {
        Map<String, String> existingColumns = loadColumnsByLowerName(tableName);
        for (String candidate : candidates) {
            String resolved = existingColumns.get(candidate.toLowerCase(Locale.ROOT));
            if (resolved != null) return resolved;
        }
        return null;
    }

    private Map<String, String> loadColumnsByLowerName(String tableName) throws SQLDataException {
        Map<String, String> columns = new HashMap<>();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, tableName, "%")) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    if (columnName != null) columns.put(columnName.toLowerCase(Locale.ROOT), columnName);
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture metadata colonnes impossible: " + e.getMessage());
        }
        return columns;
    }

    private String resolveColumnType(String tableName, String columnName) throws SQLDataException {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
                if (rs.next()) return safeTrim(rs.getString("TYPE_NAME"));
            }
        } catch (SQLException e) {
            throw new SQLDataException("Lecture type colonne impossible: " + e.getMessage());
        }
        return "";
    }

    private boolean isTimestampLike(String typeName) {
        String normalized = typeName == null ? "" : typeName.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("TIMESTAMP") || normalized.contains("DATETIME") || normalized.contains("TIME");
    }

    private String formatDateValue(Object rawDate) {
        if (rawDate == null) return "";
        if (rawDate instanceof LocalDate localDate)                   return localDate.toString();
        if (rawDate instanceof java.time.LocalDateTime localDateTime) return localDateTime.toLocalDate().toString();
        if (rawDate instanceof java.sql.Date sqlDate)                 return sqlDate.toLocalDate().toString();
        if (rawDate instanceof java.sql.Timestamp timestamp)          return timestamp.toLocalDateTime().toLocalDate().toString();
        return String.valueOf(rawDate);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void ensureConnection() throws SQLDataException {
        if (connection == null) throw new SQLDataException("Connexion base de donnees indisponible.");
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public static final class DashboardData {
        private final int totalUsers, totalArtistes, totalAmateurs;
        private final int totalOeuvres, totalReclamations, totalEvenements;
        private final List<MonthlySignupPoint> signupsByMonth;
        private final Map<String, Integer>     roleDistribution;
        private final List<String> recentSignups, recentReclamations, topArtistes;

        public DashboardData(int totalUsers, int totalArtistes, int totalAmateurs,
                             int totalOeuvres, int totalReclamations, int totalEvenements,
                             List<MonthlySignupPoint> signupsByMonth,
                             Map<String, Integer> roleDistribution,
                             List<String> recentSignups,
                             List<String> recentReclamations,
                             List<String> topArtistes) {
            this.totalUsers = totalUsers; this.totalArtistes = totalArtistes; this.totalAmateurs = totalAmateurs;
            this.totalOeuvres = totalOeuvres; this.totalReclamations = totalReclamations; this.totalEvenements = totalEvenements;
            this.signupsByMonth = signupsByMonth; this.roleDistribution = roleDistribution;
            this.recentSignups = recentSignups; this.recentReclamations = recentReclamations; this.topArtistes = topArtistes;
        }

        public int getTotalUsers()        { return totalUsers; }
        public int getTotalArtistes()     { return totalArtistes; }
        public int getTotalAmateurs()     { return totalAmateurs; }
        public int getTotalOeuvres()      { return totalOeuvres; }
        public int getTotalReclamations() { return totalReclamations; }
        public int getTotalEvenements()   { return totalEvenements; }

        public List<MonthlySignupPoint> getSignupsByMonth()   { return signupsByMonth; }
        public Map<String, Integer>     getRoleDistribution() { return roleDistribution; }
        public List<String> getRecentSignups()      { return recentSignups; }
        public List<String> getRecentReclamations() { return recentReclamations; }
        public List<String> getTopArtistes()        { return topArtistes; }
    }

    public static final class MonthlySignupPoint {
        private final String monthLabel;
        private final int    total;

        public MonthlySignupPoint(String monthLabel, int total) {
            this.monthLabel = monthLabel;
            this.total      = total;
        }

        public String getMonthLabel() { return monthLabel; }
        public int    getTotal()      { return total; }
    }
}