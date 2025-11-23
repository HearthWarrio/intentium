package io.hearthwarrio.intentium.core;

/**
 * Thrown when an intent cannot be resolved to a known role.
 */
public class IntentResolutionException extends RuntimeException {
    public IntentResolutionException(String message) {
        super(message);
    }
}
