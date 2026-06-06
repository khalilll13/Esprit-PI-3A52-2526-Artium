package utils;

/**
 * Utility class to translate SQL error messages into user-friendly French messages.
 */
public final class SqlErrorMessageTranslator {

    private SqlErrorMessageTranslator() {
        // Utility class
    }

    /**
     * Translates a SQL error message into a user-friendly French message.
     *
     * @param errorMessage the original SQL error message
     * @return a user-friendly French message
     */
    public static String translate(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Une erreur s'est produite. Veuillez vérifier les informations et réessayer.";
        }

        String lowerMessage = errorMessage.toLowerCase();

        // MySQL Duplicate Entry errors (UNIQUE constraints)
        if (containsAny(lowerMessage, "duplicate entry", "clé unique", "unique constraint")) {
            if (containsAny(lowerMessage, "titre")) {
                return "Un événement avec ce titre existe déjà. Veuillez utiliser un titre différent.";
            }
            return "Ces informations existent déjà. Veuillez vérifier les données.";
        }

        // Foreign Key constraint errors
        if (containsAny(lowerMessage, "foreign key constraint", "clé étrangère", "cannot add or update a child row", "galerie_id", "galerie")) {
            return "La galerie sélectionnée n'existe pas ou n'est pas accessible.";
        }

        // Out of range or invalid numeric values
        if (containsAny(lowerMessage, "out of range", "hors limites", "capacite", "capacité")) {
            return "La capacité maximale doit être un nombre entier positif.";
        }

        if (containsAny(lowerMessage, "out of range", "hors limites", "prix", "price", "prix_ticket")) {
            return "Le prix du ticket doit être un nombre valide et positif.";
        }

        // Incorrect data type or format
        if (containsAny(lowerMessage, "incorrect integer", "incorrect decimal", "incorrect datetime")) {
            if (containsAny(lowerMessage, "capacite", "capacité")) {
                return "La capacité maximale doit être un nombre entier.";
            }
            if (containsAny(lowerMessage, "prix", "price")) {
                return "Le prix du ticket doit être un nombre valide.";
            }
            if (containsAny(lowerMessage, "date", "datetime")) {
                return "Les dates saisies sont invalides.";
            }
            return "Un format de données saisies est incorrect. Veuillez vérifier.";
        }

        // Column-related errors
        if (containsAny(lowerMessage, "column", "colonne")) {
            if (containsAny(lowerMessage, "titre")) {
                return "Le titre de l'événement est obligatoire.";
            }
            if (containsAny(lowerMessage, "type")) {
                return "Le type est obligatoire.";
            }
            if (containsAny(lowerMessage, "capacite", "capacité")) {
                return "La capacité maximale doit être un nombre entier positif.";
            }
            if (containsAny(lowerMessage, "prix", "price")) {
                return "Le prix du ticket doit être un nombre valide et positif.";
            }
            return "Une donnée saisie est invalide. Veuillez vérifier les informations.";
        }

        // String too long errors
        if (containsAny(lowerMessage, "data too long", "trop long", "string data right truncated")) {
            if (containsAny(lowerMessage, "titre")) {
                return "Le titre est trop long. Veuillez le réduire.";
            }
            if (containsAny(lowerMessage, "description")) {
                return "La description est trop longue. Veuillez la réduire.";
            }
            return "Une ou plusieurs valeurs sont trop longues. Veuillez réduire la taille du texte.";
        }

        // Connection errors
        if (containsAny(lowerMessage, "connection", "connexion", "database", "base de données", "communications link failure")) {
            return "Impossible de se connecter à la base de données. Veuillez réessayer.";
        }

        // Default fallback message
        return "Veuillez vérifier les informations de l'événement.";
    }

    /**
     * Helper method to check if a string contains any of the given keywords.
     *
     * @param text the text to search in
     * @param keywords the keywords to search for
     * @return true if the text contains at least one keyword
     */
    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}


