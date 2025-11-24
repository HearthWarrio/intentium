package io.hearthwarrio.intentium.webdriver;

import org.openqa.selenium.WebElement;

/**
 * Fluent helper for a single intent like:
 * intentium.into("login field").send("user");
 */
public final class SingleIntentAction {

    private final IntentiumWebDriver intentium;
    private final String intentPhrase;

    SingleIntentAction(IntentiumWebDriver intentium, String intentPhrase) {
        this.intentium = intentium;
        this.intentPhrase = intentPhrase;
    }

    /**
     * Resolve intent and send keys immediately.
     */
    public SingleIntentAction send(CharSequence... keys) {
        intentium.sendKeys(intentPhrase, keys);
        return this;
    }

    /**
     * Resolve intent and click immediately.
     */
    public SingleIntentAction click() {
        intentium.click(intentPhrase);
        return this;
    }

    /**
     * Resolve intent and return underlying WebElement.
     */
    public WebElement element() {
        return intentium.findElement(intentPhrase);
    }

    /**
     * Get simple XPath for this intent's element.
     */
    public String xPath() {
        return intentium.getXPath(intentPhrase);
    }

    /**
     * Get simple CSS selector for this intent's element.
     */
    public String cssSelector() {
        return intentium.getCssSelector(intentPhrase);
    }
}
