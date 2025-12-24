package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.*;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.*;

/**
 * High-level Intentium entry point for Selenium WebDriver.
 * <p>
 * Intentium provides two execution modes:
 * <ul>
 *   <li>Direct API (e.g. {@link #click(String)}, {@link #sendKeys(String, CharSequence...)}): each call is treated as an
 *       independent step. Resolving an intent phrase may collect a fresh DOM candidates snapshot for that call.</li>
 *   <li>{@link ActionsChain} (via {@link #actionsChain()}): one {@link ActionsChain#perform()} call is treated as one step
 *       and reuses a single DOM candidates snapshot across all intent phrase resolutions within the chain.</li>
 * </ul>
 * <p>
 * Snapshot invalidation within a chain is triggered by URL change (navigation). DOM mutations without URL change are outside
 * the snapshot reuse guarantee.
 */
public class IntentiumWebDriver {

    private final WebDriver driver;
    private final Language language;
    private final IntentResolver intentResolver;
    private final ElementSelector elementSelector;
    /**
     * Whitelist of attribute names considered "test/qa hooks" (for example, {@code data-testid}, {@code data-qa}).
     * <p>
     * The order matters: the first present attribute wins.
     * <p>
     * Mutated by {withTestAttributeWhitelist(String...)}.
     */
    private final List<String> testAttributeWhitelist = new ArrayList<>(WebDriverDomMapper.DEFAULT_TEST_ATTRIBUTE_WHITELIST);
    private final WebDriverDomMapper domMapper;

    /**
     * Mutable to support runtime overrides and DSL sugar.
     */
    private ResolvedElementLogger resolvedElementLogger;

    private boolean consistencyCheckEnabled = false;

    /**
     * Controls whether Intentium may use likely-hashed CSS class tokens as a last-resort anchor
     * when building stable derived locators.
     * <p>
     * When disabled (default), hashed class tokens are ignored even if they are unique in context.
     * This prevents “stable” locators from anchoring on generated classes produced by CSS-in-JS/CSS-modules.
     */
    private boolean allowHashedLastResort = false;

    /**
     * Snapshot of candidates collected from the current page.
     * <p>
     * Holds:
     * <ul>
     *   <li>Ordered list of {@link DomElementInfo} candidates</li>
     *   <li>Identity-based mapping DomElementInfo instance -> WebElement</li>
     * </ul>
     */
    static final class CandidatesSnapshot {
        final List<DomElementInfo> domCandidates;
        final Map<DomElementInfo, WebElement> elementsByInfo;

        CandidatesSnapshot(List<DomElementInfo> domCandidates, Map<DomElementInfo, WebElement> elementsByInfo) {
            this.domCandidates = domCandidates;
            this.elementsByInfo = elementsByInfo;
        }
    }

    /**
     * Internal resolved element data container used for caching.
     * Package-private on purpose.
     */
    static final class ResolvedElement {
        final String intentPhrase;
        final IntentRole role;
        final DomElementInfo elementInfo;
        final WebElement element;
        final String xPath;       // may be null when not needed
        final String cssSelector; // may be null when not needed

        ResolvedElement(
                String intentPhrase,
                IntentRole role,
                DomElementInfo elementInfo,
                WebElement element,
                String xPath,
                String cssSelector
        ) {
            this.intentPhrase = intentPhrase;
            this.role = role;
            this.elementInfo = elementInfo;
            this.element = element;
            this.xPath = xPath;
            this.cssSelector = cssSelector;
        }
    }

    /**
     * p.4 fix: last resolved cache for locator-related calls.
     * <p>
     * Goal: avoid double-resolve for getXPath/getCssSelector (and reduce mismatch risk on dynamic DOM).
     */
    private static final class LastLocatorsCache {
        String url;
        String intentPhrase;
        ResolvedElementLogger loggerAtResolve;
        boolean consistencyAtResolve;
        ResolvedElement resolved; // resolved WITH locators
        /**
         * Stores the hashed-class fallback configuration used at the moment locators were computed.
         * This is required to keep last-locators cache coherent when configuration changes between calls.
         */
        boolean allowHashedLastResortAtResolve;
    }

    private final LastLocatorsCache lastLocatorsCache = new LastLocatorsCache();

    public IntentiumWebDriver(WebDriver driver, Language language) {
        this(driver, language, new DefaultIntentResolver(), new ExtensibleElementSelector(), null);
    }

    public IntentiumWebDriver(WebDriver driver, Language language, ResolvedElementLogger logger) {
        this(driver, language, new DefaultIntentResolver(), new ExtensibleElementSelector(), logger);
    }

    /**
     * Creates a driver facade using default resolver and an extensible selector configured with the provided heuristics.
     * <p>
     * This constructor enables project-specific selection rules without rewriting the whole selector.
     *
     * @param driver Selenium WebDriver
     * @param language language configuration
     * @param heuristics selection heuristics (optional)
     */
    public IntentiumWebDriver(WebDriver driver, Language language, ElementHeuristic... heuristics) {
        this(driver, language, new DefaultIntentResolver(),
                new ExtensibleElementSelector(
                        new DefaultElementScorer(),
                        heuristics == null ? Collections.<ElementHeuristic>emptyList() : Arrays.asList(heuristics)),
                null
        );
    }

    /**
     * Creates a driver facade using default resolver and an extensible selector configured with the provided heuristics.
     * <p>
     * This constructor enables project-specific selection rules without rewriting the whole selector.
     *
     * @param driver Selenium WebDriver
     * @param language language configuration
     * @param logger resolved element logger (optional)
     * @param heuristics selection heuristics (optional)
     */
    public IntentiumWebDriver(WebDriver driver, Language language, ResolvedElementLogger logger, ElementHeuristic... heuristics) {
        this(driver, language, new DefaultIntentResolver(),
                new ExtensibleElementSelector(
                        new DefaultElementScorer(),
                        heuristics == null ? Collections.<ElementHeuristic>emptyList() : Arrays.asList(heuristics)),
                logger
        );
    }

    public IntentiumWebDriver(
            WebDriver driver,
            Language language,
            IntentResolver intentResolver,
            ElementSelector elementSelector
    ) {
        this(driver, language, intentResolver, elementSelector, null);
    }

    public IntentiumWebDriver(
            WebDriver driver,
            Language language,
            IntentResolver intentResolver,
            ElementSelector elementSelector,
            ResolvedElementLogger logger
    ) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
        this.language = Objects.requireNonNull(language, "language must not be null");
        this.intentResolver = Objects.requireNonNull(intentResolver, "intentResolver must not be null");
        this.elementSelector = Objects.requireNonNull(elementSelector, "elementSelector must not be null");
        this.domMapper = new WebDriverDomMapper(driver, testAttributeWhitelist);
        this.resolvedElementLogger = logger;
    }

    // ----------- configuration (low-level) -----------

    public IntentiumWebDriver withLogger(ResolvedElementLogger logger) {
        this.resolvedElementLogger = logger;
        return this;
    }

    public IntentiumWebDriver withLoggingToStdOut(LocatorLogDetail detail) {
        this.resolvedElementLogger = new StdOutResolvedElementLogger(detail);
        return this;
    }

    public IntentiumWebDriver withConsistencyCheck(boolean enabled) {
        this.consistencyCheckEnabled = enabled;
        return this;
    }

    /**
     * Enables or disables usage of likely-hashed CSS class tokens as a strict last-resort anchor when building derived
     * "stable" locators.
     * <p>
     * When disabled (default), hashed class tokens are ignored even if they are unique in context. This prevents derived
     * locators from anchoring on build-generated classes produced by CSS-in-JS and CSS Modules.
     * <p>
     * When enabled, hashed tokens may be used only after all higher-quality anchors have been exhausted and only when the
     * alternative fallback would be a bare {@code //tag} (XPath) or {@code tag} (CSS).
     * <p>
     * For chain execution, this setting can be overridden per chain via {@link ActionsChain#withAllowHashedLastResort(boolean)}.
     *
     * @param allowHashedLastResort whether hashed-class fallback is allowed as a last resort
     * @return this driver instance for fluent chaining
     */
    public IntentiumWebDriver withAllowHashedLastResort(boolean allowHashedLastResort) {
        this.allowHashedLastResort = allowHashedLastResort;
        return this;
    }

    /**
     * Returns the current whitelist of "test/qa" attributes used during DOM snapshotting.
     * <p>
     * The returned list is read-only.
     *
     * @return current whitelist (possibly empty)
     */
    public List<String> getTestAttributeWhitelist() {
        return Collections.unmodifiableList(testAttributeWhitelist);
    }

    /**
     * Configures whitelist of project-specific test/qa attributes (for example, {@code data-testid}, {@code data-qa}).
     * <p>
     * The order matters: the first present attribute wins.
     * <p>
     * Blank names are ignored, duplicates are removed (keeping the first occurrence).
     * <p>
     * Passing an empty whitelist disables test attribute detection completely.
     *
     * @param attributeNames attribute names in priority order
     * @return this driver instance for chaining
     */
    public IntentiumWebDriver withTestAttributeWhitelist(String... attributeNames) {
        Objects.requireNonNull(attributeNames, "attributeNames must not be null");
        return withTestAttributeWhitelist(Arrays.asList(attributeNames));
    }

    /**
     * Same as {@link #withTestAttributeWhitelist(String...)} but accepts a list.
     *
     * @param attributeNames attribute names in priority order
     * @return this driver instance for chaining
     */
    public IntentiumWebDriver withTestAttributeWhitelist(List<String> attributeNames) {
        Objects.requireNonNull(attributeNames, "attributeNames must not be null");
        testAttributeWhitelist.clear();
        for (String raw : attributeNames) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!testAttributeWhitelist.contains(name)) {
                testAttributeWhitelist.add(name);
            }
        }
        return this;
    }

    /**
     * Returns the current setting for hashed-class last-resort fallback.
     * <p>
     * Package-private by design to allow {@link ActionsChain} to temporarily override and restore it.
     *
     * @return {@code true} if hashed-class last-resort fallback is enabled
     */
    boolean isAllowHashedLastResortEnabled() {
        return allowHashedLastResort;
    }

    /**
     * Replaces the configured element heuristics for the current selector, if supported.
     * <p>
     * If the configured {@link ElementSelector} does not support heuristics, this method fails fast
     * to avoid a misleading no-op.
     *
     * @param heuristics heuristics list (may be null/empty)
     * @return this driver instance for fluent chaining
     * @throws IllegalStateException if the configured {@link ElementSelector} does not support heuristics
     */
    public IntentiumWebDriver withElementHeuristics(List<? extends ElementHeuristic> heuristics) {
        List<? extends ElementHeuristic> safe = heuristics == null ? Collections.emptyList() : heuristics;

        if (this.elementSelector instanceof HeuristicAwareSelector) {
            HeuristicAwareSelector heuristicAware = (HeuristicAwareSelector) this.elementSelector;
            heuristicAware.withHeuristics(safe);
            return this;
        }

        throw new IllegalStateException(
                "ElementSelector does not support heuristics: " + this.elementSelector.getClass().getName()
        );
    }


    /**
     * Convenience overload for {@link #withElementHeuristics(List)}.
     *
     * @param heuristics heuristics to configure (may be null)
     * @return this driver instance for fluent chaining
     */
    public IntentiumWebDriver withElementHeuristics(ElementHeuristic... heuristics) {
        if (heuristics == null) {
            return withElementHeuristics(Collections.emptyList());
        }
        return withElementHeuristics(Arrays.asList(heuristics));
    }

    /**
     * Clears all configured heuristics.
     *
     * @return this driver instance for fluent chaining
     */
    public IntentiumWebDriver clearElementHeuristics() {
        return withElementHeuristics(Collections.emptyList());
    }

    /**
     * Returns the currently configured heuristics for the current selector.
     * <p>
     * If the selector does not support heuristics, returns an empty list.
     *
     * @return ordered heuristics list, or an empty list if none configured
     */
    public List<ElementHeuristic> getElementHeuristics() {
        if (this.elementSelector instanceof HeuristicAwareSelector) {
            HeuristicAwareSelector heuristicAware = (HeuristicAwareSelector) this.elementSelector;
            List<ElementHeuristic> hs = heuristicAware.getHeuristics();
            return hs == null ? Collections.emptyList() : hs;
        }
        return Collections.emptyList();
    }

    // ----------- configuration (sugar, minimal set) -----------

    public IntentiumWebDriver logLocators() {
        return withLoggingToStdOut(LocatorLogDetail.BOTH);
    }

    public IntentiumWebDriver disableLocatorLogging() {
        this.resolvedElementLogger = null;
        return this;
    }

    public IntentiumWebDriver checkLocators() {
        this.consistencyCheckEnabled = true;
        return this;
    }

    public IntentiumWebDriver disableLocatorChecks() {
        this.consistencyCheckEnabled = false;
        return this;
    }

    public boolean isConsistencyCheckEnabled() {
        return consistencyCheckEnabled;
    }

    // package-private for ActionsChain overrides
    ResolvedElementLogger getResolvedElementLogger() {
        return resolvedElementLogger;
    }

    void setResolvedElementLogger(ResolvedElementLogger logger) {
        this.resolvedElementLogger = logger;
    }

    private LocatorLogDetail currentLogDetail() {
        if (resolvedElementLogger == null) {
            return LocatorLogDetail.NONE;
        }

        LocatorLogDetail d;
        try {
            d = resolvedElementLogger.detail();
        } catch (RuntimeException e) {
            d = LocatorLogDetail.BOTH;
        }

        if (d == null) {
            d = LocatorLogDetail.BOTH;
        }
        if (d == LocatorLogDetail.XPATH_AND_CSS) {
            d = LocatorLogDetail.BOTH;
        }
        return d;
    }

    String currentUrl() {
        return driver.getCurrentUrl();
    }

    CandidatesSnapshot collectCandidatesSnapshot() {
        List<WebDriverDomMapper.Candidate> pairs = domMapper.collectCandidateList();
        if (pairs.isEmpty()) {
            return new CandidatesSnapshot(Collections.emptyList(), Collections.emptyMap());
        }

        Map<DomElementInfo, WebElement> elementsByInfo = new IdentityHashMap<>();
        List<DomElementInfo> domCandidates = new ArrayList<>(pairs.size());

        for (WebDriverDomMapper.Candidate c : pairs) {
            DomElementInfo info = c.getInfo();
            domCandidates.add(info);
            elementsByInfo.put(info, c.getElement());
        }

        return new CandidatesSnapshot(domCandidates, elementsByInfo);
    }

    Map<DomElementInfo, WebElement> collectCandidates() {
        return collectCandidatesSnapshot().elementsByInfo;
    }

    // ----------- main API -----------

    /**
     * Resolves the given intent phrase to a {@link WebElement}.
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and may collect a fresh DOM candidates snapshot.
     * To reuse a single snapshot across multiple operations within one logical step, use {@link #actionsChain()} and execute
     * via {@link ActionsChain#perform()}.
     *
     * @param intentPhrase intent phrase describing the target element
     * @return resolved element
     */
    public WebElement findElement(String intentPhrase) {
        return resolveIntent(intentPhrase, false).element;
    }

    /**
     * Resolves the given intent phrase and performs {@link WebElement#click()} on the resolved element.
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and may collect a fresh DOM candidates snapshot.
     * To reuse a single snapshot across multiple operations within one logical step, use {@link #actionsChain()} and execute
     * via {@link ActionsChain#perform()}.
     *
     * @param intentPhrase intent phrase describing the target element
     */
    public void click(String intentPhrase) {
        resolveIntent(intentPhrase, false).element.click();
    }

    /**
     * Resolves the given intent phrase and performs {@link WebElement#sendKeys(CharSequence...)} on the resolved element.
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and may collect a fresh DOM candidates snapshot.
     * To reuse a single snapshot across multiple operations within one logical step, use {@link #actionsChain()} and execute
     * via {@link ActionsChain#perform()}.
     *
     * @param intentPhrase intent phrase describing the target element
     * @param keys keys to send
     */
    public void sendKeys(String intentPhrase, CharSequence... keys) {
        resolveIntent(intentPhrase, false).element.sendKeys(keys);
    }

    /**
     * Resolves the intent phrase and returns a best-effort derived XPath for the resolved element.
     * <p>
     * Uses a "last resolve" cache to avoid a second full candidates pass when {@link #getCssSelector(String)} is called next
     * for the same intent phrase.
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and may collect a fresh DOM candidates snapshot
     * unless served from the last resolve cache.
     *
     * @param intentPhrase intent phrase describing the target element
     * @return derived XPath for the resolved element
     */
    public String getXPath(String intentPhrase) {
        ResolvedElement r = resolveIntent(intentPhrase, true);
        return r.xPath;
    }

    /**
     * Resolves the intent phrase and returns a best-effort derived CSS selector for the resolved element.
     * <p>
     * Uses a "last resolve" cache to avoid a second full candidates pass when {@link #getXPath(String)} was called right before
     * for the same intent phrase.
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and may collect a fresh DOM candidates snapshot
     * unless served from the last resolve cache.
     *
     * @param intentPhrase intent phrase describing the target element
     * @return derived CSS selector for the resolved element
     */
    public String getCssSelector(String intentPhrase) {
        ResolvedElement r = resolveIntent(intentPhrase, true);
        return r.cssSelector;
    }

    /**
     * Creates a helper for performing multiple operations against the same intent phrase.
     * <p>
     * The returned action resolves the intent phrase at most once per instance (best-effort). The first resolution may collect
     * a DOM candidates snapshot. For multi-intent flows where snapshot reuse matters, prefer {@link #actionsChain()}.
     *
     * @param intentPhrase intent phrase describing the target element
     * @return single-intent action helper
     */
    public SingleIntentAction into(String intentPhrase) {
        return new SingleIntentAction(this, intentPhrase);
    }

    /**
     * Alias for {@link #into(String)}.
     *
     * @param intentPhrase intent phrase describing the target element
     * @return single-intent action helper
     */
    public SingleIntentAction at(String intentPhrase) {
        return into(intentPhrase);
    }

// ----------- PageObject / manual locator bridge -----------

    /**
     * Resolves an element using a manual Selenium {@link By} locator.
     * <p>
     * This API is intended as a bridge for existing PageObjects: you can keep explicit locators while still using Intentium
     * logging and optional consistency checks.
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and does not participate in
     * {@link ActionsChain} snapshot reuse.
     *
     * @param by Selenium locator
     * @return resolved element
     */
    public WebElement findElement(By by) {
        return resolveBy(by, false).element;
    }

    /**
     * Resolves an element using the given {@link By} locator and performs {@link WebElement#click()}.
     * <p>
     * Logging and optional consistency checks are applied to the resolved target.
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and does not participate in
     * {@link ActionsChain} snapshot reuse.
     *
     * @param by Selenium locator
     */
    public void click(By by) {
        resolveBy(by, false).element.click();
    }

    /**
     * Resolves an element using the given {@link By} locator and performs {@link WebElement#sendKeys(CharSequence...)}.
     * <p>
     * Logging and optional consistency checks are applied to the resolved target.
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and does not participate in
     * {@link ActionsChain} snapshot reuse.
     *
     * @param by Selenium locator
     * @param keys keys to send
     */
    public void sendKeys(By by, CharSequence... keys) {
        resolveBy(by, false).element.sendKeys(keys);
    }

    /**
     * Resolves an element using the given {@link By} locator and returns a best-effort XPath for the target.
     * <p>
     * If the provided locator is an XPath locator, the returned value is the original XPath expression. Otherwise the XPath
     * is derived from the resolved element as a best-effort "quick" locator.
     *
     * @param by Selenium locator
     * @return XPath for the target (manual when possible, otherwise derived)
     */
    public String getXPath(By by) {
        return resolveBy(by, true).xPath;
    }

    /**
     * Resolves an element using the given {@link By} locator and returns a best-effort CSS selector for the target.
     * <p>
     * If the provided locator is a CSS locator, the returned value is the original CSS selector. Otherwise the selector is
     * derived from the resolved element as a best-effort "quick" locator.
     *
     * @param by Selenium locator
     * @return CSS selector for the target (manual when possible, otherwise derived)
     */
    public String getCssSelector(By by) {
        return resolveBy(by, true).cssSelector;
    }

    /**
     * Creates a helper for performing multiple operations against a single known target specified by {@link By}.
     * <p>
     * The helper resolves the target at most once per instance (best-effort) and caches the resolved element and derived
     * locators when requested.
     * <p>
     * Logging and optional consistency checks are applied to the resolved target.
     *
     * @param by Selenium locator
     * @return single-target action helper
     */
    public SingleTargetAction into(By by) {
        return new SingleTargetAction(this, by);
    }

    /**
     * Alias for {@link #into(By)}.
     *
     * @param by Selenium locator
     * @return single-target action helper
     */
    public SingleTargetAction at(By by) {
        return into(by);
    }

    /**
     * Resolves or acts on an existing {@link WebElement} reference (for example, returned from PageFactory).
     * <p>
     * Best-effort behavior:
     * <ul>
     *   <li>If the element is a PageFactory proxy and Intentium can extract the underlying {@link By}, Intentium prefers that
     *       {@link By} for logging, derived locators and optional consistency checks.</li>
     *   <li>Otherwise Intentium operates on the provided element directly and derives "quick" XPath/CSS when needed.</li>
     * </ul>
     * <p>
     * Snapshot contract: this direct call is treated as an independent step and does not participate in
     * {@link ActionsChain} snapshot reuse.
     *
     * @param element existing element reference
     * @return resolved element (may be the same reference or a resolved target from extracted {@link By})
     */
    public WebElement findElement(WebElement element) {
        return resolveWebElement(element, false).element;
    }

    /**
     * Performs {@link WebElement#click()} on the provided {@link WebElement}.
     * <p>
     * If the element is a PageFactory proxy and Intentium can extract an underlying {@link By}, Intentium prefers resolving
     * via that {@link By} for logging and optional consistency checks.
     *
     * @param element existing element reference
     */
    public void click(WebElement element) {
        resolveWebElement(element, false).element.click();
    }

    /**
     * Performs {@link WebElement#sendKeys(CharSequence...)} on the provided {@link WebElement}.
     * <p>
     * If the element is a PageFactory proxy and Intentium can extract an underlying {@link By}, Intentium prefers resolving
     * via that {@link By} for logging and optional consistency checks.
     *
     * @param element existing element reference
     * @param keys keys to send
     */
    public void sendKeys(WebElement element, CharSequence... keys) {
        resolveWebElement(element, false).element.sendKeys(keys);
    }

    /**
     * Returns a best-effort XPath for the provided {@link WebElement}.
     * <p>
     * If the element is a PageFactory proxy and Intentium can extract an underlying {@link By} of XPath kind, the returned
     * value is the extracted XPath expression. Otherwise the XPath is derived from the element as a best-effort "quick"
     * locator.
     *
     * @param element existing element reference
     * @return XPath for the target (manual when possible, otherwise derived)
     */
    public String getXPath(WebElement element) {
        return resolveWebElement(element, true).xPath;
    }

    /**
     * Returns a best-effort CSS selector for the provided {@link WebElement}.
     * <p>
     * If the element is a PageFactory proxy and Intentium can extract an underlying {@link By} of CSS kind, the returned
     * value is the extracted CSS selector. Otherwise the selector is derived from the element as a best-effort "quick"
     * locator.
     *
     * @param element existing element reference
     * @return CSS selector for the target (manual when possible, otherwise derived)
     */
    public String getCssSelector(WebElement element) {
        return resolveWebElement(element, true).cssSelector;
    }

    /**
     * Creates a helper for performing multiple operations against a single known target specified by {@link WebElement}.
     * <p>
     * The helper resolves the target at most once per instance (best-effort). If the element is a PageFactory proxy and
     * Intentium can extract an underlying {@link By}, the helper prefers resolving via that {@link By}.
     * <p>
     * Logging and optional consistency checks are applied to the resolved target.
     *
     * @param element existing element reference
     * @return single-target action helper
     */
    public SingleTargetAction into(WebElement element) {
        return new SingleTargetAction(this, element);
    }

    /**
     * Alias for {@link #into(WebElement)}.
     *
     * @param element existing element reference
     * @return single-target action helper
     */
    public SingleTargetAction at(WebElement element) {
        return into(element);
    }

    /**
     * Creates an {@link ActionsChain} that executes a sequence of operations as a single logical step.
     * <p>
     * Within one {@link ActionsChain#perform()} call, Intentium reuses a single DOM candidates snapshot for all intent phrase
     * resolutions. Snapshot invalidation is triggered by URL change (navigation).
     *
     * @return new actions chain instance bound to this driver
     */
    public ActionsChain actionsChain() {
        return new ActionsChain(this);
    }

    // ----------- internal resolve API (for caching) -----------

    ResolvedElement resolveIntent(String intentPhrase, boolean forceLocators) {
        if (forceLocators) {
            ResolvedElement cached = tryGetLastLocators(intentPhrase);
            if (cached != null) {
                return cached;
            }
        }

        CandidatesSnapshot snapshot = collectCandidatesSnapshot();
        if (snapshot.domCandidates.isEmpty()) {
            throw new ElementSelectionException("No candidates found on page");
        }

        ResolvedElement resolved = resolveIntent(intentPhrase, snapshot, forceLocators);

        if (forceLocators) {
            storeLastLocators(intentPhrase, resolved);
        }

        return resolved;
    }

    ResolvedElement resolveIntent(
            String intentPhrase,
            CandidatesSnapshot snapshot,
            boolean forceLocators
    ) {
        IntentRole role = intentResolver.resolveRole(intentPhrase, language);

        if (snapshot == null || snapshot.domCandidates == null || snapshot.domCandidates.isEmpty()) {
            throw new ElementSelectionException("No candidates found on page for role " + role);
        }

        ElementMatch match = elementSelector.selectBest(role, snapshot.domCandidates);

        DomElementInfo elementInfo = match.getElement();
        WebElement webElement = snapshot.elementsByInfo.get(elementInfo);
        if (webElement == null) {
            throw new ElementSelectionException(
                    "Internal error: selected DomElementInfo has no corresponding WebElement. " +
                            "ElementSelector must return the same DomElementInfo instance from the provided candidates list."
            );
        }

        LocatorLogDetail logDetail = currentLogDetail();

        boolean needXPath = forceLocators
                || consistencyCheckEnabled
                || (resolvedElementLogger != null && (logDetail == LocatorLogDetail.XPATH_ONLY || logDetail == LocatorLogDetail.BOTH));

        boolean needCss = forceLocators
                || consistencyCheckEnabled
                || (resolvedElementLogger != null && (logDetail == LocatorLogDetail.CSS_ONLY || logDetail == LocatorLogDetail.BOTH));

        String xPath = null;
        String css = null;

        if (needXPath) {
            xPath = buildStableXPath(elementInfo, webElement, snapshot);
        }
        if (needCss) {
            css = buildStableCssSelector(elementInfo, webElement, snapshot);
        }

        if (resolvedElementLogger != null) {
            String logXPath = (logDetail == LocatorLogDetail.XPATH_ONLY || logDetail == LocatorLogDetail.BOTH) ? xPath : null;
            String logCss = (logDetail == LocatorLogDetail.CSS_ONLY || logDetail == LocatorLogDetail.BOTH) ? css : null;
            resolvedElementLogger.logResolvedElement(intentPhrase, role, logXPath, logCss, elementInfo);
        }

        if (consistencyCheckEnabled) {
            // consistency requires BOTH locators, and we ensured they are built above
            runConsistencyCheck(intentPhrase, role, webElement, xPath, css);
        }

        return new ResolvedElement(intentPhrase, role, elementInfo, webElement, xPath, css);
    }

    ResolvedElement resolveIntent(
            String intentPhrase,
            Map<DomElementInfo, WebElement> candidatesMap,
            List<DomElementInfo> domCandidates,
            boolean forceLocators
    ) {
        CandidatesSnapshot snapshot = new CandidatesSnapshot(domCandidates, candidatesMap);
        return resolveIntent(intentPhrase, snapshot, forceLocators);
    }

    private ResolvedElement tryGetLastLocators(String intentPhrase) {
        String url = currentUrl();

        if (!Objects.equals(lastLocatorsCache.url, url)) {
            return null;
        }
        if (!Objects.equals(lastLocatorsCache.intentPhrase, intentPhrase)) {
            return null;
        }
        if (lastLocatorsCache.loggerAtResolve != resolvedElementLogger) {
            return null;
        }
        if (lastLocatorsCache.consistencyAtResolve != consistencyCheckEnabled) {
            return null;
        }
        if (lastLocatorsCache.resolved == null) {
            return null;
        }
        if (lastLocatorsCache.resolved.xPath == null || lastLocatorsCache.resolved.cssSelector == null) {
            return null;
        }
        if (lastLocatorsCache.allowHashedLastResortAtResolve != allowHashedLastResort) {
            return null;
        }

        return lastLocatorsCache.resolved;
    }

    private void storeLastLocators(String intentPhrase, ResolvedElement resolved) {
        lastLocatorsCache.url = currentUrl();
        lastLocatorsCache.intentPhrase = intentPhrase;
        lastLocatorsCache.loggerAtResolve = resolvedElementLogger;
        lastLocatorsCache.consistencyAtResolve = consistencyCheckEnabled;
        lastLocatorsCache.resolved = resolved;
        lastLocatorsCache.allowHashedLastResortAtResolve = allowHashedLastResort;
    }

    // ----------- PageObject / manual target resolve -----------

    enum ManualByKind {
        XPATH,
        CSS,
        OTHER
    }

    static final class ManualByInfo {
        final ManualByKind kind;
        final String value;

        ManualByInfo(ManualByKind kind, String value) {
            this.kind = kind;
            this.value = value;
        }
    }

    ResolvedElement resolveBy(By by, boolean forceLocators) {
        Objects.requireNonNull(by, "by must not be null");
        return resolveByInternal(by.toString(), by, forceLocators);
    }

    ResolvedElement resolveWebElement(WebElement element, boolean forceLocators) {
        Objects.requireNonNull(element, "element must not be null");

        By extracted = PageFactoryByExtractor.tryExtractBy(element);
        if (extracted != null) {
            // Best-effort: treat PageFactory proxies as manual By targets when possible.
            return resolveByInternal("PageObject(" + extracted + ")", extracted, forceLocators);
        }

        LocatorLogDetail logDetail = currentLogDetail();

        boolean needXPath = forceLocators
                || consistencyCheckEnabled
                || (resolvedElementLogger != null && (logDetail == LocatorLogDetail.XPATH_ONLY || logDetail == LocatorLogDetail.BOTH));

        boolean needCss = forceLocators
                || consistencyCheckEnabled
                || (resolvedElementLogger != null && (logDetail == LocatorLogDetail.CSS_ONLY || logDetail == LocatorLogDetail.BOTH));

        boolean needElementInfo = (resolvedElementLogger != null) || needXPath || needCss;

        DomElementInfo elementInfo = needElementInfo ? domMapper.toDomElementInfo(element) : null;

        String xPath = null;
        String css = null;

        if (needXPath) {
            xPath = buildQuickXPath(elementInfo, element);
        }
        if (needCss) {
            css = buildQuickCssSelector(elementInfo, element);
        }

        if (resolvedElementLogger != null) {
            String logXPath = (logDetail == LocatorLogDetail.XPATH_ONLY || logDetail == LocatorLogDetail.BOTH) ? xPath : null;
            String logCss = (logDetail == LocatorLogDetail.CSS_ONLY || logDetail == LocatorLogDetail.BOTH) ? css : null;
            resolvedElementLogger.logResolvedElement("WebElement", null, logXPath, logCss, elementInfo);
        }

        if (consistencyCheckEnabled) {
            runConsistencyCheck("WebElement", null, element, xPath, css);
        }

        return new ResolvedElement("WebElement", null, elementInfo, element, xPath, css);
    }

    private ResolvedElement resolveByInternal(String phrase, By by, boolean forceLocators) {
        LocatorLogDetail logDetail = currentLogDetail();

        boolean needXPath = forceLocators
                || consistencyCheckEnabled
                || (resolvedElementLogger != null && (logDetail == LocatorLogDetail.XPATH_ONLY || logDetail == LocatorLogDetail.BOTH));

        boolean needCss = forceLocators
                || consistencyCheckEnabled
                || (resolvedElementLogger != null && (logDetail == LocatorLogDetail.CSS_ONLY || logDetail == LocatorLogDetail.BOTH));

        WebElement element = driver.findElement(by);

        boolean needElementInfo = (resolvedElementLogger != null) || needXPath || needCss;
        DomElementInfo elementInfo = needElementInfo ? domMapper.toDomElementInfo(element) : null;

        String xPath = null;
        String css = null;

        if (needXPath || needCss) {
            ManualByInfo info = parseManualBy(by);

            if (needXPath) {
                if (info.kind == ManualByKind.XPATH) {
                    xPath = info.value; // manual
                } else {
                    xPath = buildQuickXPath(elementInfo, element); // derived
                }
            }

            if (needCss) {
                if (info.kind == ManualByKind.CSS) {
                    css = info.value; // manual
                } else {
                    css = buildQuickCssSelector(elementInfo, element); // derived
                }
            }
        }

        if (resolvedElementLogger != null) {
            String logXPath = (logDetail == LocatorLogDetail.XPATH_ONLY || logDetail == LocatorLogDetail.BOTH) ? xPath : null;
            String logCss = (logDetail == LocatorLogDetail.CSS_ONLY || logDetail == LocatorLogDetail.BOTH) ? css : null;
            resolvedElementLogger.logResolvedElement(phrase, null, logXPath, logCss, elementInfo);
        }

        if (consistencyCheckEnabled) {
            runConsistencyCheck(phrase, null, element, xPath, css);
        }

        return new ResolvedElement(phrase, null, elementInfo, element, xPath, css);
    }

    private ManualByInfo parseManualBy(By by) {
        String s = String.valueOf(by);
        if (s.startsWith("By.xpath: ")) {
            return new ManualByInfo(ManualByKind.XPATH, s.substring("By.xpath: ".length()));
        }
        if (s.startsWith("By.cssSelector: ")) {
            return new ManualByInfo(ManualByKind.CSS, s.substring("By.cssSelector: ".length()));
        }
        return new ManualByInfo(ManualByKind.OTHER, s);
    }

    /**
     * "Quick" locator builders for manual targets (By/WebElement).
     * <p>
     * Goal: avoid collecting full DOM candidates snapshot for PageObject bridging,
     * but still produce reasonably stable XPath/CSS for logging/diagnostics.
     * <p>
     * Important: any non-trivial locator produced here is verified for uniqueness via
     * {@code driver.findElements(...).size() == 1}. If not unique, we fall back to a generic locator,
     * which makes consistency checks skip safely.
     */
    private String buildQuickXPath(DomElementInfo info, WebElement element) {
        String tag = safeTagName(info, element);

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "//*[@id=" + xpathLiteral(id) + "]";
        }

        String testAttrName = safe(info == null ? null : info.getTestAttributeName());
        String testAttrValue = safe(info == null ? null : info.getTestAttributeValue());
        if (!testAttrName.isBlank() && !testAttrValue.isBlank()) {
            String candidate = "//" + tag + "[@" + testAttrName + "=" + xpathLiteral(testAttrValue) + "]";
            if (isUnique(By.xpath(candidate))) {
                return candidate;
            }
        }

        String name = safe(info == null ? null : info.getName());
        if (!name.isBlank()) {
            String candidate = "//" + tag + "[@name=" + xpathLiteral(name) + "]";
            if (isUnique(By.xpath(candidate))) {
                return candidate;
            }
        }

        String aria = safe(info == null ? null : info.getAriaLabel());
        if (!aria.isBlank()) {
            String candidate = "//" + tag + "[@aria-label=" + xpathLiteral(aria) + "]";
            if (isUnique(By.xpath(candidate))) {
                return candidate;
            }
        }

        String placeholder = safe(info == null ? null : info.getPlaceholder());
        if (!placeholder.isBlank()) {
            String candidate = "//" + tag + "[@placeholder=" + xpathLiteral(placeholder) + "]";
            if (isUnique(By.xpath(candidate))) {
                return candidate;
            }
        }

        String title = safe(info == null ? null : info.getTitle());
        if (!title.isBlank()) {
            String candidate = "//" + tag + "[@title=" + xpathLiteral(title) + "]";
            if (isUnique(By.xpath(candidate))) {
                return candidate;
            }
        }

        // generic fallback
        return "//" + tag;
    }

    private String buildQuickCssSelector(DomElementInfo info, WebElement element) {
        String tag = safeTagName(info, element);

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "#" + cssEscapeIdentifier(id);
        }

        String testAttrName = safe(info == null ? null : info.getTestAttributeName());
        String testAttrValue = safe(info == null ? null : info.getTestAttributeValue());
        if (!testAttrName.isBlank() && !testAttrValue.isBlank()) {
            String candidate = tag + "[" + testAttrName + "=" + cssAttrLiteral(testAttrValue) + "]";
            if (isUnique(By.cssSelector(candidate))) {
                return candidate;
            }
        }

        String name = safe(info == null ? null : info.getName());
        if (!name.isBlank()) {
            String candidate = tag + "[name=" + cssAttrLiteral(name) + "]";
            if (isUnique(By.cssSelector(candidate))) {
                return candidate;
            }
        }

        String aria = safe(info == null ? null : info.getAriaLabel());
        if (!aria.isBlank()) {
            String candidate = tag + "[aria-label=" + cssAttrLiteral(aria) + "]";
            if (isUnique(By.cssSelector(candidate))) {
                return candidate;
            }
        }

        String placeholder = safe(info == null ? null : info.getPlaceholder());
        if (!placeholder.isBlank()) {
            String candidate = tag + "[placeholder=" + cssAttrLiteral(placeholder) + "]";
            if (isUnique(By.cssSelector(candidate))) {
                return candidate;
            }
        }

        String title = safe(info == null ? null : info.getTitle());
        if (!title.isBlank()) {
            String candidate = tag + "[title=" + cssAttrLiteral(title) + "]";
            if (isUnique(By.cssSelector(candidate))) {
                return candidate;
            }
        }

        // generic fallback
        return tag;
    }

    private boolean isUnique(By by) {
        try {
            return driver.findElements(by).size() == 1;
        } catch (RuntimeException e) {
            // invalid selector or driver quirks: just treat as non-unique
            return false;
        }
    }

    // ----------- locator builders -----------

    /**
     * Builds a best-effort "stable" XPath for the resolved element.
     * <p>
     * The builder prefers semantically meaningful anchors and avoids generated (hashed) CSS classes by default.
     * Hashed tokens are considered only as a strict last-resort fallback and only when enabled via
     * {@link #withAllowHashedLastResort(boolean)}.
     * <p>
     * Resolution order (simplified):
     * <ol>
     *   <li>{@code id}</li>
     *   <li>test/qa attribute (for example, {@code data-testid}, {@code data-qa})</li>
     *   <li>{@code name}, {@code aria-label}, {@code placeholder}, {@code title} (unique in context)</li>
     *   <li>the same attributes combined with {@code type} (unique in context)</li>
     *   <li>a unique non-hashed CSS class token (unique in context)</li>
     *   <li>label-based fallback (a {@code label} followed by the target tag)</li>
     *   <li>ordinal fallback within context</li>
     *   <li>a unique hashed CSS class token as a strict last resort (only when allowed)</li>
     *   <li>bare {@code //tag} fallback</li>
     * </ol>
     * <p>
     * Context includes the enclosing form identifier when available to reduce collisions on pages with repeated forms.
     *
     * @param info resolved DOM info snapshot for the element
     * @param element resolved Selenium element
     * @param snapshot candidates snapshot of the current page
     * @return best-effort stable XPath
     */
    String buildStableXPath(DomElementInfo info, WebElement element, CandidatesSnapshot snapshot) {
        String tag = safeTagName(info, element);

        FormContext form = resolveFormContext(element);
        String formKey = resolveFormKey(info, form);

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "//*[@id=" + xpathLiteral(id) + "]";
        }

        String testAttrName = safe(info == null ? null : info.getTestAttributeName());
        String testAttrValue = safe(info == null ? null : info.getTestAttributeValue());
        if (!testAttrName.isBlank() && !testAttrValue.isBlank()) {
            if (isUniqueInContext(snapshot, tag, formKey,
                    DomElementInfo::getTestAttributeName, testAttrName,
                    DomElementInfo::getTestAttributeValue, testAttrValue)) {
                return xPathWithFormPrefix(form,
                        "//" + tag + "[@" + testAttrName + "=" + xpathLiteral(testAttrValue) + "]");
            }
        }

        String type = safe(info == null ? null : info.getType());
        String name = safe(info == null ? null : info.getName());
        String aria = safe(info == null ? null : info.getAriaLabel());
        String placeholder = safe(info == null ? null : info.getPlaceholder());
        String title = safe(info == null ? null : info.getTitle());
        String label = normalizeText(safe(info == null ? null : info.getLabelText()));
        String cssClasses = safe(info == null ? null : info.getCssClasses());

        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getName, name)) {
            return xPathWithFormPrefix(form, "//" + tag + "[@name=" + xpathLiteral(name) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getAriaLabel, aria)) {
            return xPathWithFormPrefix(form, "//" + tag + "[@aria-label=" + xpathLiteral(aria) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getPlaceholder, placeholder)) {
            return xPathWithFormPrefix(form, "//" + tag + "[@placeholder=" + xpathLiteral(placeholder) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getTitle, title)) {
            return xPathWithFormPrefix(form, "//" + tag + "[@title=" + xpathLiteral(title) + "]");
        }

        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getName, name, DomElementInfo::getType, type)) {
            return xPathWithFormPrefix(form, "//" + tag +
                    "[@name=" + xpathLiteral(name) + " and @type=" + xpathLiteral(type) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getAriaLabel, aria, DomElementInfo::getType, type)) {
            return xPathWithFormPrefix(form, "//" + tag +
                    "[@aria-label=" + xpathLiteral(aria) + " and @type=" + xpathLiteral(type) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getPlaceholder, placeholder, DomElementInfo::getType, type)) {
            return xPathWithFormPrefix(form, "//" + tag +
                    "[@placeholder=" + xpathLiteral(placeholder) + " and @type=" + xpathLiteral(type) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getTitle, title, DomElementInfo::getType, type)) {
            return xPathWithFormPrefix(form, "//" + tag +
                    "[@title=" + xpathLiteral(title) + " and @type=" + xpathLiteral(type) + "]");
        }

        String classToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses, false);
        if (classToken != null) {
            String classPredicate = "contains(concat(' ', normalize-space(@class), ' '), " +
                    xpathLiteral(" " + classToken + " ") + ")";
            return xPathWithFormPrefix(form, "//" + tag + "[" + classPredicate + "]");
        }

        if (!label.isBlank()) {
            String labelXPath = "//label[normalize-space(.)=" + xpathLiteral(label) + "]/following::" + tag + "[1]";
            return xPathWithFormPrefix(form, labelXPath);
        }

        String base = "//" + tag;
        if (!type.isBlank() && "input".equalsIgnoreCase(tag)) {
            base = base + "[@type=" + xpathLiteral(type) + "]";
        }

        int ordinal = ordinalInContext(snapshot, info, tag, formKey, type);
        if (ordinal > 0) {
            String scopedBase = xPathWithFormPrefix(form, base);
            return "(" + scopedBase + ")[" + ordinal + "]";
        }

        String hashedClassToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses, allowHashedLastResort);
        if (hashedClassToken != null) {
            String classPredicate = "contains(concat(' ', normalize-space(@class), ' '), " +
                    xpathLiteral(" " + hashedClassToken + " ") + ")";
            return xPathWithFormPrefix(form, "//" + tag + "[" + classPredicate + "]");
        }

        return xPathWithFormPrefix(form, "//" + tag);
    }


    /**
     * Builds a best-effort "stable" CSS selector for the resolved element.
     * <p>
     * The builder prefers semantically meaningful anchors and avoids generated (hashed) CSS classes by default.
     * Hashed tokens are considered only as a strict last-resort fallback and only when enabled via
     * {@link #withAllowHashedLastResort(boolean)}.
     * <p>
     * Resolution order (simplified):
     * <ol>
     *   <li>{@code id}</li>
     *   <li>test/qa attribute (for example, {@code data-testid}, {@code data-qa})</li>
     *   <li>{@code name}, {@code aria-label}, {@code placeholder}, {@code title} (unique in context)</li>
     *   <li>the same attributes combined with {@code type} (unique in context)</li>
     *   <li>a unique non-hashed CSS class token (unique in context)</li>
     *   <li>{@code type} uniqueness (when applicable)</li>
     *   <li>a unique hashed CSS class token as a strict last resort (only when allowed)</li>
     *   <li>bare {@code tag} fallback</li>
     * </ol>
     * <p>
     * Context includes the enclosing form identifier when available to reduce collisions on pages with repeated forms.
     *
     * @param info resolved DOM info snapshot for the element
     * @param element resolved Selenium element
     * @param snapshot candidates snapshot of the current page
     * @return best-effort stable CSS selector
     */
    String buildStableCssSelector(DomElementInfo info, WebElement element, CandidatesSnapshot snapshot) {
        String tag = safeTagName(info, element);

        FormContext form = resolveFormContext(element);
        String formKey = resolveFormKey(info, form);

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "#" + cssEscapeIdentifier(id);
        }

        String testAttrName = safe(info == null ? null : info.getTestAttributeName());
        String testAttrValue = safe(info == null ? null : info.getTestAttributeValue());
        if (!testAttrName.isBlank() && !testAttrValue.isBlank()) {
            if (isUniqueInContext(snapshot, tag, formKey,
                    DomElementInfo::getTestAttributeName, testAttrName,
                    DomElementInfo::getTestAttributeValue, testAttrValue)) {
                return cssWithFormPrefix(form,
                        tag + "[" + testAttrName + "=" + cssAttrLiteral(testAttrValue) + "]");
            }
        }

        String type = safe(info == null ? null : info.getType());
        String name = safe(info == null ? null : info.getName());
        String aria = safe(info == null ? null : info.getAriaLabel());
        String placeholder = safe(info == null ? null : info.getPlaceholder());
        String title = safe(info == null ? null : info.getTitle());
        String cssClasses = safe(info == null ? null : info.getCssClasses());

        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getName, name)) {
            return cssWithFormPrefix(form, tag + "[name=" + cssAttrLiteral(name) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getAriaLabel, aria)) {
            return cssWithFormPrefix(form, tag + "[aria-label=" + cssAttrLiteral(aria) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getPlaceholder, placeholder)) {
            return cssWithFormPrefix(form, tag + "[placeholder=" + cssAttrLiteral(placeholder) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getTitle, title)) {
            return cssWithFormPrefix(form, tag + "[title=" + cssAttrLiteral(title) + "]");
        }

        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getName, name, DomElementInfo::getType, type)) {
            return cssWithFormPrefix(form, tag +
                    "[name=" + cssAttrLiteral(name) + "][type=" + cssAttrLiteral(type) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getAriaLabel, aria, DomElementInfo::getType, type)) {
            return cssWithFormPrefix(form, tag +
                    "[aria-label=" + cssAttrLiteral(aria) + "][type=" + cssAttrLiteral(type) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getPlaceholder, placeholder, DomElementInfo::getType, type)) {
            return cssWithFormPrefix(form, tag +
                    "[placeholder=" + cssAttrLiteral(placeholder) + "][type=" + cssAttrLiteral(type) + "]");
        }
        if (isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getTitle, title, DomElementInfo::getType, type)) {
            return cssWithFormPrefix(form, tag +
                    "[title=" + cssAttrLiteral(title) + "][type=" + cssAttrLiteral(type) + "]");
        }

        String classToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses, false);
        if (classToken != null) {
            return cssWithFormPrefix(form, tag + "." + cssEscapeIdentifier(classToken));
        }

        if (!type.isBlank() && isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getType, type)) {
            return cssWithFormPrefix(form, tag + "[type=" + cssAttrLiteral(type) + "]");
        }

        String hashedClassToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses, allowHashedLastResort);
        if (hashedClassToken != null) {
            return cssWithFormPrefix(form, tag + "." + cssEscapeIdentifier(hashedClassToken));
        }

        return cssWithFormPrefix(form, tag);
    }


    // ----------- context helpers -----------

    private static final class FormContext {
        static final FormContext NONE = new FormContext("", "");
        final String id;
        final String name;

        FormContext(String id, String name) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
        }

        String identifier() {
            if (!id.isBlank()) {
                return id;
            }
            return name;
        }
    }

    private FormContext resolveFormContext(WebElement element) {
        try {
            WebElement form = element.findElement(By.xpath("ancestor::form[1]"));
            String id = safe(form.getAttribute("id"));
            String name = safe(form.getAttribute("name"));
            if (id.isBlank() && name.isBlank()) {
                return FormContext.NONE;
            }
            return new FormContext(id, name);
        } catch (Exception e) {
            return FormContext.NONE;
        }
    }

    private String resolveFormKey(DomElementInfo info, FormContext form) {
        String fromInfo = safe(info == null ? null : info.getFormIdentifier());
        if (!fromInfo.isBlank()) {
            return fromInfo;
        }

        if (form == null) {
            return "";
        }

        String id = safe(form.id);
        if (!id.isBlank()) {
            return "id:" + id;
        }

        String name = safe(form.name);
        if (!name.isBlank()) {
            return "name:" + name;
        }

        return "";
    }

    private String xPathWithFormPrefix(FormContext form, String xPath) {
        if (form == null || form == FormContext.NONE) {
            return xPath;
        }
        if (!form.id.isBlank()) {
            return "//form[@id=" + xpathLiteral(form.id) + "]" + ensureStartsWithDoubleSlash(xPath);
        }
        if (!form.name.isBlank()) {
            return "//form[@name=" + xpathLiteral(form.name) + "]" + ensureStartsWithDoubleSlash(xPath);
        }
        return xPath;
    }

    private String cssWithFormPrefix(FormContext form, String css) {
        if (form == null || form == FormContext.NONE) {
            return css;
        }
        if (!form.id.isBlank()) {
            return "form#" + cssEscapeIdentifier(form.id) + " " + css;
        }
        if (!form.name.isBlank()) {
            return "form[name=" + cssAttrLiteral(form.name) + "] " + css;
        }
        return css;
    }

    private String ensureStartsWithDoubleSlash(String xPath) {
        if (xPath == null || xPath.isBlank()) {
            return "";
        }
        if (xPath.startsWith("//")) {
            return xPath;
        }
        if (xPath.startsWith("/")) {
            return "/" + xPath;
        }
        return "//" + xPath;
    }

    // ----------- uniqueness helpers -----------

    @FunctionalInterface
    private interface ValueGetter {
        String get(DomElementInfo info);
    }

    private boolean isUniqueInContext(
            CandidatesSnapshot snapshot,
            String tag,
            String formIdentifier,
            ValueGetter getter,
            String value
    ) {
        if (snapshot == null || snapshot.domCandidates == null) {
            return false;
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        int count = 0;
        for (DomElementInfo d : snapshot.domCandidates) {
            if (d == null) {
                continue;
            }
            if (!tagEquals(tag, safe(d.getTagName()))) {
                continue;
            }
            if (!formMatches(formIdentifier, safe(d.getFormIdentifier()))) {
                continue;
            }
            if (value.equals(safe(getter.get(d)))) {
                count++;
                if (count > 1) {
                    return false;
                }
            }
        }
        return count == 1;
    }

    private boolean isUniqueInContext(
            CandidatesSnapshot snapshot,
            String tag,
            String formIdentifier,
            ValueGetter getter1,
            String value1,
            ValueGetter getter2,
            String value2
    ) {
        if (snapshot == null || snapshot.domCandidates == null) {
            return false;
        }
        if (value1 == null || value1.isBlank() || value2 == null || value2.isBlank()) {
            return false;
        }
        int count = 0;
        for (DomElementInfo d : snapshot.domCandidates) {
            if (d == null) {
                continue;
            }
            if (!tagEquals(tag, safe(d.getTagName()))) {
                continue;
            }
            if (!formMatches(formIdentifier, safe(d.getFormIdentifier()))) {
                continue;
            }
            if (value1.equals(safe(getter1.get(d))) && value2.equals(safe(getter2.get(d)))) {
                count++;
                if (count > 1) {
                    return false;
                }
            }
        }
        return count == 1;
    }

    private boolean formMatches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return actual == null || actual.isBlank();
        }
        return expected.equals(actual);
    }

    private int ordinalInContext(
            CandidatesSnapshot snapshot,
            DomElementInfo target,
            String tag,
            String formIdentifier,
            String type
    ) {
        if (snapshot == null || snapshot.domCandidates == null || target == null) {
            return -1;
        }

        int ordinal = 0;
        for (DomElementInfo d : snapshot.domCandidates) {
            if (d == null) {
                continue;
            }
            if (!tagEquals(tag, safe(d.getTagName()))) {
                continue;
            }
            if (!formMatches(formIdentifier, safe(d.getFormIdentifier()))) {
                continue;
            }

            if (!type.isBlank() && "input".equalsIgnoreCase(tag)) {
                if (!type.equals(safe(d.getType()))) {
                    continue;
                }
            }

            ordinal++;
            if (d == target) {
                return ordinal;
            }
        }
        return -1;
    }

    private String pickUniqueClassToken(CandidatesSnapshot snapshot, String tag, String formIdentifier, String cssClasses) {
        return pickUniqueClassToken(snapshot, tag, formIdentifier, cssClasses, true);
    }

    private String pickUniqueClassToken(
            CandidatesSnapshot snapshot,
            String tag,
            String formIdentifier,
            String cssClasses,
            boolean allowHashedLastResort
    ) {
        if (snapshot == null || snapshot.domCandidates == null) {
            return null;
        }
        if (cssClasses == null || cssClasses.isBlank()) {
            return null;
        }

        String[] tokens = cssClasses.trim().split("\\s+");

        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (isProbablyHashedCssClassToken(token)) {
                continue;
            }
            if (isUniqueClassTokenInContext(snapshot, tag, formIdentifier, token)) {
                return token;
            }
        }

        if (!allowHashedLastResort) {
            return null;
        }

        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (!isProbablyHashedCssClassToken(token)) {
                continue;
            }
            if (isUniqueClassTokenInContext(snapshot, tag, formIdentifier, token)) {
                return token;
            }
        }

        return null;
    }

    /**
     * Heuristic detector for likely-generated ("hashed") CSS class tokens produced by CSS-in-JS, CSS Modules and similar
     * toolchains.
     * <p>
     * The detector is intentionally conservative: false positives would exclude legitimate stable classes from locator
     * building. The goal is to avoid anchoring "stable" locators on volatile, build-generated class names.
     * <p>
     * Examples of patterns treated as likely-hashed (best-effort):
     * <ul>
     *   <li>Emotion-like: {@code css-<alnum>}</li>
     *   <li>styled-components-like: {@code sc-<alnum>}</li>
     *   <li>CSS Modules-like: token containing {@code __} with a hashed suffix (for example, {@code Button_root__3x9aF})</li>
     *   <li>underscore-prefixed short tokens (for example, {@code _3x9aF})</li>
     *   <li>hex-like tokens (length &ge; 8, contains digits)</li>
     * </ul>
     *
     * @param token single class token (not the full {@code class} attribute)
     * @return {@code true} if the token likely represents a generated/hash-like class name
     */
    private boolean isProbablyHashedCssClassToken(String token) {
        if (token == null) {
            return false;
        }
        String t = token.trim();
        if (t.isEmpty() || t.length() < 5) {
            return false;
        }
        if (t.startsWith("css-")) {
            String tail = t.substring(4);
            return tail.length() >= 5 && isAlphaNumOnly(tail);
        }
        if (t.startsWith("sc-")) {
            String tail = t.substring(3);
            if (tail.length() >= 5 && tail.length() <= 12 && isAlphaNumOnly(tail)) {
                boolean hasUpper = false;
                boolean hasLower = false;
                int digits = 0;
                for (int i = 0; i < tail.length(); i++) {
                    char c = tail.charAt(i);
                    if (Character.isDigit(c)) {
                        digits++;
                    } else if (Character.isUpperCase(c)) {
                        hasUpper = true;
                    } else if (Character.isLowerCase(c)) {
                        hasLower = true;
                    }
                }
                return (hasUpper && hasLower) || (digits >= 2);
            }
            return false;
        }
        int idx = Math.max(t.lastIndexOf("__"), t.lastIndexOf("--"));
        if (idx >= 0 && idx + 2 < t.length()) {
            String tail = t.substring(idx + 2);
            if (looksHashTail(tail)) {
                return true;
            }
        }
        if (t.charAt(0) == '_' && looksHashTail(t.substring(1))) {
            return true;
        }
        return isHexLike(t) && t.length() >= 8 && containsDigit(t);
    }

    private boolean isHexLike(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.length() < 6) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = Character.toLowerCase(t.charAt(i));
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private boolean looksHashTail(String tail) {
        if (tail == null) {
            return false;
        }
        String s = tail.trim();
        if (s.length() < 5) {
            return false;
        }

        int digits = 0;
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
            } else if (Character.isLetter(c)) {
                letters++;
            } else {
                return false;
            }
        }
        return digits >= 2 && letters >= 2;
    }

    private boolean isAlphaNumOnly(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsDigit(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isUniqueClassTokenInContext(
            CandidatesSnapshot snapshot,
            String tag,
            String formIdentifier,
            String classToken
    ) {
        if (classToken == null || classToken.isBlank()) {
            return false;
        }
        int count = 0;
        for (DomElementInfo d : snapshot.domCandidates) {
            if (d == null) {
                continue;
            }
            if (!tagEquals(tag, safe(d.getTagName()))) {
                continue;
            }
            if (!formMatches(formIdentifier, safe(d.getFormIdentifier()))) {
                continue;
            }
            if (hasClassToken(safe(d.getCssClasses()), classToken)) {
                count++;
                if (count > 1) {
                    return false;
                }
            }
        }
        return count == 1;
    }

    private boolean hasClassToken(String cssClasses, String token) {
        if (cssClasses == null || cssClasses.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        String[] tokens = cssClasses.trim().split("\\s+");
        for (String t : tokens) {
            if (token.equals(t)) {
                return true;
            }
        }
        return false;
    }

    // ----------- misc helpers -----------

    private String safeTagName(DomElementInfo info, WebElement element) {
        String tag = safe(info == null ? null : info.getTagName());
        if (!tag.isBlank()) {
            return tag;
        }
        try {
            String fromElement = element.getTagName();
            return safe(fromElement);
        } catch (Exception e) {
            return "div";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean tagEquals(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String xpathLiteral(String value) {
        if (value == null) {
            return "''";
        }
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }

        String[] parts = value.split("'", -1);
        StringBuilder sb = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", \"'\", ");
            }
            sb.append("'").append(parts[i]).append("'");
        }
        sb.append(")");
        return sb.toString();
    }

    private String cssAttrLiteral(String value) {
        String v = value == null ? "" : value;
        v = v.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + v + "'";
    }

    private String cssEscapeIdentifier(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean ok = Character.isLetterOrDigit(ch) || ch == '-' || ch == '_';
            if (ok) {
                sb.append(ch);
            } else {
                sb.append('\\').append(ch);
            }
        }
        return sb.toString();
    }

    // ----------- consistency check -----------

    private void runConsistencyCheck(
            String intentPhrase,
            IntentRole role,
            WebElement original,
            String xPath,
            String cssSelector
    ) {
        if (xPath == null || cssSelector == null) {
            throw new ElementSelectionException(
                    "Locator consistency check cannot run because xPath/cssSelector is null for intent '" +
                            intentPhrase + "', role " + role
            );
        }

        if (isGenericLocator(original, xPath, cssSelector)) {
            return;
        }

        try {
            WebElement byXPath = driver.findElement(By.xpath(xPath));
            WebElement byCss = driver.findElement(By.cssSelector(cssSelector));

            boolean xpathMatchesOriginal = original.equals(byXPath);
            boolean cssMatchesOriginal = original.equals(byCss);

            if (!xpathMatchesOriginal || !cssMatchesOriginal) {
                throw new ElementSelectionException(
                        "Locator consistency check failed for intent '" + intentPhrase + "', role " + role +
                                ". XPath/CSS do not resolve to the same DOM element as the original: " +
                                "xpathMatchesOriginal=" + xpathMatchesOriginal +
                                ", cssMatchesOriginal=" + cssMatchesOriginal +
                                ", xpath='" + xPath + "', css='" + cssSelector + '\''
                );
            }
        } catch (NoSuchElementException e) {
            throw new ElementSelectionException(
                    "Locator consistency check failed for intent '" + intentPhrase + "', role " + role +
                            ". Re-resolving element by XPath/CSS failed: " + e.getMessage()
            );
        }
    }

    private boolean isGenericLocator(WebElement element, String xPath, String cssSelector) {
        String tag = element.getTagName();
        boolean xpathGeneric = ("//" + tag).equals(xPath);
        boolean cssGeneric = tag.equals(cssSelector);
        return xpathGeneric || cssGeneric;
    }
}
