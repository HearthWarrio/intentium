package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.DomElementInfo;
import io.hearthwarrio.intentium.core.IntentRole;

/**
 * Callback for logging resolved elements and their locators.
 */
public interface  ResolvedElementLogger {

    /**
     * Called when an intent has been resolved to a specific element.
     *
     * @param intentPhrase original human-readable intent, e.g. "login field"
     * @param role         resolved semantic role (LOGIN_FIELD, etc.)
     * @param xPath        simple XPath representation
     * @param cssSelector  simple CSS selector representation
     * @param elementInfo  DomElementInfo snapshot used for matching
     */
    void logResolvedElement(
            String intentPhrase,
            IntentRole role,
            String xPath,
            String cssSelector,
            DomElementInfo elementInfo
    );
}
