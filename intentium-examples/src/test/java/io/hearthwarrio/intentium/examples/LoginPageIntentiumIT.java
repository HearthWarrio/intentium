package io.hearthwarrio.intentium.examples;

import io.hearthwarrio.intentium.core.Language;
import io.hearthwarrio.intentium.webdriver.IntentiumWebDriver;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginPageIntentiumIT {
    @Test
    void successfulLoginWithIntentium() {
        WebDriver driver = new ChromeDriver();
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            driver.manage().window().maximize();

            driver.get("https://the-internet.herokuapp.com/login");

            IntentiumWebDriver intentium = new IntentiumWebDriver(driver, Language.EN);

            intentium.actionsChain()
                    .into("login field").send("tomsmith")
                    .into("password field").send("SuperSecretPassword!")
                    .at("login button").performClick();

            WebElement flash = driver.findElement(By.id("flash"));
            String text = flash.getText();
            assertTrue(
                    text.contains("You logged into a secure area!"),
                    "Expected success message after login, but was: " + text
            );
        } finally {
            driver.quit();
        }
    }
}
