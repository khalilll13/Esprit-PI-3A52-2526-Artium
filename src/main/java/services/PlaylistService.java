package services;

import entities.Musique;
import entities.Playlist;
import utils.ImageUrlUtils;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaylistService implements Iservice<Playlist> {

    private final Connection connection;
    private final MusiqueService musiqueService = new MusiqueService();

    public PlaylistService() {
        this.connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void add(Playlist playlist) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }
        if (playlist == null) {
            throw new SQLDataException("Playlist manquante pour la creation.");
        }

        TableConfig table = resolvePlaylistTable();
        try {
            connection.setAutoCommit(false);
            Integer generatedId;
            String insertSql = table.buildInsertSql();
            try (PreparedStatement statement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                int index = 1;
                if (table.nameColumn != null) {
                    statement.setString(index++, playlist.getNom());
                }
                if (table.descriptionColumn != null) {
                    statement.setString(index++, playlist.getDescription());
                }
                if (table.dateColumn != null) {
                    LocalDate dateCreation = playlist.getDateCreation() != null ? playlist.getDateCreation() : LocalDate.now();
                    statement.setDate(index++, Date.valueOf(dateCreation));
                }
                if (table.imageColumn != null) {
                    statement.setString(index++, normalizePlaylistImageForSave(playlist.getImage()));
                }
                if (table.userColumn != null) {
                    Integer effectiveUserId = resolveEffectiveUserId(playlist, table);
                    if (effectiveUserId != null) {
                        statement.setInt(index++, effectiveUserId);
                    } else {
                        statement.setObject(index++, null);
                    }
                }

                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        generatedId = keys.getInt(1);
                    } else {
                        throw new SQLDataException("Impossible de recuperer l'identifiant de la playlist creee.");
                    }
                }
            }

            playlist.setId(generatedId);
            if (playlist.getMusiques() != null && !playlist.getMusiques().isEmpty()) {
                for (Musique musique : playlist.getMusiques()) {
                    if (musique != null && musique.getId() != null) {
                        insertMusiqueAssociation(generatedId, musique.getId());
                    }
                }
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                throw new SQLDataException("Echec lors du rollback apres erreur de creation playlist: " + rollbackException.getMessage());
            }
            throw new SQLDataException("Impossible de creer la playlist: " + e.getMessage());
        } finally {
            restoreAutoCommit();
        }
    }

    @Override
    public void delete(Playlist playlist) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }
        if (playlist == null || playlist.getId() == null) {
            throw new SQLDataException("Identifiant playlist manquant pour la suppression.");
        }

        TableConfig table = resolvePlaylistTable();
        JoinConfig joinTable = resolveJoinTable();
        String deleteJoinSql = "DELETE FROM " + joinTable.tableName + " WHERE " + joinTable.playlistIdColumn + " = ?";
        String deleteSql = "DELETE FROM " + table.tableName + " WHERE " + table.idColumn + " = ?";

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement joinStatement = connection.prepareStatement(deleteJoinSql);
                 PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                joinStatement.setInt(1, playlist.getId());
                joinStatement.executeUpdate();

                deleteStatement.setInt(1, playlist.getId());
                int deleted = deleteStatement.executeUpdate();
                if (deleted == 0) {
                    throw new SQLDataException("Aucune playlist trouvee avec l'identifiant " + playlist.getId());
                }
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                throw new SQLDataException("Echec lors du rollback apres erreur de suppression playlist: " + rollbackException.getMessage());
            }
            throw new SQLDataException("Impossible de supprimer la playlist: " + e.getMessage());
        } finally {
            restoreAutoCommit();
        }
    }

    @Override
    public void update(Playlist playlist) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }
        if (playlist == null || playlist.getId() == null) {
            throw new SQLDataException("Identifiant playlist manquant pour la mise a jour.");
        }

        TableConfig table = resolvePlaylistTable();
        String updateSql = table.buildUpdateSql();
        if (updateSql == null) {
            throw new SQLDataException("Aucune colonne modifiable n'a ete trouvee pour la playlist.");
        }

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                int index = 1;
                if (table.nameColumn != null) {
                    statement.setString(index++, playlist.getNom());
                }
                if (table.descriptionColumn != null) {
                    statement.setString(index++, playlist.getDescription());
                }
                if (table.dateColumn != null) {
                    LocalDate dateCreation = playlist.getDateCreation() != null ? playlist.getDateCreation() : LocalDate.now();
                    statement.setDate(index++, Date.valueOf(dateCreation));
                }
                if (table.imageColumn != null) {
                    statement.setString(index++, normalizePlaylistImageForSave(playlist.getImage()));
                }
                if (table.userColumn != null) {
                    Integer effectiveUserId = resolveEffectiveUserId(playlist, table);
                    if (effectiveUserId != null) {
                        statement.setInt(index++, effectiveUserId);
                    } else {
                        statement.setObject(index++, null);
                    }
                }
                statement.setInt(index, playlist.getId());
                int updated = statement.executeUpdate();
                if (updated == 0) {
                    throw new SQLDataException("Aucune playlist trouvee avec l'identifiant " + playlist.getId());
                }
            }

            if (playlist.getMusiques() != null) {
                syncPlaylistMusics(playlist.getId(), playlist.getMusiques());
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                throw new SQLDataException("Echec lors du rollback apres erreur de mise a jour playlist: " + rollbackException.getMessage());
            }
            throw new SQLDataException("Impossible de mettre a jour la playlist: " + e.getMessage());
        } finally {
            restoreAutoCommit();
        }
    }

    @Override
    public List<Playlist> getAll() throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }

        TableConfig table = resolvePlaylistTable();
        String query = table.buildSelectSql();
        List<Playlist> playlists = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Playlist playlist = new Playlist();
                playlist.setId(resultSet.getInt(table.idColumn));
                if (table.nameColumn != null) {
                    playlist.setNom(resultSet.getString(table.nameColumn));
                }
                if (table.descriptionColumn != null) {
                    playlist.setDescription(resultSet.getString(table.descriptionColumn));
                }
                if (table.dateColumn != null) {
                    Date dateCreation = resultSet.getDate(table.dateColumn);
                    if (dateCreation != null) {
                        playlist.setDateCreation(dateCreation.toLocalDate());
                    }
                }
                if (table.imageColumn != null) {
                    playlist.setImage(ImageUrlUtils.normalizeForDatabase(resultSet.getString(table.imageColumn)));
                }
                if (table.userColumn != null) {
                    int userId = resultSet.getInt(table.userColumn);
                    if (!resultSet.wasNull()) {
                        playlist.setUserId(userId);
                    }
                }
                playlist.setMusiques(getMusiquesForPlaylist(playlist.getId()));
                playlists.add(playlist);
            }
        } catch (SQLException e) {
            throw new SQLDataException("Impossible de charger les playlists: " + e.getMessage());
        }

        return playlists;
    }

    @Override
    public Playlist getById(int id) throws SQLDataException {
        return null;
    }

    public void addMusiqueToPlaylist(Integer playlistId, Integer musiqueId) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }
        if (playlistId == null || musiqueId == null) {
            throw new SQLDataException("Identifiants playlist ou musique manquants.");
        }

        JoinConfig joinTable = resolveJoinTable();
        String existsSql = "SELECT 1 FROM " + joinTable.tableName + " WHERE " + joinTable.playlistIdColumn + " = ? AND " + joinTable.musiqueIdColumn + " = ?";
        String insertSql = "INSERT INTO " + joinTable.tableName + " (" + joinTable.playlistIdColumn + ", " + joinTable.musiqueIdColumn + ") VALUES (?, ?)";

        try {
            connection.setAutoCommit(false);
            insertMusiqueAssociation(playlistId, musiqueId);
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                throw new SQLDataException("Echec lors du rollback apres erreur d'ajout musique a la playlist: " + rollbackException.getMessage());
            }
            throw new SQLDataException("Impossible d'ajouter la musique a la playlist: " + e.getMessage());
        } finally {
            restoreAutoCommit();
        }
    }

    public void removeMusiqueFromPlaylist(Integer playlistId, Integer musiqueId) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }
        if (playlistId == null || musiqueId == null) {
            throw new SQLDataException("Identifiants playlist ou musique manquants.");
        }

        JoinConfig joinTable = resolveJoinTable();
        String deleteSql = "DELETE FROM " + joinTable.tableName + " WHERE " + joinTable.playlistIdColumn + " = ? AND " + joinTable.musiqueIdColumn + " = ?";

        try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setInt(1, playlistId);
            statement.setInt(2, musiqueId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLDataException("Impossible de retirer la musique de la playlist: " + e.getMessage());
        }
    }

    public List<Musique> getMusiquesForPlaylist(Integer playlistId) throws SQLDataException {
        if (connection == null) {
            throw new SQLDataException("Connexion a la base de donnees indisponible.");
        }
        if (playlistId == null) {
            return List.of();
        }

        JoinConfig joinTable = resolveJoinTable();
        String query = "SELECT " + joinTable.musiqueIdColumn + " FROM " + joinTable.tableName + " WHERE " + joinTable.playlistIdColumn + " = ?";
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, playlistId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Impossible de charger les musiques de la playlist: " + e.getMessage());
        }

        if (ids.isEmpty()) {
            return new ArrayList<>();
        }

        List<Musique> allMusiques = musiqueService.getAll();
        List<Musique> result = new ArrayList<>();
        for (Musique musique : allMusiques) {
            if (musique.getId() != null && ids.contains(musique.getId())) {
                result.add(musique);
            }
        }
        return result;
    }

    private void syncPlaylistMusics(Integer playlistId, List<Musique> musiques) throws SQLException {
        JoinConfig joinTable = resolveJoinTable();
        String deleteSql = "DELETE FROM " + joinTable.tableName + " WHERE " + joinTable.playlistIdColumn + " = ?";

        try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
            deleteStatement.setInt(1, playlistId);
            deleteStatement.executeUpdate();
        }

        if (musiques == null) {
            return;
        }

        for (Musique musique : musiques) {
            if (musique != null && musique.getId() != null) {
                insertMusiqueAssociation(playlistId, musique.getId());
            }
        }
    }

    private void insertMusiqueAssociation(Integer playlistId, Integer musiqueId) throws SQLException {
        JoinConfig joinTable = resolveJoinTable();
        String existsSql = "SELECT 1 FROM " + joinTable.tableName + " WHERE " + joinTable.playlistIdColumn + " = ? AND " + joinTable.musiqueIdColumn + " = ?";
        String insertSql = "INSERT INTO " + joinTable.tableName + " (" + joinTable.playlistIdColumn + ", " + joinTable.musiqueIdColumn + ") VALUES (?, ?)";

        boolean alreadyLinked;
        try (PreparedStatement existsStatement = connection.prepareStatement(existsSql)) {
            existsStatement.setInt(1, playlistId);
            existsStatement.setInt(2, musiqueId);
            try (ResultSet rs = existsStatement.executeQuery()) {
                alreadyLinked = rs.next();
            }
        }

        if (!alreadyLinked) {
            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                insertStatement.setInt(1, playlistId);
                insertStatement.setInt(2, musiqueId);
                insertStatement.executeUpdate();
            }
        }
    }

    private TableConfig resolvePlaylistTable() throws SQLDataException {
        String tableName = findExistingTable("playlist", "playlists");
        if (tableName == null) {
            throw new SQLDataException("Aucune table de playlist trouvee.");
        }

        return new TableConfig(
                tableName,
                findExistingColumn(tableName, "id", "playlist_id"),
                findExistingColumn(tableName, "nom", "name", "titre"),
                findExistingColumn(tableName, "description", "desc"),
                findExistingColumn(tableName, "date_creation", "created_at", "date_created"),
                findExistingColumn(tableName, "image", "cover", "cover_image"),
                findExistingColumn(tableName, "user_id", "id_user", "userId"),
                isColumnRequired(tableName, findExistingColumn(tableName, "user_id", "id_user", "userId"))
        );
    }

    private Integer resolveEffectiveUserId(Playlist playlist, TableConfig table) throws SQLDataException {
        if (playlist.getUserId() != null) {
            return playlist.getUserId();
        }

        Integer fallbackUserId = resolveFallbackUserId();
        if (table.userRequired && fallbackUserId == null) {
            throw new SQLDataException("Aucun utilisateur valide trouve pour creer la playlist (user_id requis).");
        }
        return fallbackUserId;
    }

    private Integer resolveFallbackUserId() {
        String userTable = findExistingTableQuietly("users", "user", "utilisateurs", "utilisateur");
        if (userTable == null) {
            return null;
        }

        String userIdColumn = findExistingColumnQuietly(userTable, "id", "user_id");
        if (userIdColumn == null) {
            return null;
        }

        String sql = "SELECT " + userIdColumn + " FROM " + userTable + " ORDER BY " + userIdColumn + " ASC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                int id = resultSet.getInt(1);
                if (!resultSet.wasNull()) {
                    return id;
                }
            }
        } catch (SQLException ignored) {
            return null;
        }

        return null;
    }

    private JoinConfig resolveJoinTable() throws SQLDataException {
        String tableName = findExistingTable("playlist_musique", "playlist_music", "playlists_musiques", "musique_playlist");
        if (tableName == null) {
            throw new SQLDataException("Aucune table d'association playlist/musique trouvee.");
        }

        String playlistIdColumn = findExistingColumn(tableName, "playlist_id", "id_playlist");
        String musiqueIdColumn = findExistingColumn(tableName, "musique_id", "music_id", "id_musique");
        return new JoinConfig(tableName, playlistIdColumn, musiqueIdColumn);
    }

    private String findExistingTable(String... candidates) throws SQLDataException {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    for (String candidate : candidates) {
                        if (tableName != null && tableName.equalsIgnoreCase(candidate)) {
                            return tableName;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Impossible d'identifier les tables de playlist: " + e.getMessage());
        }
        return null;
    }

    private String findExistingColumn(String tableName, String... candidates) throws SQLDataException {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, tableName, "%")) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    for (String candidate : candidates) {
                        if (columnName != null && columnName.equalsIgnoreCase(candidate)) {
                            return columnName;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Impossible d'identifier les colonnes de playlist: " + e.getMessage());
        }
        return null;
    }

    private String findExistingTableQuietly(String... candidates) {
        try {
            return findExistingTable(candidates);
        } catch (SQLDataException ignored) {
            return null;
        }
    }

    private String findExistingColumnQuietly(String tableName, String... candidates) {
        try {
            return findExistingColumn(tableName, candidates);
        } catch (SQLDataException ignored) {
            return null;
        }
    }

    private boolean isColumnRequired(String tableName, String columnName) throws SQLDataException {
        if (columnName == null) {
            return false;
        }

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
                if (columns.next()) {
                    int nullable = columns.getInt("NULLABLE");
                    return nullable == DatabaseMetaData.columnNoNulls;
                }
            }
        } catch (SQLException e) {
            throw new SQLDataException("Impossible de verifier la contrainte de colonne playlist: " + e.getMessage());
        }
        return false;
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private String normalizePlaylistImageForSave(String imageValue) throws SQLDataException {
        if (imageValue == null || imageValue.isBlank()) {
            return null;
        }
        return ImageUrlUtils.persistToWebImageDirectoryAndNormalize(imageValue);
    }

    private static final class TableConfig {
        private final String tableName;
        private final String idColumn;
        private final String nameColumn;
        private final String descriptionColumn;
        private final String dateColumn;
        private final String imageColumn;
        private final String userColumn;
        private final boolean userRequired;

        private TableConfig(String tableName, String idColumn, String nameColumn, String descriptionColumn, String dateColumn, String imageColumn, String userColumn, boolean userRequired) {
            this.tableName = tableName;
            this.idColumn = idColumn;
            this.nameColumn = nameColumn;
            this.descriptionColumn = descriptionColumn;
            this.dateColumn = dateColumn;
            this.imageColumn = imageColumn;
            this.userColumn = userColumn;
            this.userRequired = userRequired;
        }

        private String buildInsertSql() {
            List<String> columns = new ArrayList<>();
            List<String> values = new ArrayList<>();
            if (nameColumn != null) {
                columns.add(nameColumn);
                values.add("?");
            }
            if (descriptionColumn != null) {
                columns.add(descriptionColumn);
                values.add("?");
            }
            if (dateColumn != null) {
                columns.add(dateColumn);
                values.add("?");
            }
            if (imageColumn != null) {
                columns.add(imageColumn);
                values.add("?");
            }
            if (userColumn != null) {
                columns.add(userColumn);
                values.add("?");
            }
            return "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") VALUES (" + String.join(", ", values) + ")";
        }

        private String buildUpdateSql() {
            List<String> assignments = new ArrayList<>();
            if (nameColumn != null) {
                assignments.add(nameColumn + " = ?");
            }
            if (descriptionColumn != null) {
                assignments.add(descriptionColumn + " = ?");
            }
            if (dateColumn != null) {
                assignments.add(dateColumn + " = ?");
            }
            if (imageColumn != null) {
                assignments.add(imageColumn + " = ?");
            }
            if (userColumn != null) {
                assignments.add(userColumn + " = ?");
            }
            if (assignments.isEmpty()) {
                return null;
            }
            return "UPDATE " + tableName + " SET " + String.join(", ", assignments) + " WHERE " + idColumn + " = ?";
        }

        private String buildSelectSql() {
            List<String> selectColumns = new ArrayList<>();
            selectColumns.add(idColumn);
            if (nameColumn != null) {
                selectColumns.add(nameColumn);
            }
            if (descriptionColumn != null) {
                selectColumns.add(descriptionColumn);
            }
            if (dateColumn != null) {
                selectColumns.add(dateColumn);
            }
            if (imageColumn != null) {
                selectColumns.add(imageColumn);
            }
            if (userColumn != null) {
                selectColumns.add(userColumn);
            }
            return "SELECT " + String.join(", ", selectColumns) + " FROM " + tableName + " ORDER BY " + idColumn + " DESC";
        }
    }

    private record JoinConfig(String tableName, String playlistIdColumn, String musiqueIdColumn) { }
}



