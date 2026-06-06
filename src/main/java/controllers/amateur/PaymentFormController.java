package controllers.amateur;

import com.stripe.Stripe;
import com.stripe.model.Charge;
import com.stripe.model.Token;
import controllers.MainFX;
import entities.Evenement;
import entities.Ticket;
import entities.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import services.TicketService;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

public class PaymentFormController {

    @FXML
    private Label paymentTitleLabel;
    @FXML
    private Label paymentAmountLabel;
    @FXML
    private TextField cardNumberField;
    @FXML
    private TextField expMonthField;
    @FXML
    private TextField expYearField;
    @FXML
    private TextField cvcField;
    @FXML
    private TextField cardHolderField;
    @FXML
    private Button payButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Label statusLabel;

    private Evenement event;
    private Consumer<Ticket> successHandler;
    private Runnable cancelHandler;
    private final TicketService ticketService = new TicketService();

    public void setEvent(Evenement event) {
        this.event = event;
        if (event != null) {
            paymentTitleLabel.setText("Achat ticket: " + (event.getTitre() != null ? event.getTitre() : "Evénement"));
            double price = event.getPrixTicket() != null ? event.getPrixTicket() : 0.0;
            paymentAmountLabel.setText(String.format("Montant: %.2f TND", price));
            payButton.setText(String.format("Payer %.2f TND", price));
        }
    }

    public void setSuccessHandler(Consumer<Ticket> successHandler) {
        this.successHandler = successHandler;
    }

    public void setCancelHandler(Runnable cancelHandler) {
        this.cancelHandler = cancelHandler;
    }

    @FXML
    private void onPayClick() {
        if (event == null) return;
        
        User user = MainFX.getAuthenticatedUser();
        if (user == null || user.getId() == null) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Vous devez être connecté.");
            return;
        }

        String cardNumber = cardNumberField.getText().trim();
        String expMonth = expMonthField.getText().trim();
        String expYear = expYearField.getText().trim();
        String cvc = cvcField.getText().trim();

        if (cardNumber.isEmpty() || expMonth.isEmpty() || expYear.isEmpty() || cvc.isEmpty()) {
            statusLabel.setText("Veuillez remplir tous les champs.");
            return;
        }

        payButton.setDisable(true);
        statusLabel.setText("Traitement du paiement en cours...");

        // Process in background to avoid freezing UI
        new Thread(() -> {
            try {
                String secretKey = utils.EnvLoader.get("STRIPE_EVENT_SECRET_KEY");
                if (secretKey == null || secretKey.isEmpty()) {
                    secretKey = utils.EnvLoader.get("STRIPE_SECRET_KEY");
                }
                String currency = utils.EnvLoader.get("STRIPE_CURRENCY", "usd");
                
                if (secretKey == null || secretKey.isEmpty()) {
                    throw new Exception("Clé Stripe non configurée.");
                }

                Stripe.apiKey = secretKey;

                // Create Charge directly using a Stripe test token since raw card APIs are disabled by default
                double price = event.getPrixTicket() != null ? event.getPrixTicket() : 0.0;
                long amountInCents = (long) (price * 100);
                if (amountInCents == 0) amountInCents = 100; // minimum amount

                Map<String, Object> chargeParams = new HashMap<>();
                chargeParams.put("amount", amountInCents);
                chargeParams.put("currency", currency);
                
                // For test mode without raw card data API access, we use standard test tokens:
                chargeParams.put("source", "tok_visa"); 
                
                chargeParams.put("description", "Paiement ticket: " + event.getTitre());

                Charge.create(chargeParams);

                // Success! Purchase ticket in DB
                Ticket ticket = ticketService.purchaseTicket(event, user.getId());

                Platform.runLater(() -> {
                    if (successHandler != null) {
                        successHandler.accept(ticket);
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    payButton.setDisable(false);
                    statusLabel.setText("Erreur: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void onCancelClick() {
        if (cancelHandler != null) {
            cancelHandler.run();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
