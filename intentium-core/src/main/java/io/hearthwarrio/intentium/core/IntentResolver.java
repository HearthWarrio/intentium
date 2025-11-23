package io.hearthwarrio.intentium.core;

/**
 * Resolves a raw human-readable intent (like "login field")
 * into a semantic role (LOGIN_FIELD, PASSWORD_FIELD, etc.).
 */
public interface IntentResolver {
    /**
     * Resolve the given intent phrase in the given language
     * to a semantic role.
     *
     * @param rawIntent raw human-readable phrase, e.g. "login field"
     * @param language  language of the phrase (EN, RU, ...)
     * @return resolved semantic role
     * @throws IntentResolutionException if the intent is unknown
     */
    IntentRole resolveRole(String rawIntent, Language language);
}
