package utils;

import java.util.Optional;

public final class CoordinateUtils {

    private CoordinateUtils() {
    }

    public record Coordinates(double latitude, double longitude) {
    }

    public static Optional<Coordinates> parseCoordinates(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        String[] parts = normalized.split("\\s*,\\s*");
        if (parts.length != 2) {
            return Optional.empty();
        }

        try {
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
                return Optional.empty();
            }
            return Optional.of(new Coordinates(latitude, longitude));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static boolean isValidLatitude(double latitude) {
        return latitude >= -90.0d && latitude <= 90.0d;
    }

    public static boolean isValidLongitude(double longitude) {
        return longitude >= -180.0d && longitude <= 180.0d;
    }
}

