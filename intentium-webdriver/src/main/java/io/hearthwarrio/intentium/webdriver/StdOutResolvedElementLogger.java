package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.DomElementInfo;
import io.hearthwarrio.intentium.core.IntentRole;

import java.util.Objects;

/**
 * Default stdout logger for resolved elements.
 * <p>
 * Adds a small "origin" marker:
 * <ul>
 *   <li>manual – when intent/target is explicitly provided as By.xpath / By.cssSelector</li>
 *   <li>derived – when Intentium generated locator(s) from the resolved element</li>
 * </ul>
 */
public final class StdOutResolvedElementLogger implements ResolvedElementLogger {

    private final LocatorLogDetail detail;

    public StdOutResolvedElementLogger(LocatorLogDetail detail) {
        this.detail = Objects.requireNonNull(detail, "detail must not be null");
    }

    @Override
    public LocatorLogDetail detail() {
        return detail;
    }

    @Override
    public void logResolvedElement(
            String intentPhrase,
            IntentRole role,
            String xPath,
            String cssSelector,
            DomElementInfo elementInfo
    ) {
        if (detail == LocatorLogDetail.NONE) {
            // still log something minimal
            System.out.println("[Intentium] target='" + safe(intentPhrase) + "', role=" + role);
            return;
        }

        Origin origin = OriginResolver.resolve(intentPhrase);

        StringBuilder sb = new StringBuilder(256);
        sb.append("[Intentium] target='").append(safe(intentPhrase)).append('\'')
                .append(", role=").append(role);

        if (detail == LocatorLogDetail.XPATH_ONLY || detail == LocatorLogDetail.BOTH) {
            sb.append(", xpath(").append(origin.xpathOrigin).append(")=").append(nullSafe(xPath));
        }
        if (detail == LocatorLogDetail.CSS_ONLY || detail == LocatorLogDetail.BOTH) {
            sb.append(", css(").append(origin.cssOrigin).append(")=").append(nullSafe(cssSelector));
        }

        if (elementInfo != null) {
            // используем только то, что точно было у вас в коде: id/name
            sb.append(", id=").append(nullSafe(elementInfo.getId()))
                    .append(", name=").append(nullSafe(elementInfo.getName()));
        }

        System.out.println(sb);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String nullSafe(String s) {
        return s == null ? "null" : s;
    }

    private enum OriginMark {
        manual, derived
    }

    private static final class Origin {
        private final OriginMark xpathOrigin;
        private final OriginMark cssOrigin;

        private Origin(OriginMark xpathOrigin, OriginMark cssOrigin) {
            this.xpathOrigin = xpathOrigin;
            this.cssOrigin = cssOrigin;
        }
    }

    /**
     * Best-effort origin detection based on Selenium's By.toString().
     * Typical formats:
     * - "By.xpath: //div[@id='x']"
     * - "By.cssSelector: #x"
     */
    private static final class OriginResolver {

        private static Origin resolve(String targetDescription) {
            String s = targetDescription == null ? "" : targetDescription;

            boolean manualXpath = s.contains("By.xpath:");
            boolean manualCss = s.contains("By.cssSelector:");

            if (manualXpath && !manualCss) {
                return new Origin(OriginMark.manual, OriginMark.derived);
            }
            if (manualCss && !manualXpath) {
                return new Origin(OriginMark.derived, OriginMark.manual);
            }
            // default: both derived (pure intent phrase, WebElement target, proxy, etc.)
            return new Origin(OriginMark.derived, OriginMark.derived);
        }
    }
}
