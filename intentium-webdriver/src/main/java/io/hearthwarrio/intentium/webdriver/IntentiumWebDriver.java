package io.hearthwarrio.intentium.webdriver;

import io.hearthwarrio.intentium.core.*;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.*;

/**
 * High-level Intentium entry point for Selenium WebDriver.
 */
public class IntentiumWebDriver {

    private final WebDriver driver;
    private final Language language;
    private final IntentResolver intentResolver;
    private final ElementSelector elementSelector;
    private final WebDriverDomMapper domMapper;

    /**
     * Mutable to support runtime overrides and DSL sugar.
     */
    private ResolvedElementLogger resolvedElementLogger;

    private boolean consistencyCheckEnabled = false;

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
    }

    private final LastLocatorsCache lastLocatorsCache = new LastLocatorsCache();

    public IntentiumWebDriver(WebDriver driver, Language language) {
        this(driver, language, new DefaultIntentResolver(), new DefaultElementSelector(), null);
    }

    public IntentiumWebDriver(WebDriver driver, Language language, ResolvedElementLogger logger) {
        this(driver, language, new DefaultIntentResolver(), new DefaultElementSelector(), logger);
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
        this.domMapper = new WebDriverDomMapper(driver);
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

    public WebElement findElement(String intentPhrase) {
        return resolveIntent(intentPhrase, false).element;
    }

    public void click(String intentPhrase) {
        resolveIntent(intentPhrase, false).element.click();
    }

    public void sendKeys(String intentPhrase, CharSequence... keys) {
        resolveIntent(intentPhrase, false).element.sendKeys(keys);
    }

    /**
     * p.4: uses last-resolve cache to avoid second DOM pass if getCssSelector() follows.
     */
    public String getXPath(String intentPhrase) {
        ResolvedElement r = resolveIntent(intentPhrase, true);
        return r.xPath;
    }

    /**
     * p.4: uses last-resolve cache to avoid second DOM pass if getXPath() was already called.
     */
    public String getCssSelector(String intentPhrase) {
        ResolvedElement r = resolveIntent(intentPhrase, true);
        return r.cssSelector;
    }

    /** Starts a single-intent action helper. */
    public SingleIntentAction into(String intentPhrase) {
        return new SingleIntentAction(this, intentPhrase);
    }

    /** Alias for {@link #into(String)}. */
    public SingleIntentAction at(String intentPhrase) {
        return into(intentPhrase);
    }

// ----------- PageObject / manual locator bridge -----------

    /**
     * Resolve an element using a manual Selenium {@link By} locator.
     * <p>
     * Useful as a bridge for existing PageObjects: you can keep your locators,
     * but still use Intentium DSL, logging and optional consistency checks.
     */
    public WebElement findElement(By by) {
        return resolveBy(by, false).element;
    }

    public void click(By by) {
        resolveBy(by, false).element.click();
    }

    public void sendKeys(By by, CharSequence... keys) {
        resolveBy(by, false).element.sendKeys(keys);
    }

    public String getXPath(By by) {
        return resolveBy(by, true).xPath;
    }

    public String getCssSelector(By by) {
        return resolveBy(by, true).cssSelector;
    }

    public SingleTargetAction into(By by) {
        return new SingleTargetAction(this, by);
    }

    public SingleTargetAction at(By by) {
        return into(by);
    }

    /**
     * Resolve / act on an existing {@link WebElement} reference (e.g. from PageFactory).
     * <p>
     * Best-effort behavior:
     * - If the element is a PageFactory proxy and Intentium can extract the underlying {@link By},
     *   Intentium will prefer that {@link By} for logging and consistency checks.
     * - Otherwise Intentium will operate on the provided element and build derived XPath/CSS.
     */
    public WebElement findElement(WebElement element) {
        return resolveWebElement(element, false).element;
    }

    public void click(WebElement element) {
        resolveWebElement(element, false).element.click();
    }

    public void sendKeys(WebElement element, CharSequence... keys) {
        resolveWebElement(element, false).element.sendKeys(keys);
    }

    public String getXPath(WebElement element) {
        return resolveWebElement(element, true).xPath;
    }

    public String getCssSelector(WebElement element) {
        return resolveWebElement(element, true).cssSelector;
    }

    public SingleTargetAction into(WebElement element) {
        return new SingleTargetAction(this, element);
    }

    public SingleTargetAction at(WebElement element) {
        return into(element);
    }

    /** Starts an action chain DSL. */
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
        return lastLocatorsCache.resolved;
    }

    private void storeLastLocators(String intentPhrase, ResolvedElement resolved) {
        lastLocatorsCache.url = currentUrl();
        lastLocatorsCache.intentPhrase = intentPhrase;
        lastLocatorsCache.loggerAtResolve = resolvedElementLogger;
        lastLocatorsCache.consistencyAtResolve = consistencyCheckEnabled;
        lastLocatorsCache.resolved = resolved;
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

    String buildStableXPath(DomElementInfo info, WebElement element, CandidatesSnapshot snapshot) {
        String tag = safeTagName(info, element);

        FormContext form = resolveFormContext(element);
        String formKey = safe(form.identifier());

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "//*[@id=" + xpathLiteral(id) + "]";
        }

        // test/qa – immediately after id
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

        // Pass 1 – ONLY non-hashed unique class token
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
            return xPathWithFormPrefix(form, "(" + base + ")[" + ordinal + "]");
        }

        // Pass 2 (last resort) – allow hashed unique class token ONLY when otherwise we'd return //tag
        String hashedClassToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses, true);
        if (hashedClassToken != null) {
            String classPredicate = "contains(concat(' ', normalize-space(@class), ' '), " +
                    xpathLiteral(" " + hashedClassToken + " ") + ")";
            return xPathWithFormPrefix(form, "//" + tag + "[" + classPredicate + "]");
        }

        return xPathWithFormPrefix(form, "//" + tag);
    }


    String buildStableCssSelector(DomElementInfo info, WebElement element, CandidatesSnapshot snapshot) {
        String tag = safeTagName(info, element);

        FormContext form = resolveFormContext(element);
        String formKey = safe(form.identifier());

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "#" + cssEscapeIdentifier(id);
        }

        // test/qa – immediately after id
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

        // Pass 1 – ONLY non-hashed unique class token
        String classToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses, false);
        if (classToken != null) {
            return cssWithFormPrefix(form, tag + "." + cssEscapeIdentifier(classToken));
        }

        // try type uniqueness before allowing hashed
        if (!type.isBlank() && isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getType, type)) {
            return cssWithFormPrefix(form, tag + "[type=" + cssAttrLiteral(type) + "]");
        }

        // Pass 2 (last resort) – allow hashed unique class token ONLY when otherwise we'd return tag
        String hashedClassToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses, true);
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
        // default behavior preserved for any internal callers
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

        // Pass 1: prefer unique non-hashed tokens
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

        // Pass 2: allow unique hashed tokens only when explicitly permitted
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

    private boolean isProbablyHashedCssClassToken(String token) {
        if (token == null) {
            return false;
        }
        String t = token.trim();
        if (t.isEmpty() || t.length() < 5) {
            return false;
        }

        // Emotion / similar: css-<alnum...> (жёстко)
        if (t.startsWith("css-")) {
            String tail = t.substring(4);
            return tail.length() >= 5 && isAlphaNumOnly(tail);
        }

        // styled-components: sc-AxhCb / sc-hKMtZM (часто смешанный регистр и/или цифры)
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

        // CSS Modules / similar: Button_root__3x9aF, block--a1B2c3
        int idx = Math.max(t.lastIndexOf("__"), t.lastIndexOf("--"));
        if (idx >= 0 && idx + 2 < t.length()) {
            String tail = t.substring(idx + 2);
            if (looksHashTail(tail)) {
                return true;
            }
        }

        // Часто: _3x9aF / _a1B2c3D4
        if (t.charAt(0) == '_' && looksHashTail(t.substring(1))) {
            return true;
        }

        // Чистый hex-токен: hashed только если >= 8 и есть цифра
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
