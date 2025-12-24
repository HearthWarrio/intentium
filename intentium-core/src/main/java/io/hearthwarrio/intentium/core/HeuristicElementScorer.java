package io.hearthwarrio.intentium.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * {@link ElementScorer} implementation that composes a base scorer with a list of {@link ElementHeuristic} adjustments.
 * <p>
 * Final score = base score + sum(heuristic score adjustments).
 * <p>
 * The class is intentionally simple and deterministic:
 * heuristics are applied in {@link ElementHeuristic#order()} order (ascending).
 */
public final class HeuristicElementScorer implements ElementScorer {

    private final ElementScorer base;
    private final List<ElementHeuristic> heuristics;

    /**
     * Creates a new composite scorer.
     *
     * @param base base scorer (required)
     * @param heuristics heuristics list (may be empty)
     */
    public HeuristicElementScorer(ElementScorer base, List<ElementHeuristic> heuristics) {
        this.base = Objects.requireNonNull(base, "base must not be null");
        this.heuristics = normalize(heuristics);
    }

    /**
     * Returns an immutable list of heuristics used by this scorer, ordered by {@link ElementHeuristic#order()}.
     *
     * @return ordered heuristics list
     */
    public List<ElementHeuristic> heuristics() {
        return List.copyOf(heuristics);
    }

    @Override
    public double score(IntentRole role, DomElementInfo element) {
        double baseScore = base.score(role, element);
        if (heuristics.isEmpty()) {
            return baseScore;
        }

        double delta = 0.0;
        for (ElementHeuristic h : heuristics) {
            delta += h.scoreAdjustment(role, element);
        }
        return baseScore + delta;
    }

    private static List<ElementHeuristic> normalize(List<ElementHeuristic> heuristics) {
        if (heuristics == null || heuristics.isEmpty()) {
            return List.of();
        }

        List<ElementHeuristic> out = new ArrayList<>();
        for (ElementHeuristic h : heuristics) {
            if (h != null) {
                out.add(h);
            }
        }
        out.sort(Comparator.comparingInt(ElementHeuristic::order).thenComparing(ElementHeuristic::id));
        return out;
    }
}
