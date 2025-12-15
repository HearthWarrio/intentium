package io.hearthwarrio.intentium.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Default selector that uses an ElementScorer to pick the best candidate.
 *
 * Strategy v0.2 (P.5):
 * – lightweight role-based filtering (preferred candidates)
 * – fallback to full list if preferred yields no suitable match
 * – ambiguous best score is still a hard failure
 */
public class DefaultElementSelector implements ElementSelector {

    private static final String HINT_ROLE_BUTTON = "[intentium:role=button]";
    private static final String HINT_ROLE_TEXTBOX = "[intentium:role=textbox]";
    private static final String HINT_ROLE_COMBOBOX = "[intentium:role=combobox]";
    private static final String HINT_CONTENTEDITABLE = "[intentium:contenteditable]";

    private final ElementScorer scorer;

    public DefaultElementSelector(ElementScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "scorer must not be null");
    }

    public DefaultElementSelector() {
        this(new DefaultElementScorer());
    }

    @Override
    public ElementMatch selectBest(IntentRole role, List<DomElementInfo> candidates) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");

        if (candidates.isEmpty()) {
            throw new ElementSelectionException("No candidates provided for role " + role);
        }

        List<DomElementInfo> preferred = filterPreferred(role, candidates);
        if (!preferred.isEmpty()) {
            ElementMatch preferredMatch = trySelectBestOrNull(role, preferred);
            if (preferredMatch != null) {
                return preferredMatch;
            }
        }

        ElementMatch fullMatch = trySelectBestOrNull(role, candidates);
        if (fullMatch != null) {
            return fullMatch;
        }

        throw new ElementSelectionException("No suitable match found for role " + role + " (all scores <= 0)");
    }

    private ElementMatch trySelectBestOrNull(IntentRole role, List<DomElementInfo> candidates) {
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
            return null;
        }

        if (secondBest != null && Double.compare(bestScore, secondBestScore) == 0) {
            throw new ElementSelectionException(
                    "Ambiguous match for role " + role + ": at least two candidates share the best score " + bestScore
            );
        }

        return new ElementMatch(role, best, bestScore);
    }

    /**
     * Lightweight role-based filtering to reduce noise when candidate list is broad.
     * Must never break the system: if it filters too aggressively, fallback will use full list.
     */
    private List<DomElementInfo> filterPreferred(IntentRole role, List<DomElementInfo> candidates) {
        List<DomElementInfo> out = new ArrayList<>();

        for (DomElementInfo c : candidates) {
            if (c == null) {
                continue;
            }

            String tag = lower(c.getTagName());
            String type = lower(c.getType());

            if ("input".equals(tag) && "hidden".equals(type)) {
                continue;
            }

            String s = safe(c.getSurroundingText());

            switch (role) {
                case PASSWORD_FIELD:
                    // Strongest hint for password – input[type=password]
                    if ("input".equals(tag) && "password".equals(type)) {
                        out.add(c);
                        break;
                    }
                    // Custom textbox that "smells" like password
                    if ((s.contains(HINT_ROLE_TEXTBOX) || s.contains(HINT_CONTENTEDITABLE)) && looksLikePassword(c)) {
                        out.add(c);
                    }
                    break;

                case LOGIN_FIELD:
                    if ("input".equals(tag)) {
                        if (type.isEmpty()
                                || "text".equals(type)
                                || "email".equals(type)
                                || "search".equals(type)
                                || "tel".equals(type)
                                || "url".equals(type)) {
                            out.add(c);
                            break;
                        }
                    }
                    if ("textarea".equals(tag)) {
                        out.add(c);
                        break;
                    }
                    if (s.contains(HINT_ROLE_TEXTBOX) || s.contains(HINT_ROLE_COMBOBOX) || s.contains(HINT_CONTENTEDITABLE)) {
                        out.add(c);
                    }
                    break;

                case LOGIN_BUTTON:
                    if ("button".equals(tag)) {
                        out.add(c);
                        break;
                    }
                    if ("input".equals(tag) && ("submit".equals(type) || "button".equals(type))) {
                        out.add(c);
                        break;
                    }
                    if ("a".equals(tag)) {
                        out.add(c);
                        break;
                    }
                    if (s.contains(HINT_ROLE_BUTTON)) {
                        out.add(c);
                    }
                    break;

                default:
                    break;
            }
        }

        return out;
    }

    private boolean looksLikePassword(DomElementInfo e) {
        String combined = join(
                e.getLabelText(),
                e.getPlaceholder(),
                e.getAriaLabel(),
                e.getTitle(),
                e.getSurroundingText(),
                e.getName(),
                e.getId()
        );
        return containsAny(combined, "password", "pwd", "пароль", "пасс");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String haystack, String... needles) {
        String normalized = lower(haystack);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (!needle.isEmpty() && normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
