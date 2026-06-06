package services;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.github.cdimascio.dotenv.Dotenv;

public class SmsService {
    private static final String ACCOUNT_SID;
    private static final String AUTH_TOKEN;
    private static final String FROM_NUMBER;

    static {
        // Charge les variables d'environnement depuis le fichier .env
        Dotenv dotenv = Dotenv.configure()
            .directory(System.getProperty("user.dir"))
            .ignoreIfMissing()
            .load();

        ACCOUNT_SID = dotenv.get("TWILIO_ACCOUNT_SID") != null
            ? dotenv.get("TWILIO_ACCOUNT_SID")
            : System.getenv("TWILIO_ACCOUNT_SID");

        AUTH_TOKEN = dotenv.get("TWILIO_AUTH_TOKEN") != null
            ? dotenv.get("TWILIO_AUTH_TOKEN")
            : System.getenv("TWILIO_AUTH_TOKEN");

        FROM_NUMBER = dotenv.get("TWILIO_FROM_NUMBER") != null
            ? dotenv.get("TWILIO_FROM_NUMBER")
            : System.getenv("TWILIO_FROM_NUMBER");

        if (ACCOUNT_SID == null || AUTH_TOKEN == null || FROM_NUMBER == null) {
            System.err.println("ERROR: Twilio credentials not found!");
            System.err.println("Looking for .env in: " + System.getProperty("user.dir"));
            System.err.println("Make sure .env file exists at the project root with TWILIO_* variables");
            throw new RuntimeException("Twilio credentials not found. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_FROM_NUMBER in .env or system environment variables.");
        }
    }

    public SmsService() {
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
    }

    public void sendSms(String to, String messageText) {
        Message message = Message.creator(
                new PhoneNumber(to),
                new PhoneNumber(FROM_NUMBER),
                messageText
        ).create();

        System.out.println("SMS sent with SID: " + message.getSid());
    }
}
