package io.hearthwarrio.intentium.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for normalizing {@link ElementHeuristic} collections.
 */
public final class ElementHeuristics {

    private ElementHeuristics() {
    }

    /**
     * Normalizes a heuristic list:
     * <ul>
     *   <li>removes null entries</li>
     *   <li>orders by {@link ElementHeuristic#order()} then {@link ElementHeuristic#id()}</li>
     *   <li>deduplicates by {@link ElementHeuristic#id()} (first one wins)</li>
     * </ul>
     *
     * @param heuristics input list (may be null)
     * @return normalized immutable list
     */
    public static List<ElementHeuristic> normalize(List<? extends ElementHeuristic> heuristics) {
        if (heuristics == null || heuristics.isEmpty()) {
            return List.of();
        }

        List<ElementHeuristic> cleaned = new ArrayList<>();
        for (ElementHeuristic h : heuristics) {
            if (h != null) {
                cleaned.add(h);
            }
        }
        cleaned.sort(Comparator.comparingInt(ElementHeuristic::order).thenComparing(ElementHeuristic::id));

        Map<String, ElementHeuristic> byId = new LinkedHashMap<>();
        for (ElementHeuristic h : cleaned) {
            String id = h.id();
            if (!byId.containsKey(id)) {
                byId.put(id, h);
            }
        }

        return List.copyOf(byId.values());
    }
}
