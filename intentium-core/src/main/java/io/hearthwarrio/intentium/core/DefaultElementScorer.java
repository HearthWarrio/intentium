package io.hearthwarrio.intentium.core;

import java.util.Locale;

/**
 * Heuristic scorer for common auth intents.
 * <p>
 * Designed to work even without test/qa attributes,
 * but heavily benefits from them when present.
 */
public class DefaultElementScorer implements ElementScorer {

    @Override
    public double score(IntentRole role, DomElementInfo element) {
        if (role == null || element == null) {
            return 0.0;
        }

        switch (role) {
            case LOGIN_FIELD:
                return scoreLoginField(element);
            case PASSWORD_FIELD:
                return scorePasswordField(element);
            case LOGIN_BUTTON:
                return scoreLoginButton(element);
            default:
                return 0.0;
        }
    }

    private double scoreLoginField(DomElementInfo e) {
        double score = 0.0;

        String testValue = lower(e.getTestAttributeValue());
        if (!testValue.isEmpty()) {
            // test/qa hooks are a strong semantic signal
            score += 0.5;
            if (containsAny(testValue,
                    "login", "user", "username", "email", "e-mail", "mail",
                    "логин", "польз", "почт", "email")) {
                score += 2.0;
            }
        }

        String tag = lower(e.getTagName());
        String type = lower(e.getType());

        if ("input".equals(tag) && "hidden".equals(type)) {
            return 0.0;
        }

        if ("input".equals(tag)) {
            score += 3.0;
            if (type.isEmpty() || containsAny(type, "text", "email", "tel", "number", "search")) {
                score += 2.0;
            } else {
                score -= 1.0;
            }
        } else if ("textarea".equals(tag)) {
            score += 3.0;
        } else if (containsAny(tag, "div", "span")) {
            score += 0.1;
        }

        String combinedText = join(
                e.getLabelText(),
                e.getPlaceholder(),
                e.getAriaLabel(),
                e.getTitle(),
                e.getSurroundingText(),
                e.getName(),
                e.getId(),
                e.getTestAttributeValue(),
                e.getTestAttributeName()
        );

        if (containsAny(combinedText,
                "login", "user", "username", "email", "e-mail", "mail", "phone", "tel",
                "логин", "польз", "почт", "тел")) {
            score += 2.0;
        }

        if (containsAny(combinedText, "password", "пароль")) {
            score -= 2.0;
        }

        return score;
    }

    private double scorePasswordField(DomElementInfo e) {
        double score = 0.0;

        String testValue = lower(e.getTestAttributeValue());
        if (!testValue.isEmpty()) {
            score += 0.5;
            if (containsAny(testValue,
                    "password", "pass", "pwd", "secret",
                    "пароль", "пасс")) {
                score += 2.0;
            }
        }

        String tag = lower(e.getTagName());
        String type = lower(e.getType());

        if ("input".equals(tag) && "hidden".equals(type)) {
            return 0.0;
        }

        if ("input".equals(tag)) {
            score += 3.0;
            if ("password".equals(type)) {
                score += 4.0;
            }
        } else if (containsAny(tag, "div", "span")) {
            score += 0.1;
        }

        String combinedText = join(
                e.getLabelText(),
                e.getPlaceholder(),
                e.getAriaLabel(),
                e.getTitle(),
                e.getSurroundingText(),
                e.getName(),
                e.getId(),
                e.getTestAttributeValue(),
                e.getTestAttributeName()
        );

        if (containsAny(combinedText,
                "password", "pass", "pwd", "secret",
                "пароль", "пасс")) {
            score += 2.0;
        }

        return score;
    }

    private double scoreLoginButton(DomElementInfo e) {
        double score = 0.0;

        String testValue = lower(e.getTestAttributeValue());
        if (!testValue.isEmpty()) {
            score += 0.5;
            if (containsAny(testValue,
                    "login", "signin", "sign-in", "sign in", "submit", "enter",
                    "войти", "вход", "логин", "авториз")) {
                score += 2.0;
            }
        }

        String tag = lower(e.getTagName());
        String type = lower(e.getType());

        if ("button".equals(tag)) {
            score += 3.0;
        } else if ("input".equals(tag) && containsAny(type, "submit", "button")) {
            score += 3.0;
        } else if ("a".equals(tag)) {
            score += 1.0;
        } else if (containsAny(tag, "div", "span")) {
            score += 0.1;
        }

        String combinedText = join(
                e.getLabelText(),
                e.getAriaLabel(),
                e.getTitle(),
                e.getSurroundingText(),
                e.getId(),
                e.getName(),
                e.getTestAttributeValue(),
                e.getTestAttributeName()
        );

        if (containsAny(combinedText,
                "login", "sign in", "signin", "submit", "enter", "continue",
                "войти", "вход", "логин", "авториз", "продолж")) {
            score += 3.0;
        }

        if (containsAny(combinedText, "register", "signup", "sign up", "регист")) {
            score -= 1.5;
        }

        return score;
    }

    private String lower(String v) {
        if (v == null) {
            return "";
        }
        return v.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null) {
            return false;
        }
        String h = lower(haystack);
        for (String n : needles) {
            if (n == null || n.isEmpty()) {
                continue;
            }
            if (h.contains(lower(n))) {
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
