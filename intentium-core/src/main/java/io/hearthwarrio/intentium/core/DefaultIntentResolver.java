package io.hearthwarrio.intentium.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DefaultIntentResolver implements IntentResolver {
    private final Map<Language, Map<String, IntentRole>> dictionary;

    public DefaultIntentResolver() {
        this.dictionary = buildDefaultDictionary();
    }

    @Override
    public IntentRole resolveRole(String rawIntent, Language language) {
        if (rawIntent == null || rawIntent.isBlank()) {
            throw new IntentResolutionException("Intent phrase must not be null or blank");
        }
        if (language == null) {
            throw new IntentResolutionException("Language must not be null");
        }

        String normalized = normalize(rawIntent);
        Map<String, IntentRole> langMap = dictionary.get(language);

        if (langMap == null) {
            throw new IntentResolutionException("Language not supported: " + language);
        }

        IntentRole role = langMap.get(normalized);
        if (role == null) {
            throw new IntentResolutionException(
                    "Unknown intent for language " + language + ": '" + rawIntent + "'"
            );
        }

        return role;
    }

    private static String normalize(String raw) {
        return raw
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static Map<Language, Map<String, IntentRole>> buildDefaultDictionary() {
        Map<Language, Map<String, IntentRole>> result = new HashMap<>();

        Map<String, IntentRole> en = new HashMap<>();

        en.put("login field", IntentRole.LOGIN_FIELD);
        en.put("username field", IntentRole.LOGIN_FIELD);
        en.put("username", IntentRole.LOGIN_FIELD);
        en.put("user name", IntentRole.LOGIN_FIELD);
        en.put("email", IntentRole.LOGIN_FIELD);
        en.put("email field", IntentRole.LOGIN_FIELD);

        en.put("password field", IntentRole.PASSWORD_FIELD);
        en.put("password", IntentRole.PASSWORD_FIELD);
        en.put("pass field", IntentRole.PASSWORD_FIELD);

        en.put("login button", IntentRole.LOGIN_BUTTON);
        en.put("login", IntentRole.LOGIN_BUTTON);
        en.put("log in", IntentRole.LOGIN_BUTTON);
        en.put("sign in", IntentRole.LOGIN_BUTTON);

        result.put(Language.EN, Collections.unmodifiableMap(en));

        Map<String, IntentRole> ru = new HashMap<>();

        ru.put("поле логина", IntentRole.LOGIN_FIELD);
        ru.put("логин", IntentRole.LOGIN_FIELD);
        ru.put("имя пользователя", IntentRole.LOGIN_FIELD);
        ru.put("юзернейм", IntentRole.LOGIN_FIELD);
        ru.put("почта", IntentRole.LOGIN_FIELD);
        ru.put("email", IntentRole.LOGIN_FIELD);

        ru.put("поле пароля", IntentRole.PASSWORD_FIELD);
        ru.put("пароль", IntentRole.PASSWORD_FIELD);
        ru.put("пасс", IntentRole.PASSWORD_FIELD);

        ru.put("кнопка входа", IntentRole.LOGIN_BUTTON);
        ru.put("войти", IntentRole.LOGIN_BUTTON);
        ru.put("вход", IntentRole.LOGIN_BUTTON);

        result.put(Language.RU, Collections.unmodifiableMap(ru));

        return Collections.unmodifiableMap(result);
    }
}
