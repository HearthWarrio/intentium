package io.hearthwarrio.intentium.core;

import java.util.Objects;

/**
 * Lightweight DOM element snapshot used by selectors/scorers.
 * <p>
 * Intentium treats "test/qa attributes" (e.g. data-testid, data-test-id, data-qa, data-cy)
 * as a strong semantic signal when present.
 */
public final class DomElementInfo {

    private final String tagName;
    private final String type;
    private final String id;
    private final String name;
    private final String cssClasses;

    /**
     * Optional test/qa attribute (e.g. data-testid, data-test-id, data-qa, data-cy).
     * Stored as name/value pair. Empty when not present.
     */
    private final String testAttributeName;
    private final String testAttributeValue;

    private final String labelText;
    private final String placeholder;
    private final String ariaLabel;
    private final String title;
    private final String surroundingText;

    /**
     * Identifier of the form context for uniqueness checks (best-effort).
     * Usually derived from nearest ancestor form[@id] or form[@name], otherwise empty.
     */
    private final String formIdentifier;

    public DomElementInfo(
            String tagName,
            String type,
            String id,
            String name,
            String cssClasses,
            String labelText,
            String placeholder,
            String ariaLabel,
            String title,
            String surroundingText,
            String formIdentifier
    ) {
        this(
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
                "",
                ""
        );
    }

    public DomElementInfo(
            String tagName,
            String type,
            String id,
            String name,
            String cssClasses,
            String labelText,
            String placeholder,
            String ariaLabel,
            String title,
            String surroundingText,
            String formIdentifier,
            String testAttributeName,
            String testAttributeValue
    ) {
        this.tagName = normalizeNull(tagName);
        this.type = normalizeNull(type);
        this.id = normalizeNull(id);
        this.name = normalizeNull(name);
        this.cssClasses = normalizeNull(cssClasses);
        this.labelText = normalizeNull(labelText);
        this.placeholder = normalizeNull(placeholder);
        this.ariaLabel = normalizeNull(ariaLabel);
        this.title = normalizeNull(title);
        this.surroundingText = normalizeNull(surroundingText);
        this.formIdentifier = normalizeNull(formIdentifier);
        this.testAttributeName = normalizeNull(testAttributeName);
        this.testAttributeValue = normalizeNull(testAttributeValue);
    }

    private static String normalizeNull(String s) {
        return s == null ? "" : s;
    }

    public String getTagName() {
        return tagName;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCssClasses() {
        return cssClasses;
    }

    /**
     * Returns the name of a test/qa attribute (e.g. data-testid) if present, otherwise empty string.
     */
    public String getTestAttributeName() {
        return testAttributeName;
    }

    /**
     * Returns the value of a test/qa attribute (e.g. "login") if present, otherwise empty string.
     */
    public String getTestAttributeValue() {
        return testAttributeValue;
    }

    public String getLabelText() {
        return labelText;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getAriaLabel() {
        return ariaLabel;
    }

    public String getTitle() {
        return title;
    }

    public String getSurroundingText() {
        return surroundingText;
    }

    public String getFormIdentifier() {
        return formIdentifier;
    }

    @Override
    public String toString() {
        return "DomElementInfo{" +
                "tagName='" + tagName + '\'' +
                ", type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", cssClasses='" + cssClasses + '\'' +
                ", testAttributeName='" + testAttributeName + '\'' +
                ", testAttributeValue='" + testAttributeValue + '\'' +
                ", labelText='" + labelText + '\'' +
                ", placeholder='" + placeholder + '\'' +
                ", ariaLabel='" + ariaLabel + '\'' +
                ", title='" + title + '\'' +
                ", surroundingText='" + surroundingText + '\'' +
                ", formIdentifier='" + formIdentifier + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DomElementInfo)) return false;
        DomElementInfo that = (DomElementInfo) o;
        return Objects.equals(tagName, that.tagName) &&
                Objects.equals(type, that.type) &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(cssClasses, that.cssClasses) &&
                Objects.equals(testAttributeName, that.testAttributeName) &&
                Objects.equals(testAttributeValue, that.testAttributeValue) &&
                Objects.equals(labelText, that.labelText) &&
                Objects.equals(placeholder, that.placeholder) &&
                Objects.equals(ariaLabel, that.ariaLabel) &&
                Objects.equals(title, that.title) &&
                Objects.equals(surroundingText, that.surroundingText) &&
                Objects.equals(formIdentifier, that.formIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                tagName, type, id, name, cssClasses,
                testAttributeName, testAttributeValue,
                labelText, placeholder, ariaLabel, title, surroundingText, formIdentifier
        );
    }
}
