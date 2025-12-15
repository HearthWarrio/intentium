package io.hearthwarrio.intentium.core;

import java.util.Locale;

/**
 * Naive semantic scoring for login forms.
 *
 * v0.2 (P.5):
 * – supports broader candidate tags: textarea, select, a, role widgets, contenteditable
 * – reduces noise from hidden inputs
 */
public class DefaultElementScorer implements ElementScorer {

    private static final String HINT_ROLE_BUTTON = "[intentium:role=button]";
    private static final String HINT_ROLE_TEXTBOX = "[intentium:role=textbox]";
    private static final String HINT_ROLE_COMBOBOX = "[intentium:role=combobox]";
    private static final String HINT_CONTENTEDITABLE = "[intentium:contenteditable]";

    @Override
    public double score(IntentRole role, DomElementInfo element) {
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

        String tag = lower(e.getTagName());
        String type = lower(e.getType());

        if ("input".equals(tag) && "hidden".equals(type)) {
            return -100.0;
        }

        if ("input".equals(tag)) {
            score += 1.0;
            if (type.isEmpty() || "text".equals(type)) {
                score += 1.0;
            } else if ("email".equals(type)) {
                score += 2.0;
            }
        } else if ("textarea".equals(tag)) {
            score += 0.5;
        } else if ("select".equals(tag)) {
            score += 0.1;
        } else if ("div".equals(tag) || "span".equals(tag)) {
            score += 0.1;
        }

        String surrounding = safe(e.getSurroundingText());
        if (surrounding.contains(HINT_ROLE_TEXTBOX) || surrounding.contains(HINT_CONTENTEDITABLE)) {
            score += 1.0;
        }
        if (surrounding.contains(HINT_ROLE_COMBOBOX)) {
            score += 0.5;
        }

        String combinedText = join(
                e.getLabelText(),
                e.getPlaceholder(),
                e.getAriaLabel(),
                e.getTitle(),
                e.getSurroundingText(),
                e.getName(),
                e.getId()
        );

        if (containsAny(combinedText,
                "login", "user", "username", "user name",
                "email", "e-mail",
                "логин", "имя пользователя", "юзернейм", "почта", "email")) {
            score += 3.0;
        }

        return score;
    }

    private double scorePasswordField(DomElementInfo e) {
        double score = 0.0;

        String tag = lower(e.getTagName());
        String type = lower(e.getType());

        if ("input".equals(tag) && "hidden".equals(type)) {
            return -100.0;
        }

        if ("input".equals(tag) && "password".equals(type)) {
            score += 4.0;
        } else if ("input".equals(tag) || "textarea".equals(tag)) {
            score += 0.5;
        } else if ("div".equals(tag) || "span".equals(tag)) {
            score += 0.1;
        }

        String surrounding = safe(e.getSurroundingText());
        if (surrounding.contains(HINT_ROLE_TEXTBOX) || surrounding.contains(HINT_CONTENTEDITABLE)) {
            score += 0.75;
        }

        String combinedText = join(
                e.getLabelText(),
                e.getPlaceholder(),
                e.getAriaLabel(),
                e.getTitle(),
                e.getSurroundingText(),
                e.getName(),
                e.getId()
        );

        if (containsAny(combinedText, "password", "pwd", "пароль", "пасс")) {
            score += 2.0;
        }

        return score;
    }

    private double scoreLoginButton(DomElementInfo e) {
        double score = 0.0;

        String tag = lower(e.getTagName());
        String type = lower(e.getType());

        if ("input".equals(tag) && "hidden".equals(type)) {
            return -100.0;
        }

        if ("button".equals(tag)) {
            score += 1.0;
        }
        if ("input".equals(tag) && ("submit".equals(type) || "button".equals(type))) {
            score += 1.0;
        }
        if ("a".equals(tag)) {
            score += 0.5;
        }
        if ("div".equals(tag) || "span".equals(tag)) {
            score += 0.25;
        }

        String surrounding = safe(e.getSurroundingText());
        if (surrounding.contains(HINT_ROLE_BUTTON)) {
            score += 1.0;
        }

        String combinedText = join(
                e.getLabelText(),
                e.getAriaLabel(),
                e.getTitle(),
                e.getSurroundingText(),
                e.getName(),
                e.getId(),
                e.getPlaceholder()
        );

        if (containsAny(combinedText, "login", "log in", "sign in", "войти", "вход")) {
            score += 3.0;
        }

        return score;
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
