package io.hearthwarrio.intentium.core;

import java.util.Collections;
import java.util.List;

/**
 * Pluggable rule that can influence how {@link ElementSelector} chooses the best matching candidate.
 * <p>
 * Heuristics are intended to extend Intentium for real-world frontends (custom dropdowns, date pickers,
 * component libraries, project-specific data attributes) without rewriting core selector logic.
 * <p>
 * Heuristics participate in three optional stages:
 * <ul>
 *   <li><b>Restriction</b>: narrow candidate list when the heuristic is confident</li>
 *   <li><b>Preference</b>: provide a subset to try earlier (without excluding others)</li>
 *   <li><b>Score adjustment</b>: boost or down-rank particular candidates</li>
 * </ul>
 * <p>
 * Contract:
 * <ul>
 *   <li>Methods must never return candidates that are not present in the provided list.</li>
 *   <li>Keep score adjustments small; the base scorer should still do the heavy lifting.</li>
 *   <li>Heuristics are applied in {@link #order()} sequence (ascending).</li>
 * </ul>
 */
public interface ElementHeuristic {

    /**
     * Stable identifier used in diagnostics.
     *
     * @return heuristic identifier
     */
    default String id() {
        return getClass().getSimpleName();
    }

    /**
     * Defines the application order of heuristics.
     * Lower values run earlier.
     *
     * @return order value
     */
    default int order() {
        return 0;
    }

    /**
     * Whether this heuristic is applicable to the given role.
     *
     * @param role intent role
     * @return true if applicable
     */
    default boolean supports(IntentRole role) {
        return true;
    }

    /**
     * Restricts candidates to a smaller subset when the heuristic is confident.
     * <p>
     * The selector treats this as an optional, best-effort narrowing. If restriction yields an empty set,
     * the selector falls back to the previous candidate set.
     *
     * @param role intent role
     * @param candidates current candidate list
     * @return restricted list (may be equal to candidates)
     */
    default List<DomElementInfo> restrict(IntentRole role, List<DomElementInfo> candidates) {
        return candidates;
    }

    /**
     * Returns a subset of candidates that should be tried earlier for the given role.
     * <p>
     * This method does not remove candidates from consideration. The selector will try preferred candidates first,
     * then fall back to the full list.
     *
     * @param role intent role
     * @param candidates all available candidates
     * @return preferred subset (may be empty)
     */
    default List<DomElementInfo> prefer(IntentRole role, List<DomElementInfo> candidates) {
        return Collections.emptyList();
    }

    /**
     * Returns a score adjustment to be added to the base scorer score for the given candidate.
     *
     * @param role intent role
     * @param candidate candidate element info
     * @return score adjustment (positive or negative)
     */
    default double scoreAdjustment(IntentRole role, DomElementInfo candidate) {
        return 0.0;
    }
}
