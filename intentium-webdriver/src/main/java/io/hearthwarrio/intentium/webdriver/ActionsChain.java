package io.hearthwarrio.intentium.webdriver;

import java.util.*;
import java.util.function.Consumer;

/**
 * DSL for executing a sequence of intent-driven actions.
 * <p>
 * Supports optional overrides for logging and consistency checks at chain level.
 * <p>
 * Per-chain caching:
 * - DOM candidates are collected once per perform()
 * - each intent phrase is resolved once per perform()
 * - cache is invalidated automatically if the current URL changes mid-chain
 */
public final class ActionsChain {

    private final IntentiumWebDriver intentium;

    private String currentIntent;

    private final List<Consumer<ExecutionContext>> steps = new ArrayList<>();

    private boolean loggerOverrideSpecified = false;
    private ResolvedElementLogger loggerOverrideValue = null;

    /**
     * Tri-state:
     * null – no override, keep driver setting
     * true/false – override for this chain execution
     */
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
        steps.add(ctx -> ctx.resolve(intent, false).element.sendKeys(keys));
        return this;
    }

    /**
     * Adds a click step for the current intent.
     */
    public ActionsChain click() {
        final String intent = requireCurrentIntent();
        steps.add(ctx -> ctx.resolve(intent, false).element.click());
        return this;
    }

    /**
     * Sugar alias for into(intentPhrase).send(keys).
     */
    public ActionsChain type(String intentPhrase, CharSequence... keys) {
        return into(intentPhrase).send(keys);
    }

    /**
     * Sugar: at(intentPhrase) + performClick().
     */
    public void performClickAt(String intentPhrase) {
        at(intentPhrase);
        performClick();
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

            ExecutionContext ctx = ExecutionContext.create(intentium);

            for (Consumer<ExecutionContext> step : steps) {
                step.accept(ctx);
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
     * Convenience: add click for current intent and execute chain immediately.
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

    /**
     * Per-execution context holding a DOM snapshot and a cache of resolved intents.
     */
    private static final class ExecutionContext {

        private final IntentiumWebDriver intentium;

        private String urlSnapshot;

        private IntentiumWebDriver.CandidatesSnapshot snapshot;

        private final Map<String, IntentiumWebDriver.ResolvedElement> resolvedCache = new HashMap<>();

        private ExecutionContext(IntentiumWebDriver intentium) {
            this.intentium = intentium;
            refreshSnapshot();
        }

        static ExecutionContext create(IntentiumWebDriver intentium) {
            return new ExecutionContext(intentium);
        }

        IntentiumWebDriver.ResolvedElement resolve(String intentPhrase, boolean forceLocators) {
            ensureSnapshotIsValid();

            String cacheKey = intentPhrase + "|forceLocators=" + forceLocators;
            IntentiumWebDriver.ResolvedElement cached = resolvedCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            IntentiumWebDriver.ResolvedElement resolved =
                    intentium.resolveIntent(intentPhrase, snapshot, forceLocators);

            resolvedCache.put(cacheKey, resolved);
            return resolved;
        }

        private void ensureSnapshotIsValid() {
            String currentUrl = intentium.currentUrl();
            if (!Objects.equals(currentUrl, urlSnapshot)) {
                refreshSnapshot();
            }
        }

        private void refreshSnapshot() {
            this.urlSnapshot = intentium.currentUrl();

            this.snapshot = intentium.collectCandidatesSnapshot();
            if (snapshot.domCandidates.isEmpty()) {
                throw new IllegalStateException("No DOM candidates found – cannot execute chain.");
            }
            this.resolvedCache.clear();
        }
    }
}
