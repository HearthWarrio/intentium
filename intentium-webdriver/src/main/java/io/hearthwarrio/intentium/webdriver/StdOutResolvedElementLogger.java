package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.DomElementInfo;
import io.hearthwarrio.intentium.core.IntentRole;

/**
 * Simple {@link ResolvedElementLogger} implementation that prints to {@link System#out}.
 * <p>
 * Used by DSL sugar methods like {@code logLocators()} and {@code withLoggingToStdOut(...)}.
 */
public final class StdOutResolvedElementLogger implements ResolvedElementLogger {

    private final LocatorLogDetail detail;

    public StdOutResolvedElementLogger(LocatorLogDetail detail) {
        this.detail = detail == null ? LocatorLogDetail.NONE : detail;
    }

    @Override
    public void logResolvedElement(
            String intentPhrase,
            IntentRole role,
            String xPath,
            String cssSelector,
            DomElementInfo elementInfo
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Intentium] intent='").append(intentPhrase).append('\'')
                .append(", role=").append(role);

        if (shouldLogXPath(detail)) {
            sb.append(", xpath=").append(xPath);
        }
        if (shouldLogCss(detail)) {
            sb.append(", css=").append(cssSelector);
        }

        if (elementInfo != null) {
            sb.append(", id=").append(elementInfo.getId())
                    .append(", name=").append(elementInfo.getName());
        }

        System.out.println(sb);
    }

    private boolean shouldLogXPath(LocatorLogDetail d) {
        return d == LocatorLogDetail.XPATH_ONLY
                || d == LocatorLogDetail.BOTH
                || d == LocatorLogDetail.XPATH_AND_CSS;
    }

    private boolean shouldLogCss(LocatorLogDetail d) {
        return d == LocatorLogDetail.CSS_ONLY
                || d == LocatorLogDetail.BOTH
                || d == LocatorLogDetail.XPATH_AND_CSS;
    }
}
