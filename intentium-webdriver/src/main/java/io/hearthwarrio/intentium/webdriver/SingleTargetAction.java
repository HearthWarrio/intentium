package io.hearthwarrio.intentium.webdriver;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Objects;

/**
 * Fluent helper for interacting with a single, already-known target.
 * <p>
 * This helper is designed for cases when the target is specified explicitly:
 * <ul>
 *   <li>a manual Selenium {@link By} locator</li>
 *   <li>an existing {@link WebElement} reference (for example, returned by PageFactory)</li>
 * </ul>
 * <p>
 * Compared to intent phrase resolution (see {@link SingleIntentAction}), this helper does not need to scan and score DOM
 * candidates. It acts as a "PageObject bridge" while still participating in Intentium logging and optional consistency
 * checks (when applicable).
 * <p>
 * Caching behavior:
 * <ul>
 *   <li>The target is resolved at most once per {@link SingleTargetAction} instance (best-effort).</li>
 *   <li>If a later call requires locators (XPath/CSS), the target may be resolved once more with locators enabled.</li>
 * </ul>
 */
public final class SingleTargetAction {

    private final IntentiumWebDriver intentium;
    private final By by;
    private final WebElement element;

    private IntentiumWebDriver.ResolvedElement cached;


    /**
     * Creates a single-target action helper bound to a manual {@link By} locator.
     *
     * @param intentium intentium driver facade
     * @param by Selenium locator describing the target
     */
    SingleTargetAction(IntentiumWebDriver intentium, By by) {
        this.intentium = Objects.requireNonNull(intentium, "intentium must not be null");
        this.by = Objects.requireNonNull(by, "by must not be null");
        this.element = null;
    }

    /**
     * Creates a single-target action helper bound to an existing {@link WebElement} reference.
     *
     * @param intentium intentium driver facade
     * @param element existing element reference
     */
    SingleTargetAction(IntentiumWebDriver intentium, WebElement element) {
        this.intentium = Objects.requireNonNull(intentium, "intentium must not be null");
        this.by = null;
        this.element = Objects.requireNonNull(element, "element must not be null");
    }


    /**
     * Resolves the target and performs {@link WebElement#sendKeys(CharSequence...)}.
     *
     * @param keys keys to send
     * @return this helper instance for fluent chaining
     */
    public SingleTargetAction send(CharSequence... keys) {
        resolve(false).element.sendKeys(keys);
        return this;
    }

    /**
     * Resolves the target and performs {@link WebElement#click()}.
     *
     * @return this helper instance for fluent chaining
     */
    public SingleTargetAction click() {
        resolve(false).element.click();
        return this;
    }

    /**
     * Resolves the target and returns the underlying {@link WebElement}.
     * <p>
     * The returned element reference is the same one used by {@link #click()} and {@link #send(CharSequence...)}.
     *
     * @return resolved target element
     */
    public WebElement element() {
        return resolve(false).element;
    }

    /**
     * Resolves the target and returns a best-effort XPath locator for it.
     * <p>
     * If the target is specified by an XPath {@link By} locator (or an equivalent can be extracted from a PageFactory
     * proxy), the returned value may represent that original locator. Otherwise the XPath is derived from the resolved
     * element as a best-effort "quick" locator.
     * <p>
     * The returned value may be {@code null} when locator derivation is not available.
     *
     * @return best-effort XPath for the target (may be {@code null})
     */
    public String xPath() {
        return resolve(true).xPath;
    }

    /**
     * Resolves the target and returns a best-effort CSS selector for it.
     * <p>
     * If the target is specified by a CSS {@link By} locator (or an equivalent can be extracted from a PageFactory
     * proxy), the returned value may represent that original locator. Otherwise the selector is derived from the resolved
     * element as a best-effort "quick" locator.
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
            cached = doResolve(forceLocators);
            return cached;
        }

        if (forceLocators && (cached.xPath == null || cached.cssSelector == null)) {
            cached = doResolve(true);
        }

        return cached;
    }

    private IntentiumWebDriver.ResolvedElement doResolve(boolean forceLocators) {
        if (by != null) {
            return intentium.resolveBy(by, forceLocators);
        }
        return intentium.resolveWebElement(element, forceLocators);
    }
}
