package services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import entities.Evenement;
import entities.Galerie;
import entities.Ticket;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import utils.EnvLoader;

import javax.imageio.ImageIO;

public class TicketPdfService {

    private static final PDRectangle TICKET_PAGE = new PDRectangle(850, 350);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy 'à' HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("0.##");
    
    // VIP Colors (Brand matched)
    private static final java.awt.Color PAGE_BG_COLOR = new java.awt.Color(58, 57, 197); // #3A39C5
    private static final java.awt.Color TICKET_BG_COLOR = new java.awt.Color(255, 255, 255);
    private static final java.awt.Color TICKET_ACCENT_COLOR = new java.awt.Color(177, 240, 5); // #B1F005
    private static final java.awt.Color TEXT_PRIMARY = new java.awt.Color(17, 24, 39);
    private static final java.awt.Color TEXT_SECONDARY = new java.awt.Color(107, 114, 128);
    private final GalerieService galerieService = new GalerieService();
    private static final String LOGO_RESOURCE = "/views/assets/PNG Icon.png";
    private static final String WINDOWS_ARIAL = "C:/Windows/Fonts/arial.ttf";
    private static final String WINDOWS_ARIAL_BOLD = "C:/Windows/Fonts/arialbd.ttf";
    private static final int QR_SIZE = 280;
    // Configure with env vars or JVM props:
    // QR_API_URL / -Dqr.api.url
    // QR_API_KEY / -Dqr.api.key
    // QR_API_HOST / -Dqr.api.host
    // QR_API_LOGO_URL / -Dqr.api.logoUrl
    private static final String QR_API_URL_PROP = "qr.api.url";
    private static final String QR_API_KEY_PROP = "qr.api.key";
    private static final String QR_API_HOST_PROP = "qr.api.host";
    private static final String QR_API_LOGO_PROP = "qr.api.logoUrl";
    private static final String QR_API_SIZE_PROP = "qr.api.size";
    private static final String QR_API_FORMAT_PROP = "qr.api.format";
    private static final String QR_API_ECC_PROP = "qr.api.ecc";
    private static final String QR_API_MARGIN_PROP = "qr.api.margin";
    private static final String QR_API_QZONE_PROP = "qr.api.qzone";
    private static final String QR_API_COLOR_PROP = "qr.api.color";
    private static final String QR_API_BGCOLOR_PROP = "qr.api.bgcolor";
    private static final String QR_API_CHARSET_SOURCE_PROP = "qr.api.charsetSource";
    private static final String QR_API_CHARSET_TARGET_PROP = "qr.api.charsetTarget";
    private static final String QR_API_CONFIG_PATH_PROP = "qr.api.configPath";
    private static final String QR_API_STRICT_PROP = "qr.api.strict";
    private static final String QR_API_DEBUG_PROP = "qr.api.debug";
    private static final String QR_API_URL_ENV = "QR_API_URL";
    private static final String QR_API_KEY_ENV = "QR_API_KEY";
    private static final String QR_API_HOST_ENV = "QR_API_HOST";
    private static final String QR_API_LOGO_ENV = "QR_API_LOGO_URL";
    private static final String QR_API_SIZE_ENV = "QR_API_SIZE";
    private static final String QR_API_FORMAT_ENV = "QR_API_FORMAT";
    private static final String QR_API_ECC_ENV = "QR_API_ECC";
    private static final String QR_API_MARGIN_ENV = "QR_API_MARGIN";
    private static final String QR_API_QZONE_ENV = "QR_API_QZONE";
    private static final String QR_API_COLOR_ENV = "QR_API_COLOR";
    private static final String QR_API_BGCOLOR_ENV = "QR_API_BGCOLOR";
    private static final String QR_API_CHARSET_SOURCE_ENV = "QR_API_CHARSET_SOURCE";
    private static final String QR_API_CHARSET_TARGET_ENV = "QR_API_CHARSET_TARGET";
    private static final String QR_API_CONFIG_PATH_ENV = "QR_API_CONFIG_PATH";
    private static final String QR_API_STRICT_ENV = "QR_API_STRICT";
    private static final String QR_API_DEBUG_ENV = "QR_API_DEBUG";
    private static final String LOCAL_QR_CONFIG_FILE = "config/qr-api.local.properties";

    public File createPreviewPdf(Evenement event, Ticket ticket) throws IOException {
        File preview = Files.createTempFile("ticket-preview-", ".pdf").toFile();
        preview.deleteOnExit();
        exportTicketPdf(event, ticket, preview);
        return preview;
    }

    public File exportTicketPdf(Evenement event, Ticket ticket, File destination) throws IOException {
        validate(event, ticket, destination);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(TICKET_PAGE);
            document.addPage(page);

            PDFont regular = loadFont(document, WINDOWS_ARIAL, PDType1Font.HELVETICA);
            PDFont bold = loadFont(document, WINDOWS_ARIAL_BOLD, PDType1Font.HELVETICA_BOLD);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawTicket(document, content, event, ticket, regular, bold);
            }

            document.save(destination);
        }

        return destination;
    }

    public void openPdf(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IOException("Le fichier PDF est introuvable.");
        }

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(pdfFile);
                return;
            }
        }

        throw new IOException("L'ouverture automatique du PDF n'est pas supportée sur cette machine.");
    }

    public String buildSuggestedFileName(Evenement event, Ticket ticket) {
        String title = safeFileToken(event == null ? null : event.getTitre(), "ticket");
        String reference = safeFileToken(resolveReference(ticket), "ref");
        return "ticket-" + title + "-" + reference + ".pdf";
    }

    private void drawTicket(PDDocument document,
                            PDPageContentStream content,
                            Evenement event,
                            Ticket ticket,
                            PDFont regular,
                            PDFont bold) throws IOException {
        float width = TICKET_PAGE.getWidth();
        float height = TICKET_PAGE.getHeight();

        // 1. Draw Page Background (Dark Premium Theme)
        content.setNonStrokingColor(PAGE_BG_COLOR);
        content.addRect(0, 0, width, height);
        content.fill();

        // 2. Draw Main Ticket Card (White)
        float cardX = 40;
        float cardY = 40;
        float cardW = width - 80;
        float cardH = height - 80;
        
        drawRoundedRect(content, cardX, cardY, cardW, cardH, 20, TICKET_BG_COLOR);

        // 3. Right Stub coordinates
        float stubWidth = 240;
        float stubX = cardX + cardW - stubWidth;

        // 4. Draw Gold Accent Band on the Left
        content.setNonStrokingColor(TICKET_ACCENT_COLOR);
        content.addRect(cardX, cardY + 25, 12, cardH - 50);
        content.fill();

        // 5. Draw Perforated Line (Tear-off separator)
        content.setStrokingColor(new java.awt.Color(220, 220, 220));
        content.setLineWidth(2.5f);
        content.setLineDashPattern(new float[]{8, 8}, 0);
        content.moveTo(stubX, cardY + 15);
        content.lineTo(stubX, cardY + cardH - 15);
        content.stroke();
        content.setLineDashPattern(new float[]{}, 0); // reset dash

        // Draw Half circles for the perforation cutouts at top and bottom
        content.setNonStrokingColor(PAGE_BG_COLOR);
        drawCircle(content, stubX, cardY + cardH, 16); // Top cutout
        drawCircle(content, stubX, cardY, 16); // Bottom cutout

        // 6. Draw Content - Left Side (Main Event Info)
        float leftMargin = cardX + 45;
        float topMargin = cardY + cardH - 45;

        // ARTIUM Brand Header
        content.setNonStrokingColor(TICKET_ACCENT_COLOR);
        writeText(content, bold, leftMargin, topMargin, 16, "ARTIUM VIP ADMISSION");

        // Event Title (Huge and Bold)
        content.setNonStrokingColor(TEXT_PRIMARY);
        writeText(content, bold, leftMargin, topMargin - 45, 32, textOrDefault(event.getTitre(), "Évènement Exclusif").toUpperCase(), 450);

        // Event Type & Gallery
        content.setNonStrokingColor(TEXT_SECONDARY);
        writeText(content, regular, leftMargin, topMargin - 75, 13, (textOrDefault(event.getType(), "VIP EVENT") + "  •  " + resolveGalleryName(event)).toUpperCase());

        // Info Grid
        float gridY = topMargin - 130;
        drawInfoBlock(content, regular, bold, leftMargin, gridY, "DÉBUT DE L'ÉVÈNEMENT", formatDateTime(event.getDateDebut()));
        drawInfoBlock(content, regular, bold, leftMargin + 220, gridY, "FIN DE L'ÉVÈNEMENT", formatDateTime(event.getDateFin()));
        
        drawInfoBlock(content, regular, bold, leftMargin, gridY - 55, "LIEU", textOrDefault(event.getGalerieId() != null ? resolveGalleryName(event) : "Galerie non définie", "-").toUpperCase());
        drawInfoBlock(content, regular, bold, leftMargin + 220, gridY - 55, "PRIX DU TICKET", formatPrice(event.getPrixTicket()));

        // Reference text removed as requested
        
        // 7. Draw Content - Right Side (QR Code Stub)
        float qrSize = 150;
        float qrX = stubX + (stubWidth - qrSize) / 2;
        float qrY = cardY + (cardH - qrSize) / 2 + 15;

        BufferedImage qrImage = createQrImage(resolveQrPayload(ticket));
        if (qrImage != null) {
            PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage);
            content.drawImage(qr, qrX, qrY, qrSize, qrSize);
        }
        
        // Text under QR code removed as requested
    }

    private void drawInfoBlock(PDPageContentStream content, PDFont regular, PDFont bold, float x, float y, String label, String value) throws IOException {
        content.setNonStrokingColor(TEXT_SECONDARY);
        writeText(content, regular, x, y, 10, label);
        content.setNonStrokingColor(TEXT_PRIMARY);
        writeText(content, bold, x, y - 18, 14, value);
    }

    private void drawRoundedRect(PDPageContentStream content, float x, float y, float width, float height, float radius, java.awt.Color color) throws IOException {
        content.setNonStrokingColor(color);
        // Draw intersecting rects
        content.addRect(x + radius, y, width - 2 * radius, height);
        content.addRect(x, y + radius, width, height - 2 * radius);
        content.fill();
        // Draw 4 corners
        drawCircle(content, x + radius, y + radius, radius);
        drawCircle(content, x + width - radius, y + radius, radius);
        drawCircle(content, x + radius, y + height - radius, radius);
        drawCircle(content, x + width - radius, y + height - radius, radius);
    }

    private void drawCircle(PDPageContentStream content, float cx, float cy, float r) throws IOException {
        final float k = 0.552284749831f;
        content.moveTo(cx, cy + r);
        content.curveTo(cx + k * r, cy + r, cx + r, cy + k * r, cx + r, cy);
        content.curveTo(cx + r, cy - k * r, cx + k * r, cy - r, cx, cy - r);
        content.curveTo(cx - k * r, cy - r, cx - r, cy - k * r, cx - r, cy);
        content.curveTo(cx - r, cy + k * r, cx - k * r, cy + r, cx, cy + r);
        content.fill();
    }

    private void writeText(PDPageContentStream content, PDFont font, float x, float y, float size, String text) throws IOException {
        writeText(content, font, x, y, size, text, Float.MAX_VALUE);
    }

    private void writeText(PDPageContentStream content, PDFont font, float x, float y, float size, String text, float maxWidth) throws IOException {
        if (text == null) {
            return;
        }

        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);

        for (String line : wrapText(font, size, text, maxWidth)) {
            content.showText(line);
            content.newLineAtOffset(0, -size - 3);
        }

        content.endText();
    }

    private String[] wrapText(PDFont font, float size, String text, float maxWidth) throws IOException {
        if (maxWidth == Float.MAX_VALUE) {
            return new String[]{text};
        }

        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            float width = (font.getStringWidth(candidate) / 1000f) * size;
            if (width <= maxWidth || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        return lines.toArray(new String[0]);
    }

    public BufferedImage createQrImage(String payload) throws IOException {
        try {
            BufferedImage image = createQrImageFromApi(payload);
            debug("QR API used successfully.");
            return image;
        } catch (IOException apiFailure) {
            if (isApiStrict()) {
                throw new IOException("QR API failed in strict mode: " + apiFailure.getMessage(), apiFailure);
            }
            // Keep PDF export reliable even when API config/network/provider fails.
            debug("QR API failed, fallback ZXing used: " + apiFailure.getMessage());
            return createQrImageWithZxing(payload);
        }
    }

    private BufferedImage createQrImageFromApi(String payload) throws IOException {
        String endpoint = readConfig(QR_API_URL_PROP, QR_API_URL_ENV);
        if (endpoint.isBlank()) {
            throw new IOException("QR API non configuree (QR_API_URL ou -D" + QR_API_URL_PROP + ").");
        }
        debug("QR endpoint: " + endpoint);

        if (isQrServerEndpoint(endpoint)) {
            debug("QR provider detected: qrserver (GET)");
            return createQrImageFromQrServer(endpoint, payload);
        }
        debug("QR provider detected: generic/RapidAPI (POST)");

        String apiKey = readConfig(QR_API_KEY_PROP, QR_API_KEY_ENV);
        String apiHost = readConfig(QR_API_HOST_PROP, QR_API_HOST_ENV);
        String logoUrl = readConfig(QR_API_LOGO_PROP, QR_API_LOGO_ENV);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildQrApiBody(payload, logoUrl)));

        if (!apiKey.isBlank()) {
            requestBuilder.header("X-RapidAPI-Key", apiKey);
        }
        if (!apiHost.isBlank()) {
            requestBuilder.header("X-RapidAPI-Host", apiHost);
        }

        HttpResponse<byte[]> response;
        try {
            response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Appel API QR interrompu.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("API QR en echec: HTTP " + response.statusCode());
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (image == null) {
            throw new IOException("Reponse API QR invalide (image illisible).");
        }

        return toArgbImage(image);
    }

    private BufferedImage createQrImageFromQrServer(String endpoint, String payload) throws IOException {
        String requestUrl = buildQrServerUrl(endpoint, payload);
        debug("QR request URL: " + requestUrl);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();

        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Appel API QR interrompu.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("API QR en echec: HTTP " + response.statusCode());
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (image == null) {
            throw new IOException("Reponse API QR invalide (image illisible).");
        }

        return toArgbImage(image);
    }

    private boolean isQrServerEndpoint(String endpoint) {
        String normalized = endpoint.toLowerCase();
        return normalized.contains("api.qrserver.com") && normalized.contains("create-qr-code");
    }

    private String buildQrServerUrl(String endpoint, String payload) {
        String size = readConfig(QR_API_SIZE_PROP, QR_API_SIZE_ENV);
        if (size.isBlank()) {
            size = QR_SIZE + "x" + QR_SIZE;
        }

        String format = readConfig(QR_API_FORMAT_PROP, QR_API_FORMAT_ENV);
        if (format.isBlank()) {
            format = "png";
        }

        String ecc = readConfig(QR_API_ECC_PROP, QR_API_ECC_ENV);
        String margin = readConfig(QR_API_MARGIN_PROP, QR_API_MARGIN_ENV);
        String qzone = readConfig(QR_API_QZONE_PROP, QR_API_QZONE_ENV);
        String color = readConfig(QR_API_COLOR_PROP, QR_API_COLOR_ENV);
        String bgColor = readConfig(QR_API_BGCOLOR_PROP, QR_API_BGCOLOR_ENV);
        String charsetSource = readConfig(QR_API_CHARSET_SOURCE_PROP, QR_API_CHARSET_SOURCE_ENV);
        String charsetTarget = readConfig(QR_API_CHARSET_TARGET_PROP, QR_API_CHARSET_TARGET_ENV);

        StringBuilder url = new StringBuilder(endpoint);
        char separator = endpoint.contains("?") ? '&' : '?';

        separator = appendQueryParam(url, separator, "data", payload);
        separator = appendQueryParam(url, separator, "size", size);
        separator = appendQueryParam(url, separator, "format", format);
        separator = appendQueryParam(url, separator, "ecc", ecc);
        separator = appendQueryParam(url, separator, "margin", margin);
        separator = appendQueryParam(url, separator, "qzone", qzone);
        separator = appendQueryParam(url, separator, "color", color);
        separator = appendQueryParam(url, separator, "bgcolor", bgColor);
        separator = appendQueryParam(url, separator, "charset-source", charsetSource);
        appendQueryParam(url, separator, "charset-target", charsetTarget);

        return url.toString();
    }

    private char appendQueryParam(StringBuilder url, char separator, String key, String value) {
        if (value == null || value.isBlank()) {
            return separator;
        }

        url.append(separator)
                .append(key)
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));

        return '&';
    }

    private BufferedImage createQrImageWithZxing(String payload) throws IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (WriterException e) {
            throw new IOException("Impossible de générer le QR code du ticket.", e);
        }
    }

    private BufferedImage toArgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB || source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }

        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private String buildQrApiBody(String payload, String logoUrl) {
        // If your provider expects different field names, adjust this JSON only.
        StringBuilder body = new StringBuilder();
        body.append('{')
                .append("\"data\":\"").append(escapeJson(payload)).append("\",")
                .append("\"size\":").append(QR_SIZE).append(',')
                .append("\"format\":\"png\"");

        if (!logoUrl.isBlank()) {
            body.append(',').append("\"logo\":\"").append(escapeJson(logoUrl)).append("\"");
        }

        body.append('}');
        return body.toString();
    }

    private String readConfig(String propName, String envName) {
        String byEnvLoader = EnvLoader.get(envName);
        if (byEnvLoader != null && !byEnvLoader.isBlank()) {
            return byEnvLoader.trim();
        }

        byEnvLoader = EnvLoader.get(propName);
        if (byEnvLoader != null && !byEnvLoader.isBlank()) {
            return byEnvLoader.trim();
        }

        String byProp = System.getProperty(propName);
        if (byProp != null && !byProp.isBlank()) {
            return byProp.trim();
        }
        String byEnv = System.getenv(envName);
        if (byEnv != null && !byEnv.isBlank()) {
            return byEnv.trim();
        }
        return "";
    }

    private String readFromLocalConfig(String key) {
        return "";
    }

    private Properties loadLocalQrConfig() {
        return new Properties();
    }

    private Path resolveLocalConfigPath() {
        String customPath = firstNonBlank(
                System.getProperty(QR_API_CONFIG_PATH_PROP),
                System.getenv(QR_API_CONFIG_PATH_ENV)
        );
        if (!customPath.isBlank()) {
            return Paths.get(customPath.trim());
        }

        Path defaultPath = Paths.get(LOCAL_QR_CONFIG_FILE);
        if (defaultPath.isAbsolute()) {
            return defaultPath;
        }
        return Paths.get(System.getProperty("user.dir")).resolve(defaultPath).normalize();
    }

    private boolean isApiStrict() {
        String raw = readConfig(QR_API_STRICT_PROP, QR_API_STRICT_ENV);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private boolean isApiDebugEnabled() {
        String raw = readConfig(QR_API_DEBUG_PROP, QR_API_DEBUG_ENV);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private void debug(String message) {
        if (isApiDebugEnabled()) {
            System.out.println("[TicketPdfService] " + message);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return "";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private PDImageXObject loadLogo(PDDocument document) {
        try (InputStream inputStream = getClass().getResourceAsStream(LOGO_RESOURCE)) {
            if (inputStream == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                return null;
            }
            return LosslessFactory.createFromImage(document, image);
        } catch (IOException e) {
            return null;
        }
    }

    private PDFont loadFont(PDDocument document, String windowsPath, PDFont fallback) {
        File file = new File(windowsPath);
        if (file.exists()) {
            try {
                return PDType0Font.load(document, file);
            } catch (IOException ignored) {
                // fallback below
            }
        }
        return fallback;
    }

    private void validate(Evenement event, Ticket ticket, File destination) throws IOException {
        if (event == null) {
            throw new IOException("L'évènement est introuvable.");
        }
        if (ticket == null) {
            throw new IOException("Le ticket est introuvable.");
        }
        if (destination == null) {
            throw new IOException("La destination du PDF est introuvable.");
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Impossible de créer le dossier de destination.");
        }
    }

    private String resolveQrPayload(Ticket ticket) {
        if (ticket == null || ticket.getCodeQr() == null || ticket.getCodeQr().isBlank()) {
            return "QR non disponible";
        }
        return ticket.getCodeQr();
    }

    private String resolveReference(Ticket ticket) {
        if (ticket == null || ticket.getCodeQr() == null || ticket.getCodeQr().isBlank()) {
            return "-";
        }
        String payload = ticket.getCodeQr();
        int refIndex = payload.indexOf("|ref=");
        if (refIndex >= 0 && refIndex + 5 < payload.length()) {
            return payload.substring(refIndex + 5);
        }
        return payload;
    }

    private String resolveGalleryName(Evenement event) {
        if (event == null || event.getGalerieId() == null) {
            return "Galerie";
        }

        try {
            for (Galerie galerie : galerieService.getAll()) {
                if (galerie != null && event.getGalerieId().equals(galerie.getId())) {
                    return textOrDefault(galerie.getNom(), "Galerie");
                }
            }
        } catch (java.sql.SQLDataException ignored) {
            // Fallback below keeps the ticket readable even if the gallery lookup fails.
        }

        return "Galerie";
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String textOrDash(Integer value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "Date non définie" : DATE_TIME_FORMATTER.format(value);
    }

    private String formatDate(LocalDate value) {
        return value == null ? "-" : DATE_FORMATTER.format(value);
    }

    private String formatPrice(Double value) {
        return value == null ? "Prix non défini" : PRICE_FORMAT.format(value) + " TND";
    }

    private String formatCapacity(Integer value) {
        return value == null ? "Capacité non définie" : value + " personnes";
    }

    private String safeFileToken(String value, String fallback) {
        String token = value == null ? fallback : value.trim().toLowerCase();
        token = token.replaceAll("[^a-z0-9]+", "-");
        token = token.replaceAll("^-+|-+$", "");
        return token.isBlank() ? fallback : token;
    }
}


