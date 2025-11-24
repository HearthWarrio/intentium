package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.*;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.*;

/**
 * High-level entry point for using Intentium with Selenium WebDriver.
 *
 * v0.1:
 * - Resolve human intent ("login field") to IntentRole
 * - Collect candidates from the current page
 * - Select the best matching WebElement
 * - Optionally log XPath and CSS for the resolved element
 * - Optionally verify that XPath and CSS resolve to the same DOM element
 */
public class IntentiumWebDriver {

    private final WebDriver driver;
    private final Language language;
    private final IntentResolver intentResolver;
    private final ElementSelector elementSelector;
    private final WebDriverDomMapper domMapper;
    private final ResolvedElementLogger resolvedElementLogger;

    /**
     * If enabled, Intentium will re-resolve the selected element by the
     * generated XPath and CSS selectors and ensure they point to the same
     * DOM node as the original WebElement.
     */
    private boolean consistencyCheckEnabled = false;

    /**
     * Construct IntentiumWebDriver with default resolver/selector and no logging.
     */
    public IntentiumWebDriver(WebDriver driver, Language language) {
        this(driver, language, new DefaultIntentResolver(), new DefaultElementSelector(), null);
    }

    /**
     * Construct IntentiumWebDriver with default resolver/selector and custom logger.
     */
    public IntentiumWebDriver(WebDriver driver, Language language, ResolvedElementLogger logger) {
        this(driver, language, new DefaultIntentResolver(), new DefaultElementSelector(), logger);
    }

    /**
     * Construct IntentiumWebDriver with custom resolver/selector and no logging.
     */
    public IntentiumWebDriver(
            WebDriver driver,
            Language language,
            IntentResolver intentResolver,
            ElementSelector elementSelector
    ) {
        this(driver, language, intentResolver, elementSelector, null);
    }

    /**
     * Full constructor with custom resolver, selector and logger.
     */
    public IntentiumWebDriver(
            WebDriver driver,
            Language language,
            IntentResolver intentResolver,
            ElementSelector elementSelector,
            ResolvedElementLogger logger
    ) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
        this.language = Objects.requireNonNull(language, "language must not be null");
        this.intentResolver = Objects.requireNonNull(intentResolver, "intentResolver must not be null");
        this.elementSelector = Objects.requireNonNull(elementSelector, "elementSelector must not be null");
        this.domMapper = new WebDriverDomMapper(driver);
        this.resolvedElementLogger = logger;
    }

    /**
     * Enable XPath/CSS consistency check for resolved elements.
     *
     * @return this for chaining
     */
    public IntentiumWebDriver enableConsistencyCheck() {
        this.consistencyCheckEnabled = true;
        return this;
    }

    /**
     * Disable XPath/CSS consistency check.
     *
     * @return this for chaining
     */
    public IntentiumWebDriver disableConsistencyCheck() {
        this.consistencyCheckEnabled = false;
        return this;
    }

    public boolean isConsistencyCheckEnabled() {
        return consistencyCheckEnabled;
    }

    /**
     * Resolve a human-readable intent into a WebElement on the current page.
     *
     * @param intentPhrase e.g. "login field", "поле логина"
     * @return best matching WebElement
     *
     * @throws IntentResolutionException  if the phrase cannot be mapped to a role
     * @throws ElementSelectionException  if no suitable element is found
     *                                    or the result is ambiguous
     */
    public WebElement findElement(String intentPhrase) {
        IntentRole role = intentResolver.resolveRole(intentPhrase, language);

        Map<DomElementInfo, WebElement> candidatesMap = domMapper.collectCandidates();
        if (candidatesMap.isEmpty()) {
            throw new ElementSelectionException(
                    "No candidates found on page for role " + role
            );
        }

        List<DomElementInfo> domCandidates = new ArrayList<>(candidatesMap.keySet());
        ElementMatch match = elementSelector.selectBest(role, domCandidates);

        DomElementInfo elementInfo = match.getElement();
        WebElement webElement = candidatesMap.get(elementInfo);
        if (webElement == null) {
            throw new ElementSelectionException(
                    "Internal error: selected DomElementInfo has no corresponding WebElement"
            );
        }

        // Build locators once, use for logging and (optionally) for consistency check
        String xPath = buildSimpleXPath(webElement);
        String css = buildSimpleCssSelector(webElement);

        if (resolvedElementLogger != null) {
            resolvedElementLogger.logResolvedElement(
                    intentPhrase,
                    role,
                    xPath,
                    css,
                    elementInfo
            );
        }

        // Optional double-check: XPath and CSS should point to the same DOM node
        if (consistencyCheckEnabled) {
            runConsistencyCheck(intentPhrase, role, webElement, xPath, css);
        }

        return webElement;
    }

    /**
     * Convenience: click on element resolved from intent.
     */
    public void click(String intentPhrase) {
        WebElement element = findElement(intentPhrase);
        element.click();
    }

    /**
     * Convenience: send keys to element resolved from intent.
     */
    public void sendKeys(String intentPhrase, CharSequence... keys) {
        WebElement element = findElement(intentPhrase);
        element.sendKeys(keys);
    }

    /**
     * Get a simple XPath representation for a resolved element.
     * v0.1: very simple implementation.
     */
    public String getXPath(String intentPhrase) {
        WebElement element = findElement(intentPhrase);
        return buildSimpleXPath(element);
    }

    /**
     * Get a simple CSS selector representation for a resolved element.
     * v0.1: very simple implementation.
     */
    public String getCssSelector(String intentPhrase) {
        WebElement element = findElement(intentPhrase);
        return buildSimpleCssSelector(element);
    }

    /**
     * Start a single-intent fluent action:
     * intentium.into("login field").send("user");
     */
    public SingleIntentAction into(String intentPhrase) {
        return new SingleIntentAction(this, intentPhrase);
    }

    /**
     * Start a chain of actions:
     * actionsChain()
     *   .into("login field").send("user")
     *   .into("password field").send("secret")
     *   .at("login button").performClick();
     */
    public ActionsChain actionsChain() {
        return new ActionsChain(this);
    }

    // --- simple locator builders, v0.1 ---

    String buildSimpleXPath(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) {
            return "//*[@id='" + id.replace("'", "\\'") + "']";
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return "//" + element.getTagName() + "[@name='" + name.replace("'", "\\'") + "']";
        }

        // fallback: just tagName (not very stable, but better than nothing)
        return "//" + element.getTagName();
    }

    String buildSimpleCssSelector(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) {
            return "#" + id.replace(" ", "\\ ");
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return element.getTagName() + "[name='" + name.replace("'", "\\'") + "']";
        }

        // fallback: just tagName
        return element.getTagName();
    }

    // --- consistency check implementation ---

    private void runConsistencyCheck(
            String intentPhrase,
            IntentRole role,
            WebElement original,
            String xPath,
            String cssSelector
    ) {
        // Если локаторы совсем уж общие (//input и "input"), проверка только создаст шум.
        if (isGenericLocator(original, xPath, cssSelector)) {
            return;
        }

        try {
            WebElement byXPath = driver.findElement(By.xpath(xPath));
            WebElement byCss = driver.findElement(By.cssSelector(cssSelector));

            boolean xpathMatchesOriginal = original.equals(byXPath);
            boolean cssMatchesOriginal = original.equals(byCss);

            if (!xpathMatchesOriginal || !cssMatchesOriginal) {
                throw new ElementSelectionException(
                        "Locator consistency check failed for intent '" + intentPhrase + "', role " + role +
                                ". XPath/CSS do not resolve to the same DOM element as the original: " +
                                "xpathMatchesOriginal=" + xpathMatchesOriginal +
                                ", cssMatchesOriginal=" + cssMatchesOriginal +
                                ", xpath='" + xPath + "', css='" + cssSelector + '\''
                );
            }
        } catch (NoSuchElementException e) {
            throw new ElementSelectionException(
                    "Locator consistency check failed for intent '" + intentPhrase + "', role " + role +
                            ". Re-resolving element by XPath/CSS failed: " + e.getMessage()
            );
        }
    }

    private boolean isGenericLocator(WebElement element, String xPath, String cssSelector) {
        String tag = element.getTagName();
        String genericXPath = "//" + tag;
        String genericCss = tag;

        boolean xpathGeneric = genericXPath.equals(xPath);
        boolean cssGeneric = genericCss.equals(cssSelector);

        // Если хотя бы один из локаторов совсем общий — не мучаем страницу двойной проверкой.
        return xpathGeneric || cssGeneric;
    }
}
