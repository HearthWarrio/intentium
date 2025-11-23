package io.hearthwarrio.intentium.core;

import java.util.List;
import java.util.Objects;

/**
 * Default selector that uses an ElementScorer to pick the best candidate.
 *
 * Strategy v0.1:
 * - compute score for each candidate
 * - if all scores <= 0 -> "no suitable match"
 * - if there are at least two candidates with the same best score -> "ambiguous"
 * - otherwise select the candidate with the highest score
 */
public class DefaultElementSelector implements ElementSelector {
    private final ElementScorer scorer;

    public DefaultElementSelector(ElementScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "scorer must not be null");
    }

    /**
     * Convenience constructor that uses DefaultElementScorer.
     */
    public DefaultElementSelector() {
        this(new DefaultElementScorer());
    }

    @Override
    public ElementMatch selectBest(IntentRole role, List<DomElementInfo> candidates) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");

        if (candidates.isEmpty()) {
            throw new ElementSelectionException(
                    "No candidates provided for role " + role
            );
        }

        DomElementInfo best = null;
        DomElementInfo secondBest = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double secondBestScore = Double.NEGATIVE_INFINITY;

        for (DomElementInfo candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            double score = scorer.score(role, candidate);
            if (score > bestScore) {
                secondBestScore = bestScore;
                secondBest = best;

                bestScore = score;
                best = candidate;
            } else if (score > secondBestScore) {
                secondBestScore = score;
                secondBest = candidate;
            }
        }

        if (best == null || bestScore <= 0.0) {
            throw new ElementSelectionException(
                    "No suitable match found for role " + role +
                            " (all scores <= 0)"
            );
        }

        if (secondBest != null && Double.compare(bestScore, secondBestScore) == 0) {
            throw new ElementSelectionException(
                    "Ambiguous match for role " + role +
                            ": at least two candidates share the best score " + bestScore
            );
        }

        return new ElementMatch(role, best, bestScore);
    }
}
