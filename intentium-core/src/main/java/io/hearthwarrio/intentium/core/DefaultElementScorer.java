package io.hearthwarrio.intentium.core;

import java.util.Locale;

/**
 * Naive v0.1 implementation of semantic scoring for login forms.
 *
 * It uses:
 * - tag name and type
 * - label / placeholder / aria-label / title
 * - simple keyword matching in RU/EN
 */
public class DefaultElementScorer implements ElementScorer {

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

        if ("input".equals(tag)) {
            score += 1.0;
            if (type.isEmpty() || "text".equals(type)) {
                score += 1.0;
            } else if ("email".equals(type)) {
                score += 2.0;
            }
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

        if ("input".equals(tag) && "password".equals(type)) {
            score += 4.0;
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
                "password", "pwd",
                "пароль", "пасс")) {
            score += 2.0;
        }

        return score;
    }

    private double scoreLoginButton(DomElementInfo e) {
        double score = 0.0;

        String tag = lower(e.getTagName());
        String type = lower(e.getType());

        // типы кнопок
        if ("button".equals(tag)) {
            score += 1.0;
        }
        if ("input".equals(tag) && ("submit".equals(type) || "button".equals(type))) {
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

        if (containsAny(combinedText,
                "login", "log in", "sign in",
                "войти", "вход")) {
            score += 3.0;
        }

        return score;
    }

    /**
     * Utils
     */

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
