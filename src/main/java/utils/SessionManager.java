package utils;

import entities.User;
import services.UserService;
import java.util.prefs.Preferences;
import java.sql.SQLDataException;

/**
 * Gestionnaire de session persistante pour l'application ARTIUM.
 * Utilise java.util.prefs.Preferences pour sauvegarder l'ID de l'utilisateur.
 * L'utilisateur est rechargé depuis la base de données au démarrage
 * pour garantir que les informations (nom, photo, etc.) sont toujours à jour.
 */
public class SessionManager {
    private static User currentUser;
    private static final Preferences PREFS = Preferences.userRoot().node("com.artium.session");
    private static final String PREF_USER_ID = "logged_in_user_id";

    static {
        // Charger la session persistante si elle existe
        loadSessionFromPreferences();
    }

    /**
     * Récupère l'utilisateur actuellement connecté.
     * @return L'utilisateur connecté, ou null s'il n'y en a pas
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Définit l'utilisateur actuellement connecté et sauvegarde la session.
     * @param user L'utilisateur à connecter
     */
    public static void setCurrentUser(User user) {
        currentUser = user;
        if (user != null && user.getId() != null) {
            PREFS.putInt(PREF_USER_ID, user.getId());
            System.out.println("✓ Session sauvegardée pour l'ID: " + user.getId());
        } else {
            clearSession();
        }
    }

    /**
     * Vérifie si un utilisateur est actuellement connecté.
     * @return true si un utilisateur est connecté, false sinon
     */
    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Efface la session utilisateur (déconnexion) et supprime les préférences.
     */
    public static void clearSession() {
        currentUser = null;
        PREFS.remove(PREF_USER_ID);
        System.out.println("✓ Session supprimée.");
    }

    /**
     * Obtient l'ID de l'utilisateur actuellement connecté.
     * @return L'ID de l'utilisateur, ou null s'il n'y en a pas
     */
    public static Integer getCurrentUserId() {
        return currentUser != null ? currentUser.getId() : null;
    }

    /**
     * Obtient le rôle de l'utilisateur actuellement connecté.
     * @return Le rôle de l'utilisateur, ou une chaîne vide s'il n'y en a pas
     */
    public static String getCurrentUserRole() {
        return currentUser != null ? currentUser.getRole() : "";
    }

    /**
     * Obtient l'email de l'utilisateur actuellement connecté.
     * @return L'email de l'utilisateur, ou null s'il n'y en a pas
     */
    public static String getCurrentUserEmail() {
        return currentUser != null ? currentUser.getEmail() : null;
    }

    /**
     * Charge la session utilisateur depuis les préférences.
     */
    private static void loadSessionFromPreferences() {
        int userId = PREFS.getInt(PREF_USER_ID, -1);

        if (userId == -1) {
            System.out.println("Aucune session persistante trouvée.");
            currentUser = null;
            return;
        }

        try {
            UserService userService = new UserService();
            // On récupère tous les utilisateurs et on cherche par ID,
            // ou on peut utiliser une méthode getById si elle existe dans UserService.
            // Pour être sûr, on utilise getAll() et on filtre.
            for (User u : userService.getAll()) {
                if (u.getId() != null && u.getId() == userId) {
                    currentUser = u;
                    System.out.println("✓ Session restaurée et rafraîchie depuis la BD pour: " + currentUser.getEmail());
                    return;
                }
            }

            // Si on ne trouve pas l'utilisateur dans la BD (ex: compte supprimé)
            System.out.println("L'utilisateur de la session n'existe plus en base de données.");
            clearSession();

        } catch (SQLDataException e) {
            System.err.println("Erreur lors de la récupération de l'utilisateur depuis la BD: " + e.getMessage());
            currentUser = null;
        }
    }
}

