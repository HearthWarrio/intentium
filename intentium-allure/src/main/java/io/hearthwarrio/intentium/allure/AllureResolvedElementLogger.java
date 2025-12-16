package io.hearthwarrio.intentium.allure;

import io.hearthwarrio.intentium.core.DomElementInfo;
import io.hearthwarrio.intentium.core.IntentRole;
import io.hearthwarrio.intentium.webdriver.LocatorLogDetail;
import io.hearthwarrio.intentium.webdriver.ResolvedElementLogger;
import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Allure logger for resolved elements.
 * <p>
 * Lives in intentium-allure to avoid leaking Allure dependency into core/webdriver.
 */
public final class AllureResolvedElementLogger implements ResolvedElementLogger {

    private final WebDriver driver;
    private final LocatorLogDetail detail;
    private final boolean attachScreenshot;

    public AllureResolvedElementLogger(WebDriver driver, LocatorLogDetail detail, boolean attachScreenshot) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");

        LocatorLogDetail d = detail == null ? LocatorLogDetail.NONE : detail;
        if (d == LocatorLogDetail.XPATH_AND_CSS) {
            d = LocatorLogDetail.BOTH;
        }
        this.detail = d;

        this.attachScreenshot = attachScreenshot;
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
        Origin origin = OriginResolver.resolve(intentPhrase);

        String title = "Intentium: " + safe(intentPhrase) + " – " + role;

        Allure.step(title, () -> {
            StringBuilder sb = new StringBuilder(512);

            sb.append("target: ").append(safe(intentPhrase)).append('\n')
                    .append("role: ").append(role).append('\n');

            if (detail == LocatorLogDetail.XPATH_ONLY || detail == LocatorLogDetail.BOTH) {
                sb.append("xpath(").append(origin.xpathOrigin).append("): ").append(nullSafe(xPath)).append('\n');
            }
            if (detail == LocatorLogDetail.CSS_ONLY || detail == LocatorLogDetail.BOTH) {
                sb.append("css(").append(origin.cssOrigin).append("): ").append(nullSafe(cssSelector)).append('\n');
            }

            if (elementInfo != null) {
                sb.append("dom.id: ").append(nullSafe(elementInfo.getId())).append('\n')
                        .append("dom.name: ").append(nullSafe(elementInfo.getName())).append('\n');
            }

            byte[] txt = sb.toString().getBytes(StandardCharsets.UTF_8);
            Allure.addAttachment(
                    "Resolved element",
                    "text/plain",
                    new ByteArrayInputStream(txt),
                    ".txt"
            );

            if (attachScreenshot && driver instanceof TakesScreenshot ts) {
                byte[] png = ts.getScreenshotAs(OutputType.BYTES);
                Allure.addAttachment(
                        "Screenshot",
                        "image/png",
                        new ByteArrayInputStream(png),
                        ".png"
                );
            }
        });
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
            return new Origin(OriginMark.derived, OriginMark.derived);
        }
    }
}
