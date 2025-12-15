package io.hearthwarrio.intentium.examples;

import io.hearthwarrio.intentium.core.Language;
import io.hearthwarrio.intentium.webdriver.IntentiumWebDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IntentiumRoleWidgetsIT {

    private WebDriver driver;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void shouldWorkWithRoleTextboxContentEditableAndRoleButton() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
        driver.manage().window().maximize();

        Path page = Paths.get("src", "test", "resources", "pages", "intentium-role-login.html");
        driver.get(page.toUri().toString());

        IntentiumWebDriver intentium = new IntentiumWebDriver(driver, Language.EN)
                .logLocators()
                .checkLocators();

        intentium.actionsChain()
                .into("login field").send("tomsmith")
                .into("password field").send("SuperSecretPassword!")
                .at("login button").performClick();

        String result = driver.findElement(By.id("result")).getText();
        assertEquals("OK", result);
    }
}
