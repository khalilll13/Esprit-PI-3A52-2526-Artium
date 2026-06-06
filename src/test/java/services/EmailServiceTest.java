package services;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailServiceTest {

    @Test
    void passwordResetEmailIsRenderedAsHtml() {
        String html = EmailService.buildPasswordResetHtmlBody("123456", LocalDateTime.of(2026, 5, 7, 18, 30));

        assertTrue(html.contains("<html lang=\"fr\">"));
        assertTrue(html.contains("text/html") || html.contains("<body"));
        assertTrue(html.contains("123456"));
        assertTrue(html.contains("07/05/2026 18:30"));
        assertTrue(html.contains("Votre code de réinitialisation est"));
    }
}

