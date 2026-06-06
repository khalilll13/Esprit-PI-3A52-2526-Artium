package utils;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateUtilsTest {

    @Test
    void parsesValidCoordinatesWithSpaces() {
        Optional<CoordinateUtils.Coordinates> result = CoordinateUtils.parseCoordinates("36.893612, 10.182190");

        assertTrue(result.isPresent());
        assertEquals(36.893612, result.get().latitude(), 0.0000001);
        assertEquals(10.182190, result.get().longitude(), 0.0000001);
    }

    @Test
    void parsesValidCoordinatesWithoutSpaces() {
        Optional<CoordinateUtils.Coordinates> result = CoordinateUtils.parseCoordinates("36.893612,10.182190");

        assertTrue(result.isPresent());
        assertEquals(36.893612, result.get().latitude(), 0.0000001);
        assertEquals(10.182190, result.get().longitude(), 0.0000001);
    }

    @Test
    void rejectsInvalidNumbers() {
        assertFalse(CoordinateUtils.parseCoordinates("bardo, tunis").isPresent());
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertFalse(CoordinateUtils.parseCoordinates("120.0, 10.0").isPresent());
        assertFalse(CoordinateUtils.parseCoordinates("36.0, 220.0").isPresent());
    }
}

