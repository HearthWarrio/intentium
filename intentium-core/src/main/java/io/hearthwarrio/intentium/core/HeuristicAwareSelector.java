package io.hearthwarrio.intentium.core;

import java.util.List;

/**
 * Marker interface for {@link ElementSelector} implementations that support runtime-configurable heuristics.
 */
public interface HeuristicAwareSelector {

    /**
     * Replaces configured heuristics.
     *
     * @param heuristics heuristics list (may be null/empty)
     * @return this selector instance for fluent chaining
     */
    HeuristicAwareSelector withHeuristics(List<? extends ElementHeuristic> heuristics);

    /**
     * Returns currently configured heuristics in the effective application order.
     *
     * @return heuristics list (never null)
     */
    List<ElementHeuristic> getHeuristics();
}
