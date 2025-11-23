package io.hearthwarrio.intentium.core;

/**
 * Thrown when no suitable element can be selected
 * or when the selection is ambiguous.
 */
public class ElementSelectionException extends RuntimeException {
    public ElementSelectionException(String message) {
        super(message);
    }
}
