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
 * <p>
 * v0.1: collects inputs and buttons on the current page.
 */
public class WebDriverDomMapper {

    private final WebDriver driver;

    public WebDriverDomMapper(WebDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    /**
     * Candidate pair: a {@link DomElementInfo} snapshot instance and the underlying {@link WebElement}.
     * <p>
     * Important: {@link DomElementInfo} has {@code equals/hashCode}. Multiple real DOM elements can produce
     * identical snapshots. We therefore keep identity-stable {@link DomElementInfo} instances per element.
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
     * <p>
     * Kept for backward compatibility, but it uses identity semantics to avoid
     * accidental de-duplication when two different elements have equal {@link DomElementInfo} snapshots.
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
     * <p>
     * Order is the same as returned by {@link WebDriver#findElements(By)}.
     */
    List<Candidate> collectCandidateList() {
        List<Candidate> result = new ArrayList<>();

        List<WebElement> elements = driver.findElements(By.cssSelector("input, button"));

        for (WebElement element : elements) {
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
                formIdentifier
        );
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
                // ignore
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
            // ignore
        }

        return "";
    }

    private String resolveSurroundingText(WebElement element) {
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

    /**
     * Very naive CSS escaping for id; v0.1 assumes no exotic cases.
     */
    private String cssEscape(String value) {
        return value.replace("'", "\\'");
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
