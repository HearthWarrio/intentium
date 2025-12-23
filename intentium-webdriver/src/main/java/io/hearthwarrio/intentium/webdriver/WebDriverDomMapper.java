package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.DomElementInfo;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Maps Selenium {@link WebElement} to {@link DomElementInfo} snapshots.
 * <p>
 * This class is not thread-safe and is expected to be used from a single test thread.
 */
public class WebDriverDomMapper {

    private static final String HINT_PREFIX = "[hint:";
    private static final String HINT_SUFFIX = "]";

    /**
     * Default whitelist of "test/qa" attributes (for example, {@code data-testid}, {@code data-test-id}, {@code data-qa}).
     * <p>
     * The order matters: the first present attribute wins.
     * <p>
     * This list is immutable. If you need a custom set, pass your own list via
     * {@link #WebDriverDomMapper(WebDriver, List)}.
     */
    public static final List<String> DEFAULT_TEST_ATTRIBUTE_WHITELIST = List.of(
            "data-testid",
            "data-test-id",
            "data-test",
            "data-qa",
            "data-cy",
            "data-automation-id",
            "data-automation"
    );

    private static final class TestAttribute {
        final String name;
        final String value;

        private TestAttribute(String name, String value) {
            this.name = name == null ? "" : name;
            this.value = value == null ? "" : value;
        }

        boolean isPresent() {
            return !name.isBlank() && !value.isBlank();
        }
    }

    private final WebDriver driver;
    private final JavascriptExecutor js;
    private final List<String> testAttributeWhitelist;

    /**
     * Creates a mapper with the default test attribute whitelist.
     *
     * @param driver Selenium WebDriver instance
     */
    public WebDriverDomMapper(WebDriver driver) {
        this(driver, new ArrayList<>(DEFAULT_TEST_ATTRIBUTE_WHITELIST));
    }

    /**
     * Creates a DOM mapper with a configurable whitelist of test attributes.
     * <p>
     * The order matters: the first present attribute wins.
     * <p>
     * The provided list instance is used as-is (no defensive copy). This allows configuration to be changed at runtime
     * by mutating the list (for example, via { IntentiumWebDriver#withTestAttributeWhitelist(String...)}).
     *
     * @param driver Selenium WebDriver instance
     * @param testAttributeWhitelist whitelist of attribute names; empty list disables test attribute detection
     */
    public WebDriverDomMapper(WebDriver driver, List<String> testAttributeWhitelist) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
        this.js = (JavascriptExecutor) driver;
        this.testAttributeWhitelist = Objects.requireNonNull(
                testAttributeWhitelist,
                "testAttributeWhitelist must not be null"
        );
    }

    /**
     * Candidate pair container.
     */
    public static final class Candidate {
        private final DomElementInfo info;
        private final WebElement element;

        Candidate(DomElementInfo info, WebElement element) {
            this.info = info;
            this.element = element;
        }

        public DomElementInfo getInfo() {
            return info;
        }

        public WebElement getElement() {
            return element;
        }
    }

    /**
     * Collects Selenium candidates and maps them to {@link DomElementInfo}.
     *
     * @return list of candidates; may be empty when DOM is not accessible
     */
    public List<Candidate> collectCandidateList() {
        List<WebElement> candidates = collectDomCandidates();
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Candidate> out = new ArrayList<>(candidates.size());
        for (WebElement element : candidates) {
            out.add(new Candidate(toDomElementInfo(element), element));
        }
        return out;
    }

    /**
     * Builds {@link DomElementInfo} for a single {@link WebElement}.
     *
     * @param element Selenium element
     * @return DOM snapshot for scoring/resolution
     */
    public DomElementInfo toDomElementInfo(WebElement element) {
        String tagName = safe(element.getTagName());
        String type = attr(element, "type");
        String id = attr(element, "id");
        String name = attr(element, "name");
        String cssClasses = attr(element, "class");

        TestAttribute testAttribute = resolveTestAttribute(element);
        String testAttributeName = testAttribute.name;
        String testAttributeValue = testAttribute.value;

        String placeholder = attr(element, "placeholder");
        String ariaLabel = attr(element, "aria-label");
        String title = attr(element, "title");

        String labelText = resolveAssociatedLabelText(element);
        String surroundingText = resolveSurroundingText(element);
        String formIdentifier = resolveFormIdentifier(element);

        return new DomElementInfo(
                tagName,
                type,
                id,
                name,
                cssClasses,
                labelText,
                placeholder,
                ariaLabel,
                title,
                surroundingText,
                formIdentifier,
                testAttributeName,
                testAttributeValue
        );
    }

    /**
     * Collects a broad set of DOM candidates.
     * <p>
     * Selector/scorer is expected to down-rank irrelevant elements.
     *
     * @return list of elements; may be empty
     */
    private List<WebElement> collectDomCandidates() {
        try {
            return driver.findElements(By.cssSelector("*"));
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Resolves test/qa attribute name/value using {@link #testAttributeWhitelist}.
     * <p>
     * First present attribute wins.
     *
     * @param element Selenium element
     * @return resolved attribute pair or empty pair when not found
     */
    private TestAttribute resolveTestAttribute(WebElement element) {
        for (String attrName : testAttributeWhitelist) {
            if (attrName == null || attrName.isBlank()) {
                continue;
            }
            String v = attr(element, attrName);
            if (v != null && !v.isBlank()) {
                return new TestAttribute(attrName, v.trim());
            }
        }
        return new TestAttribute("", "");
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private String attr(WebElement element, String name) {
        try {
            String v = element.getAttribute(name);
            return v == null ? "" : v.trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String resolveAssociatedLabelText(WebElement element) {
        try {
            String id = attr(element, "id");
            if (!id.isBlank()) {
                List<WebElement> labels = driver.findElements(By.cssSelector("label[for='" + cssEscape(id) + "']"));
                for (WebElement label : labels) {
                    String t = normalizeText(label.getText());
                    if (!t.isBlank()) {
                        return t;
                    }
                }
            }

            WebElement parentLabel = (WebElement) js.executeScript(
                    "return arguments[0].closest('label');",
                    element
            );
            if (parentLabel != null) {
                String t = normalizeText(parentLabel.getText());
                if (!t.isBlank()) {
                    return t;
                }
            }
        } catch (RuntimeException e) {
            return "";
        }
        return "";
    }

    private String resolveSurroundingText(WebElement element) {
        try {
            String hint = (String) js.executeScript(
                    "var el = arguments[0];" +
                            "function textOf(n){ return (n && n.textContent) ? n.textContent.trim() : ''; }" +
                            "var before = '';" +
                            "var after = '';" +
                            "if (el && el.parentElement) {" +
                            "  var p = el.parentElement;" +
                            "  var kids = Array.prototype.slice.call(p.childNodes || []);" +
                            "  var idx = kids.indexOf(el);" +
                            "  if (idx > 0) before = textOf(kids[idx-1]);" +
                            "  if (idx >= 0 && idx < kids.length-1) after = textOf(kids[idx+1]);" +
                            "}" +
                            "var b = before.replace(/\\s+/g,' ').trim();" +
                            "var a = after.replace(/\\s+/g,' ').trim();" +
                            "var r = '';" +
                            "if (b) r += '" + HINT_PREFIX + "before=" + "' + b + '" + HINT_SUFFIX + " ';" +
                            "if (a) r += '" + HINT_PREFIX + "after=" + "' + a + '" + HINT_SUFFIX + " ';" +
                            "return r.trim();",
                    element
            );
            return hint == null ? "" : hint.trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String resolveFormIdentifier(WebElement element) {
        try {
            WebElement form = (WebElement) js.executeScript(
                    "return arguments[0].closest('form');",
                    element
            );
            if (form == null) {
                return "";
            }

            String id = attr(form, "id");
            if (!id.isBlank()) {
                return "id:" + id;
            }

            String name = attr(form, "name");
            if (!name.isBlank()) {
                return "name:" + name;
            }

            String action = attr(form, "action");
            if (!action.isBlank()) {
                return "action:" + action;
            }

            return "form";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String normalizeText(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    private String cssEscape(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
