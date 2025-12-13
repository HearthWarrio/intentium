package io.hearthwarrio.intentium.testkit;

import io.hearthwarrio.intentium.core.Language;
import io.hearthwarrio.intentium.webdriver.IntentiumWebDriver;
import io.hearthwarrio.intentium.webdriver.LocatorLogDetail;
import org.openqa.selenium.WebDriver;

import java.util.Objects;

/**
 * Convenience factory methods for creating Intentium instances in tests.
 * <p>
 * Keeps test code minimal and consistent.
 * Does not depend on Allure.
 */
public final class TestIntentium {

    private TestIntentium() {
        // utility class
    }

    /**
     * Creates a plain IntentiumWebDriver without logging and without checks.
     */
    public static IntentiumWebDriver plain(WebDriver driver, Language language) {
        Objects.requireNonNull(driver, "driver must not be null");
        Objects.requireNonNull(language, "language must not be null");
        return new IntentiumWebDriver(driver, language);
    }

    /**
     * Creates an IntentiumWebDriver with stdout locator logging enabled.
     */
    public static IntentiumWebDriver stdout(WebDriver driver, Language language, LocatorLogDetail detail) {
        Objects.requireNonNull(driver, "driver must not be null");
        Objects.requireNonNull(language, "language must not be null");
        return new IntentiumWebDriver(driver, language)
                .withLoggingToStdOut(detail);
    }

    /**
     * Creates an IntentiumWebDriver with stdout locator logging and consistency checks enabled.
     */
    public static IntentiumWebDriver stdoutWithChecks(
            WebDriver driver,
            Language language,
            LocatorLogDetail detail
    ) {
        Objects.requireNonNull(driver, "driver must not be null");
        Objects.requireNonNull(language, "language must not be null");
        return new IntentiumWebDriver(driver, language)
                .withLoggingToStdOut(detail)
                .checkLocators();
    }
}
