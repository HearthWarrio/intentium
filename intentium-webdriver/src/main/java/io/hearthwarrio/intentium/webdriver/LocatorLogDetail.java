package io.hearthwarrio.intentium.webdriver;

/**
 * Controls which locator data should be logged for a resolved element.
 * <p>
 * {@link #BOTH} is the preferred name for logging both XPath and CSS.
 * {@link #XPATH_AND_CSS} is kept for backward compatibility and is deprecated.
 */
public enum LocatorLogDetail {

    /**
     * Do not log any locator data.
     */
    NONE,

    /**
     * Log only XPath.
     */
    XPATH_ONLY,

    /**
     * Log only CSS selector.
     */
    CSS_ONLY,

    /**
     * Log both XPath and CSS selector.
     */
    BOTH,

    /**
     * Log both XPath and CSS selector.
     *
     * @deprecated Use {@link #BOTH}.
     */
    @Deprecated
    XPATH_AND_CSS
}