package utils;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-zÀ-ÿ' -]{2,50}$");
    private static final Pattern CITY_PATTERN = Pattern.compile("^[A-Za-zÀ-ÿ' -]{2,60}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{8,15}$");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");

    private InputValidator() {
        // Utility class
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String clean(String value) {
        return value == null ? null : value.trim();
    }

    public static boolean isValidEmail(String email) {
        return !isBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidName(String value) {
        return !isBlank(value) && NAME_PATTERN.matcher(value.trim()).matches();
    }

    public static boolean isValidCity(String value) {
        return !isBlank(value) && CITY_PATTERN.matcher(value.trim()).matches();
    }

    public static boolean isValidPhone(String phone) {
        return !isBlank(phone) && PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    public static boolean isAdult(LocalDate dateNaissance, int minimumAge) {
        if (dateNaissance == null || dateNaissance.isAfter(LocalDate.now())) {
            return false;
        }
        return Period.between(dateNaissance, LocalDate.now()).getYears() >= minimumAge;
    }

    public static boolean isValidPassword(String password) {
        if (isBlank(password) || password.length() < 8) {
            return false;
        }
        return UPPERCASE_PATTERN.matcher(password).matches()
                && LOWERCASE_PATTERN.matcher(password).matches()
                && DIGIT_PATTERN.matcher(password).matches();
    }
}
