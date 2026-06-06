package utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailUtilTest {

    @Test
    void reclamationEmailIsRenderedAsHtmlAndEscapesContent() {
        String html = EmailUtil.buildHtmlEmail("L'utilisateur <test> & co\nDescription: Hello & goodbye");

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<html lang=\"fr\">"));
        assertTrue(html.contains("L'utilisateur &lt;test&gt; &amp; co"));
        assertTrue(html.contains("Description: Hello &amp; goodbye"));
        assertTrue(html.contains("white-space:pre-wrap"));
    }
}

