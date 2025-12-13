package io.hearthwarrio.intentium.testkit;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.URL;
import java.time.Duration;
import java.util.Objects;

/**
 * Minimal WebDriver factory for tests.
 * <p>
 * This is intentionally simple and opinionated:
 * - local Chrome by default
 * - optional customization via ChromeOptions
 * - optional RemoteWebDriver support
 * <p>
 * Testkit lives outside Intentium core to avoid turning Intentium into a test framework.
 */
public final class TestDrivers {
    /**
     * Default implicit wait used by the testkit.
     */
    public static final Duration DEFAULT_IMPLICIT_WAIT = Duration.ofSeconds(5);

    private TestDrivers() {
        // utility class
    }

    /**
     * Creates a local ChromeDriver with default settings.
     */
    public static WebDriver chrome() {
        return chrome(new ChromeOptions());
    }

    /**
     * Creates a local ChromeDriver with provided options.
     */
    public static WebDriver chrome(ChromeOptions options) {
        Objects.requireNonNull(options, "options must not be null");

        WebDriver driver = new ChromeDriver(options);
        applyDefaults(driver);
        return driver;
    }

    /**
     * Creates a RemoteWebDriver with provided Selenium Grid URL and capabilities.
     */
    public static WebDriver remote(URL remoteUrl, Capabilities capabilities) {
        Objects.requireNonNull(remoteUrl, "remoteUrl must not be null");
        Objects.requireNonNull(capabilities, "capabilities must not be null");

        WebDriver driver = new RemoteWebDriver(remoteUrl, capabilities);
        applyDefaults(driver);
        return driver;
    }

    private static void applyDefaults(WebDriver driver) {
        driver.manage().timeouts().implicitlyWait(DEFAULT_IMPLICIT_WAIT);
        driver.manage().window().maximize();
    }
}
