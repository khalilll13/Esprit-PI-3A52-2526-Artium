package utils;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailUtil {
    
    // Remplacez ces valeurs par vos propres identifiants SMTP pour tester l'envoi réel
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static String SENDER_EMAIL = "myriam24bouziri@gmail.com"; // Email expéditeur par défaut
    private static String SENDER_PASSWORD = ""; // Sera chargé depuis le fichier .env

    public static String ADMIN_EMAIL = "myriam24bouziri@gmail.com"; // Email de l'admin par défaut
    
    static {
        SENDER_EMAIL = EnvLoader.get("MAIL_SENDER_EMAIL", SENDER_EMAIL);
        SENDER_PASSWORD = EnvLoader.get("MAIL_SENDER_PASSWORD", SENDER_PASSWORD);
        ADMIN_EMAIL = EnvLoader.get("MAIL_ADMIN_EMAIL", ADMIN_EMAIL);
    }
    
    public static void sendEmailToAdmin(String subject, String content) {
        // Envoi asynchrone pour ne pas bloquer l'interface utilisateur (UI)
        new Thread(() -> {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", SMTP_PORT);
            // Utilisé pour certains serveurs exigeant SSL/TLS de manière plus stricte
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");

            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                }
            });

            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(SENDER_EMAIL));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(ADMIN_EMAIL));
                message.setSubject(subject);
                message.setContent(buildHtmlEmail(content), "text/html; charset=UTF-8");

                // Envoi réel de l'email
                Transport.send(message); 

                System.out.println("====== SIMULATION D'ENVOI D'EMAIL ======");
                System.out.println("À l'admin (" + ADMIN_EMAIL + "): " + subject);
                System.out.println("Contenu: \n" + content);
                System.out.println("========================================");
                
            } catch (Throwable e) {
                System.err.println("Erreur critique lors de l'envoi de l'email: " + e.getMessage());
                javafx.application.Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Erreur d'envoi d'email");
                    alert.setHeaderText("L'email n'a pas pu être envoyé.");
                    alert.setContentText("Détail de l'erreur : " + e + "\n\nVérifiez votre connexion, vos identifiants ou si Maven a bien téléchargé javax.mail.");
                    alert.showAndWait();
                });
            }
        }).start();
    }

    static String buildHtmlEmail(String content) {
        return "<!DOCTYPE html>"
                + "<html lang=\"fr\">"
                + "<body style=\"margin:0;padding:0;background-color:#f6f8fb;font-family:Arial,Helvetica,sans-serif;color:#1f2937;\">"
                + "<div style=\"max-width:720px;margin:0 auto;padding:32px 16px;\">"
                + "<div style=\"background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;padding:28px 32px;box-shadow:0 10px 24px rgba(15,23,42,0.08);\">"
                + "<h1 style=\"margin:0 0 18px;font-size:22px;color:#111827;\">Nouvelle réclamation</h1>"
                + "<div style=\"font-size:15px;line-height:1.7;white-space:pre-wrap;\">"
                + escapeHtml(content)
                + "</div>"
                + "</div></div></body></html>";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
