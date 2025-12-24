package io.hearthwarrio.intentium.core.heuristics;

import io.hearthwarrio.intentium.core.DomElementInfo;
import io.hearthwarrio.intentium.core.ElementHeuristic;
import io.hearthwarrio.intentium.core.IntentRole;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Generic heuristic that matches candidate element attributes by substring.
 * <p>
 * Typical use cases:
 * <ul>
 *   <li>project-specific test id patterns</li>
 *   <li>component-library hooks embedded into classes/id/name</li>
 *   <li>UI conventions such as "signin" or "auth" in surrounding blocks</li>
 * </ul>
 */
public final class AttributeContainsHeuristic implements ElementHeuristic {

    /**
     * Supported attributes that can be matched.
     */
    public enum Attribute {
        TAG_NAME,
        TYPE,
        ID,
        NAME,
        CSS_CLASSES,
        TEST_ATTRIBUTE_NAME,
        TEST_ATTRIBUTE_VALUE,
        LABEL_TEXT,
        PLACEHOLDER,
        ARIA_LABEL,
        TITLE,
        SURROUNDING_TEXT,
        FORM_IDENTIFIER
    }

    private final String id;
    private final int order;
    private final IntentRole role;
    private final Set<Attribute> attributes;
    private final List<String> needlesLower;
    private final boolean restrictToMatches;
    private final boolean preferMatches;
    private final double scoreBoost;

    /**
     * Creates a heuristic.
     *
     * @param id               identifier (used in diagnostics)
     * @param order            order value (lower runs earlier)
     * @param role             role this heuristic applies to
     * @param attributes       attributes to search in
     * @param needles          substrings to search for (case-insensitive)
     * @param restrictToMatches if true, {@link #restrict(IntentRole, List)} will narrow candidates to matches
     * @param preferMatches     if true, {@link #prefer(IntentRole, List)} will return matches as preferred
     * @param scoreBoost        score adjustment applied to matching candidates
     */
    public AttributeContainsHeuristic(
            String id,
            int order,
            IntentRole role,
            Set<Attribute> attributes,
            List<String> needles,
            boolean restrictToMatches,
            boolean preferMatches,
            double scoreBoost
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.order = order;
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.attributes = attributes == null || attributes.isEmpty()
                ? EnumSet.of(Attribute.TEST_ATTRIBUTE_VALUE)
                : EnumSet.copyOf(attributes);
        this.needlesLower = normalizeNeedles(needles);
        this.restrictToMatches = restrictToMatches;
        this.preferMatches = preferMatches;
        this.scoreBoost = scoreBoost;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean supports(IntentRole r) {
        return role == r;
    }

    @Override
    public List<DomElementInfo> restrict(IntentRole r, List<DomElementInfo> candidates) {
        if (!restrictToMatches) {
            return candidates;
        }
        return matchesOnly(candidates);
    }

    @Override
    public List<DomElementInfo> prefer(IntentRole r, List<DomElementInfo> candidates) {
        if (!preferMatches) {
            return List.of();
        }
        return matchesOnly(candidates);
    }

    @Override
    public double scoreAdjustment(IntentRole r, DomElementInfo candidate) {
        if (scoreBoost == 0.0) {
            return 0.0;
        }
        return matches(candidate) ? scoreBoost : 0.0;
    }

    private List<DomElementInfo> matchesOnly(List<DomElementInfo> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<DomElementInfo> out = new ArrayList<>();
        for (DomElementInfo c : candidates) {
            if (matches(c)) {
                out.add(c);
            }
        }
        return out;
    }

    private boolean matches(DomElementInfo c) {
        if (c == null || needlesLower.isEmpty()) {
            return false;
        }

        for (Attribute a : attributes) {
            String value = read(a, c);
            if (value.isEmpty()) {
                continue;
            }
            for (String needle : needlesLower) {
                if (value.contains(needle)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String read(Attribute a, DomElementInfo c) {
        return switch (a) {
            case TAG_NAME -> lower(c.getTagName());
            case TYPE -> lower(c.getType());
            case ID -> lower(c.getId());
            case NAME -> lower(c.getName());
            case CSS_CLASSES -> lower(c.getCssClasses());
            case TEST_ATTRIBUTE_NAME -> lower(c.getTestAttributeName());
            case TEST_ATTRIBUTE_VALUE -> lower(c.getTestAttributeValue());
            case LABEL_TEXT -> lower(c.getLabelText());
            case PLACEHOLDER -> lower(c.getPlaceholder());
            case ARIA_LABEL -> lower(c.getAriaLabel());
            case TITLE -> lower(c.getTitle());
            case SURROUNDING_TEXT -> lower(c.getSurroundingText());
            case FORM_IDENTIFIER -> lower(c.getFormIdentifier());
        };
    }

    private String lower(String v) {
        if (v == null) {
            return "";
        }
        return v.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeNeedles(List<String> needles) {
        if (needles == null || needles.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String n : needles) {
            if (n == null) {
                continue;
            }
            String t = n.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return List.copyOf(out);
    }
}
