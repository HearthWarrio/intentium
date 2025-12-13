package io.hearthwarrio.intentium.examples;

import io.hearthwarrio.intentium.core.Language;
import io.hearthwarrio.intentium.webdriver.IntentiumWebDriver;
import io.hearthwarrio.intentium.webdriver.LocatorLogDetail;
import io.hearthwarrio.intentium.allure.AllureResolvedElementLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginPageIntentiumIT {
    private WebDriver driver;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void login_withStdOutLogging_andConsistencyCheck() {
        driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        driver.manage().window().maximize();
        driver.get("https://the-internet.herokuapp.com/login");

        IntentiumWebDriver intentium = new IntentiumWebDriver(driver, Language.EN)
                .logLocators()
                .checkLocators();

        intentium.actionsChain()
                .into("login field").send("tomsmith")
                .into("password field").send("SuperSecretPassword!")
                .at("login button").performClick();

        assertLoggedIn();
    }

    @Test
    void login_withAllureLogging_andConsistencyCheck() {
        driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        driver.manage().window().maximize();
        driver.get("https://the-internet.herokuapp.com/login");

        IntentiumWebDriver intentium = new IntentiumWebDriver(driver, Language.EN)
                .withLogger(new AllureResolvedElementLogger(driver, LocatorLogDetail.BOTH, false))
                .checkLocators();

        intentium.actionsChain()
                .into("login field").send("tomsmith")
                .into("password field").send("SuperSecretPassword!")
                .at("login button").performClick();

        assertLoggedIn();
    }

    private void assertLoggedIn() {
        String flash = driver.findElement(By.id("flash")).getText();
        assertTrue(flash.contains("You logged into a secure area!"),
                "Expected success flash message, got: " + flash);

        assertTrue(driver.getCurrentUrl().contains("/secure"),
                "Expected URL to contain /secure, got: " + driver.getCurrentUrl());
    }
}
