package services;

import entities.Evenement;
import entities.Ticket;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import utils.EnvLoader;

public class EmailServiceEvent {

    private static String SMTP_HOST = "smtp.gmail.com";
    private static String SMTP_PORT = "587";
    private static String SENDER_EMAIL = "";
    private static String SENDER_PASSWORD = "";

    static {
        SMTP_HOST = EnvLoader.get("MAIL_EVENT_SMTP_HOST");
        if (SMTP_HOST == null || SMTP_HOST.isEmpty()) {
            SMTP_HOST = EnvLoader.get("SMTP_HOST", "smtp.gmail.com");
        }
        
        SMTP_PORT = EnvLoader.get("MAIL_EVENT_SMTP_PORT");
        if (SMTP_PORT == null || SMTP_PORT.isEmpty()) {
            SMTP_PORT = EnvLoader.get("SMTP_PORT", "587");
        }
        
        SENDER_EMAIL = EnvLoader.get("MAIL_EVENT_SENDER_EMAIL");
        if (SENDER_EMAIL == null || SENDER_EMAIL.isEmpty()) {
            SENDER_EMAIL = EnvLoader.get("SMTP_USERNAME", "");
        }
        
        SENDER_PASSWORD = EnvLoader.get("MAIL_EVENT_SENDER_PASSWORD");
        if (SENDER_PASSWORD == null || SENDER_PASSWORD.isEmpty()) {
            SENDER_PASSWORD = EnvLoader.get("SMTP_PASSWORD", "");
        }
    }

    private final TicketPdfService ticketPdfService = new TicketPdfService();

    public void sendTicketEmailAsync(String recipientEmail, Evenement event, Ticket ticket) {
        CompletableFuture.runAsync(() -> {
            try {
                sendTicketEmail(recipientEmail, event, ticket);
            } catch (Exception e) {
                System.err.println("Échec de l'envoi de l'e-mail: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void sendTicketEmail(String recipientEmail, Evenement event, Ticket ticket) throws Exception {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            System.err.println("Cannot send email: recipient is empty.");
            return;
        }

        if (SENDER_EMAIL == null || SENDER_EMAIL.isBlank() || "votre.email@gmail.com".equals(SENDER_EMAIL)) {
            System.err.println("L'e-mail n'a pas été envoyé: Veuillez configurer vos identifiants dans config/mail.local.properties");
            return;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(SENDER_EMAIL, "ARTIUM Ticketing"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
        message.setSubject("Votre Ticket VIP pour l'évènement : " + event.getTitre());

        // Corps du message
        String bodyText = "Bonjour,\n\n"
                + "Félicitations pour votre achat ! Veuillez trouver ci-joint votre ticket d'accès VIP pour l'évènement '" + event.getTitre() + "'.\n"
                + "Le code QR présent sur ce ticket devra être scanné à l'entrée.\n\n"
                + "Nous vous souhaitons une excellente expérience.\n\n"
                + "Cordialement,\n"
                + "L'équipe ARTIUM";

        MimeBodyPart htmlPart = new MimeBodyPart();

        String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0; }" +
                ".container { max-width: 600px; margin: 20px auto; background: #ffffff; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 10px rgba(0,0,0,0.1); }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; font-size: 22px; }" +
                ".content { padding: 20px; color: #333; }" +
                ".content h2 { color: #4CAF50; }" +
                ".footer { background-color: #f1f1f1; padding: 15px; text-align: center; font-size: 12px; color: #777; }" +
                ".btn { display: inline-block; padding: 10px 15px; margin-top: 15px; background-color: #4CAF50; color: white; text-decoration: none; border-radius: 5px; }" +
                "</style>" +
                "</head>" +
                "<body>" +

                "<div class='container'>" +

                "<div class='header'>🎟️ ARTIUM Ticketing</div>" +

                "<div class='content'>" +
                "<h2>Bonjour 👋</h2>" +
                "<p>Félicitations pour votre achat !</p>" +
                "<p>Votre ticket VIP pour l'évènement <strong>" + event.getTitre() + "</strong> est prêt.</p>" +
                "<p>📌 Le code QR présent sur ce ticket devra être scanné à l'entrée.</p>" +
                "<p>Veuillez trouver votre ticket en pièce jointe.</p>" +
                "<p>Nous vous souhaitons une excellente expérience 🎉</p>" +
                "</div>" +

                "<div class='footer'>" +
                "© 2026 ARTIUM - Tous droits réservés" +
                "</div>" +

                "</div>" +

                "</body>" +
                "</html>";

        htmlPart.setContent(htmlContent, "text/html; charset=utf-8");

        // Pièce jointe (Le ticket PDF)
        File pdfFile = ticketPdfService.createPreviewPdf(event, ticket);
        MimeBodyPart attachmentPart = new MimeBodyPart();
        DataSource source = new FileDataSource(pdfFile);
        attachmentPart.setDataHandler(new DataHandler(source));
        attachmentPart.setFileName("Ticket_" + event.getTitre().replaceAll("[^a-zA-Z0-9]", "_") + ".pdf");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(htmlPart);
        multipart.addBodyPart(attachmentPart);

        message.setContent(multipart);

        Transport.send(message);
        System.out.println("L'e-mail avec le ticket PDF a bien été envoyé à " + recipientEmail);

        // Nettoyage du fichier temporaire
        if (pdfFile.exists()) {
            pdfFile.delete();
        }
    }
}
