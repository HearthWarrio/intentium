package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.*;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.*;

/**
 * High-level Intentium entry point for Selenium WebDriver.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Resolve a human intent phrase to an {@link IntentRole}</li>
 *   <li>Collect DOM candidates via {@link WebDriverDomMapper}</li>
 *   <li>Select the best candidate via {@link ElementSelector}</li>
 *   <li>Optionally log XPath/CSS via {@link ResolvedElementLogger}</li>
 *   <li>Optionally verify XPath/CSS consistency</li>
 * </ul>
 */
public class IntentiumWebDriver {

    private final WebDriver driver;
    private final Language language;
    private final IntentResolver intentResolver;
    private final ElementSelector elementSelector;
    private final WebDriverDomMapper domMapper;

    /**
     * Mutable to support runtime overrides and DSL sugar.
     */
    private ResolvedElementLogger resolvedElementLogger;

    private boolean consistencyCheckEnabled = false;

    public IntentiumWebDriver(WebDriver driver, Language language) {
        this(driver, language, new DefaultIntentResolver(), new DefaultElementSelector(), null);
    }

    public IntentiumWebDriver(WebDriver driver, Language language, ResolvedElementLogger logger) {
        this(driver, language, new DefaultIntentResolver(), new DefaultElementSelector(), logger);
    }

    public IntentiumWebDriver(
            WebDriver driver,
            Language language,
            IntentResolver intentResolver,
            ElementSelector elementSelector
    ) {
        this(driver, language, intentResolver, elementSelector, null);
    }

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

    // ----------- configuration (low-level) -----------

    /**
     * Sets a custom logger implementation for resolved elements.
     */
    public IntentiumWebDriver withLogger(ResolvedElementLogger logger) {
        this.resolvedElementLogger = logger;
        return this;
    }

    /**
     * Enables stdout logging using a built-in {@link StdOutResolvedElementLogger}.
     */
    public IntentiumWebDriver withLoggingToStdOut(LocatorLogDetail detail) {
        this.resolvedElementLogger = new StdOutResolvedElementLogger(detail);
        return this;
    }

    /**
     * Enables or disables XPath/CSS consistency checks for this instance.
     */
    public IntentiumWebDriver withConsistencyCheck(boolean enabled) {
        this.consistencyCheckEnabled = enabled;
        return this;
    }

    // ----------- configuration (sugar, minimal set) -----------

    /**
     * Sugar: enables stdout logging of both XPath and CSS.
     */
    public IntentiumWebDriver logLocators() {
        return withLoggingToStdOut(LocatorLogDetail.BOTH);
    }

    /**
     * Sugar: disables locator logging.
     */
    public IntentiumWebDriver disableLocatorLogging() {
        this.resolvedElementLogger = null;
        return this;
    }

    /**
     * Sugar: enables consistency checks.
     */
    public IntentiumWebDriver checkLocators() {
        this.consistencyCheckEnabled = true;
        return this;
    }

    /**
     * Sugar: disables consistency checks.
     */
    public IntentiumWebDriver disableLocatorChecks() {
        this.consistencyCheckEnabled = false;
        return this;
    }

    public boolean isConsistencyCheckEnabled() {
        return consistencyCheckEnabled;
    }

    // package-private for ActionsChain overrides
    ResolvedElementLogger getResolvedElementLogger() {
        return resolvedElementLogger;
    }

    void setResolvedElementLogger(ResolvedElementLogger logger) {
        this.resolvedElementLogger = logger;
    }

    // ----------- main API -----------

    /**
     * Resolves an intent phrase to a WebElement on the current page.
     *
     * @throws IntentResolutionException if the phrase cannot be mapped to a role
     * @throws ElementSelectionException if the element cannot be selected
     */
    public WebElement findElement(String intentPhrase) {
        IntentRole role = intentResolver.resolveRole(intentPhrase, language);

        Map<DomElementInfo, WebElement> candidatesMap = domMapper.collectCandidates();
        if (candidatesMap.isEmpty()) {
            throw new ElementSelectionException("No candidates found on page for role " + role);
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

        String xPath = buildSimpleXPath(webElement);
        String css = buildSimpleCssSelector(webElement);

        if (resolvedElementLogger != null) {
            resolvedElementLogger.logResolvedElement(intentPhrase, role, xPath, css, elementInfo);
        }

        if (consistencyCheckEnabled) {
            runConsistencyCheck(intentPhrase, role, webElement, xPath, css);
        }

        return webElement;
    }

    /**
     * Convenience: resolves by intent and clicks.
     */
    public void click(String intentPhrase) {
        findElement(intentPhrase).click();
    }

    /**
     * Convenience: resolves by intent and sends keys.
     */
    public void sendKeys(String intentPhrase, CharSequence... keys) {
        findElement(intentPhrase).sendKeys(keys);
    }

    /**
     * Returns a simple XPath for the resolved element (v0.1).
     */
    public String getXPath(String intentPhrase) {
        return buildSimpleXPath(findElement(intentPhrase));
    }

    /**
     * Returns a simple CSS selector for the resolved element (v0.1).
     */
    public String getCssSelector(String intentPhrase) {
        return buildSimpleCssSelector(findElement(intentPhrase));
    }

    /**
     * Starts a single-intent action helper.
     */
    public SingleIntentAction into(String intentPhrase) {
        return new SingleIntentAction(this, intentPhrase);
    }

    /**
     * Starts an action chain DSL.
     */
    public ActionsChain actionsChain() {
        return new ActionsChain(this);
    }

    // ----------- simple locator builders (v0.1) -----------

    String buildSimpleXPath(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) {
            return "//*[@id='" + id.replace("'", "\\'") + "']";
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return "//" + element.getTagName() + "[@name='" + name.replace("'", "\\'") + "']";
        }

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

        return element.getTagName();
    }

    // ----------- consistency check -----------

    private void runConsistencyCheck(
            String intentPhrase,
            IntentRole role,
            WebElement original,
            String xPath,
            String cssSelector
    ) {
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
        boolean xpathGeneric = ("//" + tag).equals(xPath);
        boolean cssGeneric = tag.equals(cssSelector);
        return xpathGeneric || cssGeneric;
    }
}
