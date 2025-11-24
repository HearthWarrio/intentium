package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-level entry point for using Intentium with Selenium WebDriver.
 *
 * v0.1:
 * - Resolve human intent ("login field") to IntentRole
 * - Collect candidates from the current page
 * - Select the best matching WebElement
 */
public class IntentiumWebDriver {
    private final WebDriver driver;
    private final Language language;
    private final IntentResolver intentResolver;
    private final ElementSelector elementSelector;
    private final WebDriverDomMapper domMapper;

    /**
     * Construct IntentiumWebDriver with default resolver and selector.
     */
    public IntentiumWebDriver(WebDriver driver, Language language) {
        this(
                driver,
                language,
                new DefaultIntentResolver(),
                new DefaultElementSelector()
        );
    }

    /**
     * Full constructor, if пользователь хочет подменить resolver/selector.
     */
    public IntentiumWebDriver(
            WebDriver driver,
            Language language,
            IntentResolver intentResolver,
            ElementSelector elementSelector
    ) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
        this.language = Objects.requireNonNull(language, "language must not be null");
        this.intentResolver = Objects.requireNonNull(intentResolver, "intentResolver must not be null");
        this.elementSelector = Objects.requireNonNull(elementSelector, "elementSelector must not be null");
        this.domMapper = new WebDriverDomMapper(driver);
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

        WebElement webElement = candidatesMap.get(match.getElement());
        if (webElement == null) {
            // Теоретически не должно случиться, но на всякий случай.
            throw new ElementSelectionException(
                    "Internal error: selected DomElementInfo has no corresponding WebElement"
            );
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
     * v0.1: очень простой вариант.
     */
    public String getXPath(String intentPhrase) {
        WebElement element = findElement(intentPhrase);
        return buildSimpleXPath(element);
    }

    /**
     * Get a simple CSS selector representation for a resolved element.
     * v0.1: очень простой вариант.
     */
    public String getCssSelector(String intentPhrase) {
        WebElement element = findElement(intentPhrase);
        return buildSimpleCssSelector(element);
    }

    // --- simple locator builders, v0.1 ---

    private String buildSimpleXPath(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) {
            return "//*[@id='" + id.replace("'", "\\'") + "']";
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return "//" + element.getTagName() + "[@name='" + name.replace("'", "\\'") + "']";
        }

        // fallback: just tagName (не очень стабильно, но лучше, чем ничего)
        return "//" + element.getTagName();
    }

    private String buildSimpleCssSelector(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) {
            return "#" + id.replace(" ", "\\ "); // примитивный escape
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return element.getTagName() + "[name='" + name.replace("'", "\\'") + "']";
        }

        // fallback: просто тег
        return element.getTagName();
    }
}
