package io.hearthwarrio.intentium.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultElementSelectorTest {
    private final ElementScorer scorer = new DefaultElementScorer();
    private final ElementSelector selector = new DefaultElementSelector(scorer);

    @Test
    void selectsLoginFieldOverOtherInputs() {
        DomElementInfo login = new DomElementInfo(
                "input",
                "text",
                "login",
                "login",
                "",
                "Login",
                "",
                "",
                "",
                "",
                "loginForm"
        );

        DomElementInfo search = new DomElementInfo(
                "input",
                "text",
                "search",
                "search",
                "",
                "Search",
                "Search...",
                "",
                "",
                "",
                "loginForm"
        );

        DomElementInfo password = new DomElementInfo(
                "input",
                "password",
                "password",
                "password",
                "",
                "Password",
                "",
                "",
                "",
                "",
                "loginForm"
        );

        List<DomElementInfo> candidates = Arrays.asList(login, search, password);

        ElementMatch match = selector.selectBest(IntentRole.LOGIN_FIELD, candidates);

        assertSame(login, match.getElement());
        assertEquals(IntentRole.LOGIN_FIELD, match.getRole());
        assertTrue(match.getScore() > 0.0);
    }

    @Test
    void selectsPasswordFieldByTypeAndLabel() {
        DomElementInfo login = new DomElementInfo(
                "input",
                "text",
                "login",
                "login",
                "",
                "Login",
                "",
                "",
                "",
                "",
                "loginForm"
        );

        DomElementInfo password = new DomElementInfo(
                "input",
                "password",
                "password",
                "password",
                "",
                "Password",
                "",
                "",
                "",
                "",
                "loginForm"
        );

        List<DomElementInfo> candidates = Arrays.asList(login, password);

        ElementMatch match = selector.selectBest(IntentRole.PASSWORD_FIELD, candidates);

        assertSame(password, match.getElement());
        assertEquals(IntentRole.PASSWORD_FIELD, match.getRole());
        assertTrue(match.getScore() > 0.0);
    }

    @Test
    void selectsLoginButton() {
        DomElementInfo loginButton = new DomElementInfo(
                "button",
                "submit",
                "loginBtn",
                "loginBtn",
                "",
                "",
                "",
                "",
                "",
                "Login",
                "loginForm"
        );

        DomElementInfo cancelButton = new DomElementInfo(
                "button",
                "button",
                "cancelBtn",
                "cancelBtn",
                "",
                "",
                "",
                "",
                "",
                "Cancel",
                "loginForm"
        );

        List<DomElementInfo> candidates = Arrays.asList(loginButton, cancelButton);

        ElementMatch match = selector.selectBest(IntentRole.LOGIN_BUTTON, candidates);

        assertSame(loginButton, match.getElement());
        assertEquals(IntentRole.LOGIN_BUTTON, match.getRole());
        assertTrue(match.getScore() > 0.0);
    }

    @Test
    void throwsOnAmbiguousBestScore() {
        DomElementInfo login1 = new DomElementInfo(
                "input",
                "text",
                "login1",
                "login",
                "",
                "Login",
                "",
                "",
                "",
                "",
                "loginForm"
        );

        DomElementInfo login2 = new DomElementInfo(
                "input",
                "text",
                "login2",
                "login",
                "",
                "Login",
                "",
                "",
                "",
                "",
                "loginForm"
        );

        List<DomElementInfo> candidates = Arrays.asList(login1, login2);

        ElementSelectionException ex = assertThrows(
                ElementSelectionException.class,
                () -> selector.selectBest(IntentRole.LOGIN_FIELD, candidates)
        );

        assertTrue(ex.getMessage().contains("Ambiguous"));
    }
}
