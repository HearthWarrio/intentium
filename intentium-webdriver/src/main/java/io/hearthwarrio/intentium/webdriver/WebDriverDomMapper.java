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
 */
public class WebDriverDomMapper {

    private static final String HINT_PREFIX = "[hint:";
    private static final String HINT_SUFFIX = "]";

    /**
     * Common attributes used for test automation hooks.
     * If present, Intentium treats them as a strong semantic signal.
     */
    private static final String[] TEST_ATTR_CANDIDATES = new String[]{
            "data-testid",
            "data-test-id",
            "data-test",
            "data-qa",
            "data-cy",
            "data-automation-id",
            "data-automation"
    };

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

    public WebDriverDomMapper(WebDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
        this.js = (JavascriptExecutor) driver;
    }

    /**
     * Candidate pair container.
     */
    public static final class Candidate {
        private final DomElementInfo info;
        private final WebElement element;

        public Candidate(DomElementInfo info, WebElement element) {
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

    public List<Candidate> collectCandidateList() {
        List<WebElement> elements = collectDomCandidates();
        if (elements.isEmpty()) {
            return Collections.emptyList();
        }

        List<Candidate> out = new ArrayList<>(elements.size());
        for (WebElement e : elements) {
            DomElementInfo info = toDomElementInfo(e);
            out.add(new Candidate(info, e));
        }
        return out;
    }

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

    private List<WebElement> collectDomCandidates() {
        // Keep this broad: selector/scorer will down-rank junk.
        // You likely already have a tuned list in your version – keep it if it works.
        String css = String.join(",",
                "input",
                "button",
                "textarea",
                "select",
                "a",
                "[role='button']",
                "[role='textbox']",
                "[contenteditable='true']"
        );
        try {
            return driver.findElements(By.cssSelector(css));
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private TestAttribute resolveTestAttribute(WebElement element) {
        for (String attrName : TEST_ATTR_CANDIDATES) {
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
                if (!labels.isEmpty()) {
                    return normalizeText(safe(labels.get(0).getText()));
                }
            }
        } catch (Exception ignored) {
            // ignore
        }

        try {
            WebElement label = element.findElement(By.xpath("ancestor::label[1]"));
            return normalizeText(safe(label.getText()));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String resolveSurroundingText(WebElement element) {
        try {
            Object v = js.executeScript(
                    "var el=arguments[0];" +
                            "function txt(n){return (n && n.innerText) ? n.innerText : '';} " +
                            "return (txt(el.parentElement) || '') + ' ' + (txt(el.previousElementSibling) || '') + ' ' + (txt(el.nextElementSibling) || '');",
                    element
            );
            return normalizeText(safe(String.valueOf(v)));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String resolveFormIdentifier(WebElement element) {
        try {
            WebElement form = element.findElement(By.xpath("ancestor::form[1]"));
            String id = attr(form, "id");
            String name = attr(form, "name");
            if (!id.isBlank()) {
                return id;
            }
            return name;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeText(String v) {
        if (v == null) {
            return "";
        }
        return v.trim().replaceAll("\\s+", " ");
    }

    private String cssEscape(String v) {
        // minimal escape for attribute value usage in CSS selector inside single quotes
        if (v == null) {
            return "";
        }
        return v.replace("\\", "\\\\").replace("'", "\\'");
    }
}
