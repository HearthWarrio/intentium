package io.hearthwarrio.intentium.webdriver;

import org.openqa.selenium.WebElement;

/**
 * Small helper for single-intent operations.
 * <p>
 * Example:
 * <pre>
 * intentium.into("login field").send("user");
 * </pre>
 *
 * Caching behavior:
 * <ul>
 *   <li>Resolves the intent phrase at most once per {@link SingleIntentAction} instance</li>
 *   <li>If a later call requires locators (XPath/CSS), it may re-resolve once with locators enabled</li>
 * </ul>
 */
public final class SingleIntentAction {

    private final IntentiumWebDriver intentium;
    private final String intentPhrase;

    /**
     * Cached resolved element for this intent phrase.
     */
    private IntentiumWebDriver.ResolvedElement cached;

    SingleIntentAction(IntentiumWebDriver intentium, String intentPhrase) {
        this.intentium = intentium;
        this.intentPhrase = intentPhrase;
    }

    /**
     * Resolve intent and send keys immediately.
     */
    public SingleIntentAction send(CharSequence... keys) {
        resolve(false).element.sendKeys(keys);
        return this;
    }

    /**
     * Resolve intent and click immediately.
     */
    public SingleIntentAction click() {
        resolve(false).element.click();
        return this;
    }

    /**
     * Resolve intent and return underlying WebElement.
     */
    public WebElement element() {
        return resolve(false).element;
    }

    /**
     * Get XPath for this intent's element.
     */
    public String xPath() {
        return resolve(true).xPath;
    }

    /**
     * Get CSS selector for this intent's element.
     */
    public String cssSelector() {
        return resolve(true).cssSelector;
    }

    private IntentiumWebDriver.ResolvedElement resolve(boolean forceLocators) {
        if (cached == null) {
            cached = intentium.resolveIntent(intentPhrase, forceLocators);
            return cached;
        }

        if (forceLocators && (cached.xPath == null || cached.cssSelector == null)) {
            cached = intentium.resolveIntent(intentPhrase, true);
        }

        return cached;
    }
}
