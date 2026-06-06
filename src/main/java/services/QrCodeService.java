package services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class QrCodeService {

    private static final String QR_API_BASE = "https://api.qrserver.com/v1/create-qr-code/";
    private static final int DEFAULT_SIZE = 512;

    /**
     * Génère l'URL du QR code pour une oeuvre
     * @param oeuvreId ID de l'oeuvre à encoder
     * @return URL du QR code
     */
    public String generateQrCodeUrl(String oeuvreId) {
        return generateQrCodeUrl(oeuvreId, DEFAULT_SIZE);
    }

    /**
     * Génère l'URL du QR code avec une taille personnalisée
     * @param data Données à encoder
     * @param size Taille de l'image (pixels)
     * @return URL du QR code
     */
    public String generateQrCodeUrl(String data, int size) {
        try {
            String encoded = URLEncoder.encode(data, StandardCharsets.UTF_8);
            return QR_API_BASE + "?size=" + size + "x" + size + "&data=" + encoded;
        } catch (Exception e) {
            System.err.println("Erreur lors de la génération du QR code: " + e.getMessage());
            return null;
        }
    }
}

