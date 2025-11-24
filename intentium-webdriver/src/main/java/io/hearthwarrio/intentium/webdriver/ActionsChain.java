package io.hearthwarrio.intentium.webdriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fluent chain API:
 *
 * actionsChain()
 *   .into("login field").send("user")
 *   .into("password field").send("secret")
 *   .at("login button").performClick();
 */
public final class ActionsChain {

    private final IntentiumWebDriver intentium;
    private final List<Consumer<IntentiumWebDriver>> steps = new ArrayList<>();

    private String currentIntent;

    ActionsChain(IntentiumWebDriver intentium) {
        this.intentium = Objects.requireNonNull(intentium, "intentium must not be null");
    }

    /**
     * Set current intent for subsequent actions on a field.
     */
    public ActionsChain into(String intentPhrase) {
        this.currentIntent = Objects.requireNonNull(intentPhrase, "intentPhrase must not be null");
        return this;
    }

    /**
     * Alias for into(...) – for better readability before click.
     */
    public ActionsChain at(String intentPhrase) {
        return into(intentPhrase);
    }

    private String requireCurrentIntent() {
        if (currentIntent == null || currentIntent.isBlank()) {
            throw new IllegalStateException(
                    "No current intent set. Call into(...) or at(...) before send()/click()/performClick()."
            );
        }
        return currentIntent;
    }

    /**
     * Queue sending keys to the current intent.
     */
    public ActionsChain send(CharSequence... keys) {
        final String intent = requireCurrentIntent();
        steps.add(iw -> iw.sendKeys(intent, keys));
        return this;
    }

    /**
     * Queue click on the current intent.
     * (Use together with perform() if нужно накапливать действия.)
     */
    public ActionsChain click() {
        final String intent = requireCurrentIntent();
        steps.add(iw -> iw.click(intent));
        return this;
    }

    /**
     * Execute all queued actions in order.
     */
    public void perform() {
        for (Consumer<IntentiumWebDriver> step : steps) {
            step.accept(intentium);
        }
    }

    /**
     * Convenience: add click for current intent
     * and execute chain immediately.
     *
     * actionsChain()
     *   .into("login field").send("user")
     *   .into("password field").send("secret")
     *   .at("login button").performClick();
     */
    public void performClick() {
        click();
        perform();
    }
}
