package io.hearthwarrio.intentium.webdriver;

import org.openqa.selenium.WebElement;

/**
 * Fluent helper for interacting with a single target identified by an intent phrase.
 * <p>
 * This helper is created via {@link IntentiumWebDriver#into(String)} (or {@link IntentiumWebDriver#at(String)}) and is
 * useful when you want to perform multiple operations against the same intent phrase without repeating resolution calls.
 * <p>
 * Snapshot contract:
 * <ul>
 *   <li>This helper uses direct intent resolution via {@link IntentiumWebDriver#resolveIntent(String, boolean)}.</li>
 *   <li>The first resolution may collect a fresh DOM candidates snapshot (as per direct API contract).</li>
 *   <li>Snapshot reuse across multiple intents within one logical step is guaranteed only within {@link ActionsChain}
 *       and one {@link ActionsChain#perform()} call.</li>
 * </ul>
 * <p>
 * Caching behavior:
 * <ul>
 *   <li>Resolves the intent phrase at most once per {@link SingleIntentAction} instance (best-effort).</li>
 *   <li>If a later call requires locators (XPath/CSS), it may re-resolve once with locators enabled.</li>
 * </ul>
 *
 * <pre>
 * intentium.into("login field").send("user");
 * intentium.into("submit button").click();
 * </pre>
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
     * Resolves the intent phrase and performs {@link WebElement#sendKeys(CharSequence...)} on the resolved element.
     *
     * @param keys keys to send
     * @return this helper instance for fluent chaining
     */
    public SingleIntentAction send(CharSequence... keys) {
        resolve(false).element.sendKeys(keys);
        return this;
    }

    /**
     * Resolves the intent phrase and performs {@link WebElement#click()} on the resolved element.
     *
     * @return this helper instance for fluent chaining
     */
    public SingleIntentAction click() {
        resolve(false).element.click();
        return this;
    }

    /**
     * Resolves the intent phrase and returns the underlying {@link WebElement}.
     * <p>
     * The returned element reference is the same one used by {@link #click()} and {@link #send(CharSequence...)}.
     *
     * @return resolved target element
     */
    public WebElement element() {
        return resolve(false).element;
    }

    /**
     * Resolves the intent phrase and returns a best-effort XPath locator for the resolved element.
     * <p>
     * The returned value may be {@code null} when locator derivation is not available.
     *
     * @return best-effort XPath for the target (may be {@code null})
     */
    public String xPath() {
        return resolve(true).xPath;
    }

    /**
     * Resolves the intent phrase and returns a best-effort CSS selector for the resolved element.
     * <p>
     * The returned value may be {@code null} when locator derivation is not available.
     *
     * @return best-effort CSS selector for the target (may be {@code null})
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
