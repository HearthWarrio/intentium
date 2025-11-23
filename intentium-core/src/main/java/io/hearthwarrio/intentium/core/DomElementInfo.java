package io.hearthwarrio.intentium.core;

import java.util.Objects;

/**
 * Snapshot of a DOM element with the pieces of information
 * that are useful for semantic matching.
 *
 * This class does NOT depend on Selenium and can be created
 * from any DOM representation (WebDriver, JSoup, etc.).
 */
public final class DomElementInfo {

    private final String tagName;
    private final String type;
    private final String id;
    private final String name;
    private final String cssClasses;

    private final String labelText;
    private final String placeholder;
    private final String ariaLabel;
    private final String title;

    /**
     * Optional human-visible text near the element (e.g. within same container).
     * For v0.1 we treat it as "surrounding" helper text.
     */
    private final String surroundingText;

    /**
     * Identifier of the form / container this element belongs to.
     * Could be form@id, or some synthetic key from the adapter.
     * May be null if unknown.
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
    }

    private static String normalizeNull(String value) {
        return value == null ? "" : value;
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
                labelText, placeholder, ariaLabel, title,
                surroundingText, formIdentifier
        );
    }
}
