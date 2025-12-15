package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.DomElementInfo;
import io.hearthwarrio.intentium.core.IntentRole;

/**
 * Receives information about a resolved element.
 * <p>
 * Implementations may log to stdout, Allure, files, etc.
 * <p>
 * Note: {@link #detail()} is used by Intentium to decide whether it should
 * build XPath/CSS strings at all (to avoid extra work).
 */
@FunctionalInterface
public interface ResolvedElementLogger {

    /**
     * Called after Intentium resolves the element.
     *
     * @param intentPhrase human intent phrase or target description
     * @param role         resolved semantic role (or a role derived from target)
     * @param xPath        XPath (may be null if not requested and not needed)
     * @param cssSelector  CSS selector (may be null if not requested and not needed)
     * @param elementInfo  lightweight DOM info (may be null for some target types)
     */
    void logResolvedElement(
            String intentPhrase,
            IntentRole role,
            String xPath,
            String cssSelector,
            DomElementInfo elementInfo
    );

    /**
     * Declares how much locator info this logger needs.
     * <p>
     * Default is {@link LocatorLogDetail#BOTH} to keep backward compatibility
     * with older behavior and custom lambda loggers.
     */
    default LocatorLogDetail detail() {
        return LocatorLogDetail.BOTH;
    }
}
