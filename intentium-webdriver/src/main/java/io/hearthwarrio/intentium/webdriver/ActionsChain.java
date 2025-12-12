package io.hearthwarrio.intentium.webdriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * DSL for executing a sequence of intent-driven actions.
 * <p>
 * Supports optional overrides for logging and consistency checks at chain level.
 */
public final class ActionsChain {

    private final IntentiumWebDriver intentium;

    private String currentIntent;
    private final List<Consumer<IntentiumWebDriver>> steps = new ArrayList<>();

    private boolean loggerOverrideSpecified = false;
    private ResolvedElementLogger loggerOverrideValue = null;

    private Boolean consistencyOverride = null;

    public ActionsChain(IntentiumWebDriver intentium) {
        this.intentium = Objects.requireNonNull(intentium, "intentium must not be null");
    }

    /**
     * Overrides logger for this chain (null disables logging).
     */
    public ActionsChain withLogger(ResolvedElementLogger logger) {
        this.loggerOverrideSpecified = true;
        this.loggerOverrideValue = logger;
        return this;
    }

    /**
     * Overrides logger for this chain with a stdout implementation.
     */
    public ActionsChain withLoggingToStdOut(LocatorLogDetail detail) {
        return withLogger(new StdOutResolvedElementLogger(detail));
    }

    /**
     * Enables stdout logging of both XPath and CSS for this chain.
     */
    public ActionsChain logLocators() {
        return withLoggingToStdOut(LocatorLogDetail.BOTH);
    }

    /**
     * Disables locator logging for this chain.
     */
    public ActionsChain disableLocatorLogging() {
        return withLogger(null);
    }

    /**
     * Overrides consistency check setting for this chain.
     */
    public ActionsChain withConsistencyCheck(boolean enabled) {
        this.consistencyOverride = enabled;
        return this;
    }

    /**
     * Enables consistency checks for this chain.
     */
    public ActionsChain checkLocators() {
        return withConsistencyCheck(true);
    }

    /**
     * Disables consistency checks for this chain.
     */
    public ActionsChain disableLocatorChecks() {
        return withConsistencyCheck(false);
    }

    /**
     * Selects the current intent target for subsequent actions.
     */
    public ActionsChain into(String intentPhrase) {
        this.currentIntent = intentPhrase;
        return this;
    }

    /**
     * Alias for {@link #into(String)}.
     */
    public ActionsChain at(String intentPhrase) {
        return into(intentPhrase);
    }

    /**
     * Adds a sendKeys step for the current intent.
     */
    public ActionsChain send(CharSequence... keys) {
        final String intent = requireCurrentIntent();
        steps.add(iw -> iw.sendKeys(intent, keys));
        return this;
    }

    /**
     * Adds a click step for the current intent.
     */
    public ActionsChain click() {
        final String intent = requireCurrentIntent();
        steps.add(iw -> iw.click(intent));
        return this;
    }

    /**
     * Executes all accumulated steps.
     */
    public void perform() {
        ResolvedElementLogger originalLogger = intentium.getResolvedElementLogger();
        boolean originalConsistency = intentium.isConsistencyCheckEnabled();

        try {
            if (loggerOverrideSpecified) {
                intentium.setResolvedElementLogger(loggerOverrideValue);
            }
            if (consistencyOverride != null) {
                intentium.withConsistencyCheck(consistencyOverride);
            }

            for (Consumer<IntentiumWebDriver> step : steps) {
                step.accept(intentium);
            }
        } finally {
            if (loggerOverrideSpecified) {
                intentium.setResolvedElementLogger(originalLogger);
            }
            if (consistencyOverride != null) {
                intentium.withConsistencyCheck(originalConsistency);
            }
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

    private String requireCurrentIntent() {
        if (currentIntent == null || currentIntent.isBlank()) {
            throw new IllegalStateException("No current intent selected. Call into(...) or at(...) first.");
        }
        return currentIntent;
    }
}
