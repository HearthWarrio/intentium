package io.hearthwarrio.intentium.core;

import io.hearthwarrio.intentium.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultIntentResolverTest {
    private final IntentResolver resolver = new DefaultIntentResolver();

    @Test
    void resolvesLoginFieldInEnglish() {
        IntentRole role = resolver.resolveRole("login field", Language.EN);
        assertEquals(IntentRole.LOGIN_FIELD, role);
    }

    @Test
    void resolvesPasswordFieldInEnglish() {
        IntentRole role = resolver.resolveRole("password", Language.EN);
        assertEquals(IntentRole.PASSWORD_FIELD, role);
    }

    @Test
    void resolvesLoginButtonInEnglish() {
        IntentRole role = resolver.resolveRole("sign in", Language.EN);
        assertEquals(IntentRole.LOGIN_BUTTON, role);
    }

    @Test
    void resolvesLoginFieldInRussian() {
        IntentRole role = resolver.resolveRole("поле логина", Language.RU);
        assertEquals(IntentRole.LOGIN_FIELD, role);
    }

    @Test
    void resolvesPasswordFieldInRussian() {
        IntentRole role = resolver.resolveRole("пароль", Language.RU);
        assertEquals(IntentRole.PASSWORD_FIELD, role);
    }

    @Test
    void resolvesLoginButtonInRussian() {
        IntentRole role = resolver.resolveRole("войти", Language.RU);
        assertEquals(IntentRole.LOGIN_BUTTON, role);
    }

    @Test
    void throwsOnUnknownIntent() {
        IntentResolutionException ex = assertThrows(
                IntentResolutionException.class,
                () -> resolver.resolveRole("some strange thing", Language.EN)
        );
        assertTrue(ex.getMessage().contains("Unknown intent"));
    }

    @Test
    void throwsOnNullLanguage() {
        IntentResolutionException ex = assertThrows(
                IntentResolutionException.class,
                () -> resolver.resolveRole("login field", null)
        );
        assertTrue(ex.getMessage().contains("Language"));
    }

    @Test
    void throwsOnBlankIntent() {
        IntentResolutionException ex = assertThrows(
                IntentResolutionException.class,
                () -> resolver.resolveRole("   ", Language.EN)
        );
        assertTrue(ex.getMessage().contains("must not be null or blank"));
    }
}

