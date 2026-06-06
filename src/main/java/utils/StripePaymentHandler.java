package utils;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;

/**
 * Stripe payment handler for book rentals
 */
public class StripePaymentHandler {

    static {
        initialize();
    }

    /**
     * Initialize Stripe API key from .env
     */
    public static void initialize() {
        try {
            EnvLoader.loadEnv(true); // Force reload .env to catch any changes
            String apiKey = EnvLoader.get("STRIPE_BOOK_SECRET_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = EnvLoader.get("STRIPE_SECRET_KEY");
            }
            
            if (apiKey != null && !apiKey.isEmpty()) {
                Stripe.apiKey = apiKey;
                System.out.println("✓ Stripe API key initialized successfully");
            } else {
                System.err.println("✗ ERROR: STRIPE_BOOK_SECRET_KEY or STRIPE_SECRET_KEY not found in .env file!");
            }
        } catch (Exception e) {
            System.err.println("✗ ERROR initializing Stripe: " + e.getMessage());
        }
    }

    /**
     * Create a payment intent for book rental
     */
    public static PaymentIntent createPaymentIntent(long amountInCents, String bookTitle, int userId)
            throws StripeException {

        // Verify API key is set, try to re-initialize if not
        if (Stripe.apiKey == null || Stripe.apiKey.isEmpty()) {
            initialize();
            if (Stripe.apiKey == null || Stripe.apiKey.isEmpty()) {
                throw new IllegalStateException("Stripe API key not configured. Please check your .env file and ensure STRIPE_SECRET_KEY is set.");
            }
        }

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setDescription("Location de livre: " + bookTitle)
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("bookTitle", bookTitle)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build()
                )
                .build();

        return PaymentIntent.create(params);
    }

    /**
     * Retrieve payment intent
     */
    public static PaymentIntent retrievePaymentIntent(String paymentIntentId)
            throws StripeException {

        if (Stripe.apiKey == null || Stripe.apiKey.isEmpty()) {
            throw new IllegalStateException("Stripe API key not configured.");
        }

        return PaymentIntent.retrieve(paymentIntentId);
    }

    /**
     * Check if payment succeeded
     */
    public static boolean isPaymentSuccessful(String paymentIntentId)
            throws StripeException {

        if (Stripe.apiKey == null || Stripe.apiKey.isEmpty()) {
            throw new IllegalStateException("Stripe API key not configured.");
        }

        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        return "succeeded".equals(intent.getStatus());
    }
}