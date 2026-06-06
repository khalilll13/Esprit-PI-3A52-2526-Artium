package utils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class LoginRateLimiter {

    private static final int[] DELAYS_SECONDS = {
            0,   // 1 échec  → pas de délai
            0,   // 2 échecs → pas de délai
            0,   // 3 échecs → pas de délai
            30,  // 4 échecs → 30 secondes
            300, // 5 échecs → 5 minutes
    };

    private static final int MAX_ATTEMPTS = 6;

    private static final Map<String, LoginState> states = new HashMap<>();

    public static String check(String email) {
        LoginState state = states.get(email.toLowerCase());

        if (state == null) return null;

        if (state.attempts >= MAX_ATTEMPTS) {
            return "Compte bloqué après trop de tentatives. Utilisez 'Mot de passe oublié'.";
        }

        if (state.blockedUntil != null && LocalDateTime.now().isBefore(state.blockedUntil)) {
            long secondsLeft = java.time.Duration.between(
                    LocalDateTime.now(), state.blockedUntil
            ).toSeconds();

            if (secondsLeft > 60) {
                long minutes = (long) Math.ceil(secondsLeft / 60.0);
                return "Trop de tentatives. Réessayez dans " + minutes + " minute(s).";
            } else {
                return "Trop de tentatives. Réessayez dans " + secondsLeft + " seconde(s).";
            }
        }

        // ✅ FIX : si le délai est expiré, on remet blockedUntil à null
        if (state.blockedUntil != null && !LocalDateTime.now().isBefore(state.blockedUntil)) {
            state.blockedUntil = null;
        }

        return null;
    }

    public static void recordFailure(String email) {
        String key = email.toLowerCase();
        LoginState state = states.getOrDefault(key, new LoginState());

        state.attempts++;

        if (state.attempts < DELAYS_SECONDS.length) {
            int delay = DELAYS_SECONDS[state.attempts];
            if (delay > 0) {
                state.blockedUntil = LocalDateTime.now().plusSeconds(delay);
            }
        } else {
            // ✅ FIX : au lieu de rebloquer indéfiniment, on marque MAX_ATTEMPTS
            //          pour que check() retourne le message de blocage définitif
            state.attempts = MAX_ATTEMPTS;
            state.blockedUntil = null;
        }

        states.put(key, state);
    }

    public static void resetOnSuccess(String email) {
        states.remove(email.toLowerCase());
    }

    private static class LoginState {
        int attempts = 0;
        LocalDateTime blockedUntil = null;
    }
}