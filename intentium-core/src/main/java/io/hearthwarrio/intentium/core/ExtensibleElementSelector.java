package io.hearthwarrio.intentium.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Selector that preserves {@link DefaultElementSelector} behavior and adds pluggable {@link ElementHeuristic} rules.
 * <p>
 * When no heuristics are configured, this selector behaves as the default selector:
 * it chooses the best candidate by score and fails on ambiguity.
 * <p>
 * Heuristics can:
 * <ul>
 *   <li>narrow candidates via {@link ElementHeuristic#restrict(IntentRole, List)}</li>
 *   <li>provide preferred candidates via {@link ElementHeuristic#prefer(IntentRole, List)}</li>
 *   <li>adjust score via {@link ElementHeuristic#scoreAdjustment(IntentRole, DomElementInfo)}</li>
 * </ul>
 */
public final class ExtensibleElementSelector implements ElementSelector, HeuristicAwareSelector {

    private static final String HINT_PREFIX = "[hint:";
    private static final String HINT_SUFFIX = "]";

    private final ElementScorer baseScorer;

    private volatile List<ElementHeuristic> heuristics = List.of();

    /**
     * Creates a selector with {@link DefaultElementScorer} and no heuristics.
     */
    public ExtensibleElementSelector() {
        this(new DefaultElementScorer(), List.of());
    }

    /**
     * Creates a selector with a custom base scorer and no heuristics.
     *
     * @param baseScorer base scorer
     */
    public ExtensibleElementSelector(ElementScorer baseScorer) {
        this(baseScorer, List.of());
    }

    /**
     * Creates a selector with a custom base scorer and heuristics.
     *
     * @param baseScorer base scorer
     * @param heuristics heuristics list (may be null/empty)
     */
    public ExtensibleElementSelector(ElementScorer baseScorer, List<? extends ElementHeuristic> heuristics) {
        this.baseScorer = Objects.requireNonNull(baseScorer, "baseScorer must not be null");
        this.heuristics = ElementHeuristics.normalize(heuristics);
    }

    /**
     * Returns the currently configured heuristics, normalized and ordered.
     *
     * @return ordered heuristics list
     */
    @Override
    public List<ElementHeuristic> getHeuristics() {
        return heuristics;
    }

    /**
     * Replaces the configured heuristics.
     * <p>
     * The list is normalized via {@link ElementHeuristics#normalize(List)}.
     *
     * @param heuristics heuristics list (may be null/empty)
     * @return this selector
     */
    @Override
    public ExtensibleElementSelector withHeuristics(List<? extends ElementHeuristic> heuristics) {
        this.heuristics = ElementHeuristics.normalize(heuristics);
        return this;
    }

    @Override
    public ElementMatch selectBest(IntentRole role, List<DomElementInfo> candidates) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");

        if (candidates.isEmpty()) {
            throw new ElementSelectionException("No candidates provided for role " + role);
        }

        List<DomElementInfo> restricted = applyRestrictions(role, candidates);
        List<DomElementInfo> preferred = buildPreferred(role, candidates);

        List<DomElementInfo> preferredRestricted = intersectPreserveOrder(preferred, restricted);

        ElementMatch m;

        m = trySelectWithTestTiersOrNull(role, preferredRestricted);
        if (m != null) {
            return m;
        }

        m = trySelectWithTestTiersOrNull(role, restricted);
        if (m != null) {
            return m;
        }

        m = trySelectWithTestTiersOrNull(role, preferred);
        if (m != null) {
            return m;
        }

        m = trySelectWithTestTiersOrNull(role, candidates);
        if (m != null) {
            return m;
        }

        throw new ElementSelectionException("No suitable match found for role " + role + " (all scores <= 0)");
    }

    private List<DomElementInfo> applyRestrictions(IntentRole role, List<DomElementInfo> candidates) {
        List<ElementHeuristic> hs = heuristics;
        if (hs.isEmpty()) {
            return candidates;
        }

        List<DomElementInfo> current = candidates;
        for (ElementHeuristic h : hs) {
            if (h == null || !h.supports(role)) {
                continue;
            }

            List<DomElementInfo> restricted = h.restrict(role, current);
            if (restricted == null) {
                continue;
            }

            restricted = intersectPreserveOrder(current, restricted);
            if (!restricted.isEmpty() && restricted.size() < current.size()) {
                current = restricted;
            }
        }

        return current;
    }

    private List<DomElementInfo> buildPreferred(IntentRole role, List<DomElementInfo> candidates) {
        List<DomElementInfo> out = new ArrayList<>();
        out.addAll(filterPreferred(role, candidates));

        List<ElementHeuristic> hs = heuristics;
        if (!hs.isEmpty()) {
            for (ElementHeuristic h : hs) {
                if (h == null || !h.supports(role)) {
                    continue;
                }
                List<DomElementInfo> preferred = h.prefer(role, candidates);
                if (preferred == null || preferred.isEmpty()) {
                    continue;
                }
                out.addAll(intersectPreserveOrder(candidates, preferred));
            }
        }

        return dedupePreserveOrder(out);
    }

    private List<DomElementInfo> dedupePreserveOrder(List<DomElementInfo> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<DomElementInfo> out = new ArrayList<>(in.size());
        Set<DomElementInfo> seen = new HashSet<>();
        for (DomElementInfo e : in) {
            if (e == null) {
                continue;
            }
            if (seen.add(e)) {
                out.add(e);
            }
        }
        return out;
    }

    private List<DomElementInfo> intersectPreserveOrder(List<DomElementInfo> baseOrder, List<DomElementInfo> subset) {
        if (baseOrder == null || baseOrder.isEmpty() || subset == null || subset.isEmpty()) {
            return List.of();
        }

        Set<DomElementInfo> set = new HashSet<>(subset);
        List<DomElementInfo> out = new ArrayList<>();
        for (DomElementInfo e : baseOrder) {
            if (set.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    private ElementMatch trySelectWithTestTiersOrNull(IntentRole role, List<DomElementInfo> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<DomElementInfo> tierMatch = filterTestTierMatch(role, candidates);
        if (!tierMatch.isEmpty()) {
            ElementMatch m = trySelectBestOrNull(role, tierMatch);
            if (m != null) {
                return m;
            }
        }

        List<DomElementInfo> tierAny = filterTestTierAny(candidates);
        if (!tierAny.isEmpty()) {
            ElementMatch m = trySelectBestOrNull(role, tierAny);
            if (m != null) {
                return m;
            }
        }

        return trySelectBestOrNull(role, candidates);
    }

    private List<DomElementInfo> filterTestTierAny(List<DomElementInfo> candidates) {
        List<DomElementInfo> out = new ArrayList<>();
        for (DomElementInfo c : candidates) {
            if (c == null) {
                continue;
            }
            if (!lower(c.getTestAttributeValue()).isBlank()) {
                out.add(c);
            }
        }
        return out;
    }

    private List<DomElementInfo> filterTestTierMatch(IntentRole role, List<DomElementInfo> candidates) {
        List<DomElementInfo> out = new ArrayList<>();
        for (DomElementInfo c : candidates) {
            if (c == null) {
                continue;
            }
            String testValue = lower(c.getTestAttributeValue());
            if (testValue.isBlank()) {
                continue;
            }
            if (looksLikeRoleByTestId(role, testValue)) {
                out.add(c);
            }
        }
        return out;
    }

    private boolean looksLikeRoleByTestId(IntentRole role, String testValueLower) {
        if (testValueLower == null) {
            return false;
        }

        switch (role) {
            case LOGIN_FIELD:
                return containsAny(testValueLower,
                        "login", "user", "username", "email", "e-mail", "mail", "phone",
                        "логин", "польз", "почт", "тел"
                );
            case PASSWORD_FIELD:
                return containsAny(testValueLower,
                        "password", "pass", "pwd", "secret",
                        "пароль", "пасс"
                );
            case LOGIN_BUTTON:
                return containsAny(testValueLower,
                        "login", "signin", "sign-in", "sign in", "submit", "enter",
                        "войти", "вход", "логин", "авториз"
                );
            default:
                return false;
        }
    }

    private ElementMatch trySelectBestOrNull(IntentRole role, List<DomElementInfo> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        double bestScore = Double.NEGATIVE_INFINITY;
        DomElementInfo best = null;

        double secondBestScore = Double.NEGATIVE_INFINITY;
        DomElementInfo secondBest = null;

        for (DomElementInfo c : candidates) {
            double s = score(role, c);
            if (s > bestScore) {
                secondBestScore = bestScore;
                secondBest = best;

                bestScore = s;
                best = c;
            } else if (s > secondBestScore) {
                secondBestScore = s;
                secondBest = c;
            }
        }

        if (bestScore <= 0.0 || best == null) {
            return null;
        }

        if (secondBest != null && Double.compare(bestScore, secondBestScore) == 0) {
            throw new ElementSelectionException(
                    "Ambiguous match for role " + role + ": top two candidates have equal score=" + bestScore +
                            ". Best=" + best + ", SecondBest=" + secondBest
            );
        }

        return new ElementMatch(role, best, bestScore);
    }

    private double score(IntentRole role, DomElementInfo candidate) {
        double s = baseScorer.score(role, candidate);
        List<ElementHeuristic> hs = heuristics;
        if (hs.isEmpty()) {
            return s;
        }

        double delta = 0.0;
        for (ElementHeuristic h : hs) {
            if (h == null || !h.supports(role)) {
                continue;
            }
            delta += h.scoreAdjustment(role, candidate);
        }
        return s + delta;
    }

    private List<DomElementInfo> filterPreferred(IntentRole role, List<DomElementInfo> candidates) {
        List<DomElementInfo> preferred = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return preferred;
        }

        for (DomElementInfo c : candidates) {
            if (c == null) {
                continue;
            }

            String tag = lower(c.getTagName());
            String type = lower(c.getType());

            if (role == IntentRole.PASSWORD_FIELD) {
                if ("input".equals(tag) && "password".equals(type)) {
                    preferred.add(c);
                    continue;
                }
            }

            if (role == IntentRole.LOGIN_BUTTON) {
                if ("button".equals(tag)) {
                    preferred.add(c);
                    continue;
                }
                if ("input".equals(tag) && containsAny(type, "submit", "button")) {
                    preferred.add(c);
                    continue;
                }
            }

            if (role == IntentRole.LOGIN_FIELD) {
                if ("textarea".equals(tag)) {
                    preferred.add(c);
                    continue;
                }
                if ("input".equals(tag)) {
                    if (type.isEmpty() || containsAny(type, "text", "email", "tel", "number", "search")) {
                        preferred.add(c);
                        continue;
                    }
                }

                String s = lower(join(
                        c.getId(),
                        c.getName(),
                        c.getLabelText(),
                        c.getAriaLabel(),
                        c.getPlaceholder(),
                        c.getTitle(),
                        c.getSurroundingText()
                ));

                if (s.contains(HINT_PREFIX + "role=textbox" + HINT_SUFFIX) ||
                        s.contains(HINT_PREFIX + "role=combobox" + HINT_SUFFIX) ||
                        s.contains(HINT_PREFIX + "contenteditable=true" + HINT_SUFFIX)) {
                    preferred.add(c);
                }
            }
        }

        return preferred;
    }

    private String lower(String v) {
        if (v == null) {
            return "";
        }
        return v.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String haystackLower, String... needlesLower) {
        if (haystackLower == null || haystackLower.isEmpty()) {
            return false;
        }
        for (String n : needlesLower) {
            if (n == null || n.isEmpty()) {
                continue;
            }
            if (haystackLower.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private String join(String... parts) {
        if (parts == null || parts.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(t);
        }
        return sb.toString();
    }
}
