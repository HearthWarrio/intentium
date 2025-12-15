package io.hearthwarrio.intentium.webdriver;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Objects;

/**
 * Small helper for single-target operations where the target is already known:
 * <ul>
 *   <li>a manual {@link By} locator</li>
 *   <li>a {@link WebElement} reference (e.g. from PageFactory)</li>
 * </ul>
 * <p>
 * This is the "PageObject bridge" counterpart to {@link SingleIntentAction}.
 * It still participates in Intentium logging and optional consistency checks.
 * <p>
 * Caching behavior:
 * <ul>
 *   <li>Resolves the target at most once per {@link SingleTargetAction} instance</li>
 *   <li>If a later call requires locators (XPath/CSS), it may re-resolve once with locators enabled</li>
 * </ul>
 */
public final class SingleTargetAction {

    private final IntentiumWebDriver intentium;
    private final By by;
    private final WebElement element;

    private IntentiumWebDriver.ResolvedElement cached;

    SingleTargetAction(IntentiumWebDriver intentium, By by) {
        this.intentium = Objects.requireNonNull(intentium, "intentium must not be null");
        this.by = Objects.requireNonNull(by, "by must not be null");
        this.element = null;
    }

    SingleTargetAction(IntentiumWebDriver intentium, WebElement element) {
        this.intentium = Objects.requireNonNull(intentium, "intentium must not be null");
        this.by = null;
        this.element = Objects.requireNonNull(element, "element must not be null");
    }

    /** Resolve target and send keys immediately. */
    public SingleTargetAction send(CharSequence... keys) {
        resolve(false).element.sendKeys(keys);
        return this;
    }

    /** Resolve target and click immediately. */
    public SingleTargetAction click() {
        resolve(false).element.click();
        return this;
    }

    /** Resolve target and return underlying {@link WebElement}. */
    public WebElement element() {
        return resolve(false).element;
    }

    /** Get XPath for this target. */
    public String xPath() {
        return resolve(true).xPath;
    }

    /** Get CSS selector for this target. */
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
