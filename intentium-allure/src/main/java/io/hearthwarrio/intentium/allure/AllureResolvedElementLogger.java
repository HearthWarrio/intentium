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

/**
 * {@link ResolvedElementLogger} integration for Allure.
 * <p>
 * Writes intent, role, and locator information to an Allure step.
 * Screenshots are strictly optional to avoid slowing down tests and inflating reports.
 */
public final class AllureResolvedElementLogger implements ResolvedElementLogger {

    private final WebDriver driver;
    private final LocatorLogDetail detail;
    private final boolean attachScreenshot;

    public AllureResolvedElementLogger(WebDriver driver, LocatorLogDetail detail, boolean attachScreenshot) {
        this.driver = driver;
        this.detail = detail == null ? LocatorLogDetail.NONE : detail;
        this.attachScreenshot = attachScreenshot;
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
        sb.append("Intent: '").append(intentPhrase).append('\'')
                .append("\nRole: ").append(role);

        if (shouldLogXPath(detail)) {
            sb.append("\nXPath: ").append(xPath);
        }
        if (shouldLogCss(detail)) {
            sb.append("\nCSS: ").append(cssSelector);
        }

        if (elementInfo != null) {
            sb.append("\n\nDomElementInfo:")
                    .append("\nTag: ").append(elementInfo.getTagName())
                    .append("\nId: ").append(elementInfo.getId())
                    .append("\nName: ").append(elementInfo.getName())
                    .append("\nType: ").append(elementInfo.getType())
                    .append("\nClasses: ").append(elementInfo.getCssClasses());
        }

        String title = "Intentium: '" + intentPhrase + "' → " + role;

        Allure.step(title, () -> {
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
