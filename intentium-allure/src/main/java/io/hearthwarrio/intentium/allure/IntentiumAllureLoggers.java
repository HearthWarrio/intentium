package io.hearthwarrio.intentium.allure;

import io.hearthwarrio.intentium.webdriver.LocatorLogDetail;
import io.hearthwarrio.intentium.webdriver.ResolvedElementLogger;
import org.openqa.selenium.WebDriver;

/**
 * Factory methods for Allure-related Intentium loggers.
 * <p>
 * This class lives in the intentium-allure module to avoid leaking Allure
 * dependencies into intentium-core or intentium-webdriver.
 */
public final class IntentiumAllureLoggers {

    private IntentiumAllureLoggers() {
        // utility class
    }

    /**
     * Creates an Allure logger that logs BOTH XPath and CSS without screenshots.
     */
    public static ResolvedElementLogger resolvedElements(WebDriver driver) {
        return new AllureResolvedElementLogger(driver, LocatorLogDetail.BOTH, false);
    }

    /**
     * Creates an Allure logger with explicit locator detail and screenshot flag.
     */
    public static ResolvedElementLogger resolvedElements(
            WebDriver driver,
            LocatorLogDetail detail,
            boolean screenshots
    ) {
        return new AllureResolvedElementLogger(driver, detail, screenshots);
    }
}
