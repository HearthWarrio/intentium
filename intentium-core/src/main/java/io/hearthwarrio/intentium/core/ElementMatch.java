package io.hearthwarrio.intentium.core;

/**
 * Result of selecting the best DOM element for a given role.
 */
public final class ElementMatch {

    private final IntentRole role;
    private final DomElementInfo element;
    private final double score;

    public ElementMatch(IntentRole role, DomElementInfo element, double score) {
        this.role = role;
        this.element = element;
        this.score = score;
    }

    public IntentRole getRole() {
        return role;
    }

    public DomElementInfo getElement() {
        return element;
    }

    public double getScore() {
        return score;
    }

    @Override
    public String toString() {
        return "ElementMatch{" +
                "role=" + role +
                ", score=" + score +
                ", element=" + element +
                '}';
    }
}
