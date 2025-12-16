package io.hearthwarrio.intentium.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Default selector: chooses the best candidate by score and fails on ambiguity.
 * <p>
 * Includes lightweight role-based filtering and tiered preference for test/qa attributes.
 */
public class DefaultElementSelector implements ElementSelector {

    private final ElementScorer scorer;

    public DefaultElementSelector() {
        this(new DefaultElementScorer());
    }

    public DefaultElementSelector(ElementScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "scorer must not be null");
    }

    @Override
    public ElementMatch selectBest(IntentRole role, List<DomElementInfo> candidates) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");

        if (candidates.isEmpty()) {
            throw new ElementSelectionException("No candidates provided for role " + role);
        }

        List<DomElementInfo> preferred = filterPreferred(role, candidates);

        ElementMatch preferredMatch = trySelectWithTestTiersOrNull(role, preferred);
        if (preferredMatch != null) {
            return preferredMatch;
        }

        ElementMatch fullMatch = trySelectWithTestTiersOrNull(role, candidates);
        if (fullMatch != null) {
            return fullMatch;
        }

        throw new ElementSelectionException("No suitable match found for role " + role + " (all scores <= 0)");
    }

    /**
     * Tiered selection for better precision on real frontends.
     * <p>
     * Priority:
     * <ol>
     *   <li>Candidates with a test/qa attribute that also semantically matches the role</li>
     *   <li>Any candidates with a test/qa attribute</li>
     *   <li>Full list as fallback</li>
     * </ol>
     * <p>
     * If a tier exists but all scores are {@code <= 0}, selector will fall back to the next tier.
     */
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
            double s = scorer.score(role, c);
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

    /**
     * Lightweight role-based prefiltering to reduce noise.
     * This should not be too aggressive – scorer still decides.
     */
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
                // Prefer text-like inputs/textarea/contenteditable
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

    private static final String HINT_PREFIX = "[hint:";
    private static final String HINT_SUFFIX = "]";

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
