package io.hearthwarrio.intentium.core;

/**
 * Computes how well a given DOM element matches a semantic role.
 * The higher the score, the better the match.
 */
public interface ElementScorer {
    /**
     * Score how well the given element matches the given role.
     *
     * @param role    target semantic role (LOGIN_FIELD, etc.)
     * @param element DOM element snapshot
     * @return non-negative score; 0 means "no match"
     */
    double score(IntentRole role, DomElementInfo element);
}
