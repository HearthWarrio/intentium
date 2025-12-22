package io.hearthwarrio.intentium.webdriver;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * DSL for executing a sequence of intent-driven actions.
 * <p>
 * Supports optional overrides for logging and consistency checks at chain level.
 * <p>
 * Per-chain caching:
 * <ul>
 *   <li>DOM candidates are collected once per {@link #perform()}</li>
 *   <li>each target (intent phrase / By / WebElement) is resolved once per perform(), per "need locators" flag</li>
 * </ul>
 */
public final class ActionsChain {

    private final IntentiumWebDriver intentium;

    private TargetRef currentTarget;

    private final List<Consumer<ExecutionContext>> steps = new ArrayList<>();

    private boolean loggerOverrideSpecified = false;
    private ResolvedElementLogger loggerOverrideValue = null;

    /**
     * Optional per-chain override for {@link IntentiumWebDriver#withAllowHashedLastResort(boolean)}.
     * When set, the chain applies the override for {@link #perform()} execution and restores
     * the original driver value afterwards.
     */
    private Boolean allowHashedLastResortOverride;

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
     * Selects the current target by intent phrase for subsequent actions.
     */
    public ActionsChain into(String intentPhrase) {
        this.currentTarget = TargetRef.intent(intentPhrase);
        return this;
    }

    /**
     * Selects the current target by Selenium {@link By} locator for subsequent actions.
     */
    public ActionsChain into(By by) {
        this.currentTarget = TargetRef.by(by);
        return this;
    }

    /**
     * Selects the current target by an existing {@link WebElement} reference for subsequent actions.
     */
    public ActionsChain into(WebElement element) {
        this.currentTarget = TargetRef.element(element);
        return this;
    }

    /**
     * Alias for {@link #into(String)}.
     */
    public ActionsChain at(String intentPhrase) {
        return into(intentPhrase);
    }

    /**
     * Alias for {@link #into(By)}.
     */
    public ActionsChain at(By by) {
        return into(by);
    }

    /**
     * Alias for {@link #into(WebElement)}.
     */
    public ActionsChain at(WebElement element) {
        return into(element);
    }

    /**
     * Adds a sendKeys step for the current target.
     */
    public ActionsChain send(CharSequence... keys) {
        final TargetRef target = requireCurrentTarget();
        steps.add(ctx -> ctx.resolve(target, false).element.sendKeys(keys));
        return this;
    }

    /**
     * Adds a click step for the current target.
     */
    public ActionsChain click() {
        final TargetRef target = requireCurrentTarget();
        steps.add(ctx -> ctx.resolve(target, false).element.click());
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
     * Overrides hashed-class last-resort fallback policy for this chain execution.
     * <p>
     * The override is applied only for the duration of {@link #perform()} and then reverted.
     *
     * @param allowHashedLastResort whether hashed-class fallback is allowed as a last resort
     * @return this chain instance for fluent chaining
     */
    public ActionsChain withAllowHashedLastResort(boolean allowHashedLastResort) {
        this.allowHashedLastResortOverride = allowHashedLastResort;
        return this;
    }

    /**
     * Executes all accumulated steps.
     */
    public void perform() {
        ResolvedElementLogger originalLogger = intentium.getResolvedElementLogger();
        boolean originalConsistency = intentium.isConsistencyCheckEnabled();
        boolean originalAllowHashedLastResort = intentium.isAllowHashedLastResortEnabled();

        ExecutionContext ctx = ExecutionContext.create(intentium);

        try {
            if (loggerOverrideSpecified) {
                intentium.setResolvedElementLogger(loggerOverrideValue);
            }
            if (consistencyOverride != null) {
                intentium.withConsistencyCheck(consistencyOverride);
            }
            if (allowHashedLastResortOverride != null) {
                intentium.withAllowHashedLastResort(allowHashedLastResortOverride);
            }

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
            if (allowHashedLastResortOverride != null) {
                intentium.withAllowHashedLastResort(originalAllowHashedLastResort);
            }
        }
    }


    /**
     * Convenience: add click for current target and execute chain immediately.
     *
     * <pre>
     * actionsChain()
     *   .into("login field").send("user")
     *   .into("password field").send("secret")
     *   .at("login button").performClick();
     * </pre>
     */
    public void performClick() {
        click();
        perform();
    }

    private TargetRef requireCurrentTarget() {
        if (currentTarget == null) {
            throw new IllegalStateException("No current target selected. Call into(...) or at(...) first.");
        }
        return currentTarget;
    }

    /**
     * Target abstraction for chain steps.
     */
    private interface TargetRef {

        IntentiumWebDriver.ResolvedElement resolve(ExecutionContext ctx, boolean forceLocators);

        String cacheKey();

        static TargetRef intent(String phrase) {
            return new IntentTarget(phrase);
        }

        static TargetRef by(By by) {
            return new ByTarget(by);
        }

        static TargetRef element(WebElement element) {
            return new ElementTarget(element);
        }
    }

    private static final class IntentTarget implements TargetRef {
        private final String phrase;

        private IntentTarget(String phrase) {
            this.phrase = Objects.requireNonNull(phrase, "intentPhrase must not be null");
        }

        @Override
        public IntentiumWebDriver.ResolvedElement resolve(ExecutionContext ctx, boolean forceLocators) {
            return ctx.resolveIntent(phrase, forceLocators);
        }

        @Override
        public String cacheKey() {
            return "intent:" + phrase;
        }
    }

    private static final class ByTarget implements TargetRef {
        private final By by;

        private ByTarget(By by) {
            this.by = Objects.requireNonNull(by, "by must not be null");
        }

        @Override
        public IntentiumWebDriver.ResolvedElement resolve(ExecutionContext ctx, boolean forceLocators) {
            return ctx.resolveBy(by, forceLocators);
        }

        @Override
        public String cacheKey() {
            return "by:" + by;
        }
    }

    private static final class ElementTarget implements TargetRef {
        private final WebElement element;

        private ElementTarget(WebElement element) {
            this.element = Objects.requireNonNull(element, "element must not be null");
        }

        @Override
        public IntentiumWebDriver.ResolvedElement resolve(ExecutionContext ctx, boolean forceLocators) {
            return ctx.resolveWebElement(element, forceLocators);
        }

        @Override
        public String cacheKey() {
            return "element@" + System.identityHashCode(element);
        }
    }

    /**
     * Per-execution context holding a DOM snapshot and per-target resolved cache.
     */
    private static final class ExecutionContext {

        private final IntentiumWebDriver intentium;

        private String urlSnapshot;

        private IntentiumWebDriver.CandidatesSnapshot candidatesSnapshot;

        private final Map<String, IntentiumWebDriver.ResolvedElement> resolvedCache = new HashMap<>();

        private ExecutionContext(IntentiumWebDriver intentium) {
            this.intentium = intentium;
            refreshSnapshot();
        }

        static ExecutionContext create(IntentiumWebDriver intentium) {
            return new ExecutionContext(intentium);
        }

        IntentiumWebDriver.ResolvedElement resolve(TargetRef target, boolean forceLocators) {
            Objects.requireNonNull(target, "target must not be null");
            ensureSnapshotIsValid();

            String cacheKey = target.cacheKey() + "|forceLocators=" + forceLocators;
            IntentiumWebDriver.ResolvedElement cached = resolvedCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            IntentiumWebDriver.ResolvedElement resolved = target.resolve(this, forceLocators);
            resolvedCache.put(cacheKey, resolved);
            return resolved;
        }

        IntentiumWebDriver.ResolvedElement resolveIntent(String intentPhrase, boolean forceLocators) {
            ensureSnapshotIsValid();
            return intentium.resolveIntent(intentPhrase, candidatesSnapshot, forceLocators);
        }

        IntentiumWebDriver.ResolvedElement resolveBy(By by, boolean forceLocators) {
            ensureSnapshotIsValid();
            return intentium.resolveBy(by, forceLocators);
        }

        IntentiumWebDriver.ResolvedElement resolveWebElement(WebElement element, boolean forceLocators) {
            ensureSnapshotIsValid();
            return intentium.resolveWebElement(element, forceLocators);
        }

        private void ensureSnapshotIsValid() {
            String currentUrl = intentium.currentUrl();
            if (!Objects.equals(currentUrl, urlSnapshot)) {
                refreshSnapshot();
            }
        }

        private void refreshSnapshot() {
            this.urlSnapshot = intentium.currentUrl();

            this.candidatesSnapshot = intentium.collectCandidatesSnapshot();
            if (candidatesSnapshot.domCandidates.isEmpty()) {
                throw new IllegalStateException("No DOM candidates found – cannot execute chain.");
            }
            this.resolvedCache.clear();
        }
    }
}
