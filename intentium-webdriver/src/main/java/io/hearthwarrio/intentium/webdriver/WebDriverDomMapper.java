package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.DomElementInfo;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts {@link DomElementInfo} snapshots from a Selenium WebDriver page.
 *
 * v0.2 (P.5):
 * – collects a broader set of interactive elements
 * – adds role/contenteditable hints into surroundingText for better scoring
 */
public class WebDriverDomMapper {

    private static final String HINT_PREFIX = "[intentium:";
    private static final String HINT_SUFFIX = "]";

    private final WebDriver driver;

    public WebDriverDomMapper(WebDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    /**
     * Candidate pair: a {@link DomElementInfo} snapshot instance and the underlying {@link WebElement}.
     *
     * Important: DomElementInfo may have equals/hashCode and different DOM nodes can produce identical snapshots.
     * We therefore keep identity-stable DomElementInfo instances per element.
     */
    static final class Candidate {
        private final DomElementInfo info;
        private final WebElement element;

        Candidate(DomElementInfo info, WebElement element) {
            this.info = Objects.requireNonNull(info, "info must not be null");
            this.element = Objects.requireNonNull(element, "element must not be null");
        }

        DomElementInfo getInfo() {
            return info;
        }

        WebElement getElement() {
            return element;
        }
    }

    /**
     * Collect all candidate elements on the current page.
     *
     * Kept for backward compatibility, but it uses identity semantics to avoid accidental de-duplication.
     */
    public Map<DomElementInfo, WebElement> collectCandidates() {
        Map<DomElementInfo, WebElement> result = new IdentityHashMap<>();
        for (Candidate c : collectCandidateList()) {
            result.put(c.getInfo(), c.getElement());
        }
        return result;
    }

    /**
     * Collect candidates as an ordered list of pairs.
     */
    List<Candidate> collectCandidateList() {
        List<Candidate> result = new ArrayList<>();

        String selector =
                "input, button, textarea, select, a, " +
                        "[role='button'], [role='textbox'], [role='combobox'], [role='listbox'], " +
                        "[contenteditable='true'], [contenteditable='']";

        List<WebElement> elements = driver.findElements(By.cssSelector(selector));

        for (WebElement element : elements) {
            if (element == null) {
                continue;
            }

            String tag = safeAttr(element::getTagName);
            String type = attr(element, "type");

            // Skip hidden inputs early – they create a lot of noise.
            if ("input".equalsIgnoreCase(tag) && "hidden".equalsIgnoreCase(type)) {
                continue;
            }

            // Prefer visible candidates (less noise, fewer accidental matches).
            if (!safeIsDisplayed(element)) {
                continue;
            }

            DomElementInfo info = toDomElementInfo(element);
            result.add(new Candidate(info, element));
        }

        return result;
    }

    /**
     * Build a {@link DomElementInfo} snapshot from a {@link WebElement}.
     */
    public DomElementInfo toDomElementInfo(WebElement element) {
        String tagName = safeAttr(element::getTagName);
        String type = attr(element, "type");
        String id = attr(element, "id");
        String name = attr(element, "name");
        String cssClasses = attr(element, "class");

        String labelText = resolveLabelText(element, id);
        String placeholder = attr(element, "placeholder");
        String ariaLabel = attr(element, "aria-label");
        String title = attr(element, "title");

        String role = attr(element, "role");
        String contentEditable = attr(element, "contenteditable");

        String surroundingText = resolveSurroundingText(element);
        surroundingText = prependHints(surroundingText, role, contentEditable);

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
                formIdentifier
        );
    }

    private String prependHints(String base, String role, String contentEditable) {
        StringBuilder sb = new StringBuilder();

        if (role != null && !role.isBlank()) {
            sb.append(HINT_PREFIX).append("role=").append(role.trim()).append(HINT_SUFFIX);
        }

        if (contentEditable != null) {
            String v = contentEditable.trim().toLowerCase();
            if ("true".equals(v) || v.isEmpty()) {
                sb.append(HINT_PREFIX).append("contenteditable").append(HINT_SUFFIX);
            }
        }

        if (base != null && !base.isBlank()) {
            sb.append(base);
        }

        return sb.toString();
    }

    private boolean safeIsDisplayed(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    private String attr(WebElement element, String name) {
        try {
            String value = element.getAttribute(name);
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private String safeAttr(SupplierWithException<String> supplier) {
        try {
            String value = supplier.get();
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveLabelText(WebElement element, String id) {
        if (id != null && !id.isBlank()) {
            try {
                List<WebElement> labels = driver.findElements(
                        By.cssSelector("label[for='" + cssEscape(id) + "']"));
                if (!labels.isEmpty()) {
                    String text = labels.get(0).getText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            } catch (Exception ignored) {

            }
        }

        try {
            List<WebElement> wrapping = element.findElements(By.xpath("ancestor::label"));
            if (!wrapping.isEmpty()) {
                String text = wrapping.get(0).getText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        } catch (Exception ignored) {

        }

        return "";
    }

    /**
     * Prefer element.getText() (good for buttons/links/role widgets),
     * fallback to parent container text (good for inputs).
     */
    private String resolveSurroundingText(WebElement element) {
        try {
            String selfText = element.getText();
            if (selfText != null && !selfText.isBlank()) {
                return selfText;
            }
        } catch (Exception ignored) {

        }

        try {
            WebElement parent = element.findElement(By.xpath(".."));
            String text = parent.getText();
            return text == null ? "" : text;
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveFormIdentifier(WebElement element) {
        try {
            WebElement form = element.findElement(By.xpath("ancestor::form[1]"));
            String id = form.getAttribute("id");
            if (id != null && !id.isBlank()) {
                return id;
            }
            String name = form.getAttribute("name");
            if (name != null && !name.isBlank()) {
                return name;
            }
            return "form";
        } catch (NoSuchElementException e) {
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String cssEscape(String value) {
        return value.replace("'", "\\'");
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
