package io.hearthwarrio.intentium.core;

import java.util.List;

/**
 * Selects the best matching DOM element for a given role
 * from a list of candidates.
 */
public interface ElementSelector {

    /**
     * Select the best matching element for the given role.
     *
     * @param role       target semantic role
     * @param candidates list of candidate elements (must not be null)
     * @return match containing the chosen element and its score
     * @throws ElementSelectionException if nothing matches
     *                                   or the result is ambiguous
     */
    ElementMatch selectBest(IntentRole role, List<DomElementInfo> candidates);
}
