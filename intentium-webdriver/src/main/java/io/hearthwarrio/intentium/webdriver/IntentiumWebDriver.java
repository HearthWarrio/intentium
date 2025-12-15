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
     * p.4: uses last-resolve cache to avoid second DOM pass if getXPath() preceded.
     */
    public String getCssSelector(String intentPhrase) {
        ResolvedElement r = resolveIntent(intentPhrase, true);
        return r.cssSelector;
    }

    public SingleIntentAction into(String intentPhrase) {
        return new SingleIntentAction(this, intentPhrase);
    }

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

        boolean needLocators = forceLocators || resolvedElementLogger != null || consistencyCheckEnabled;

        String xPath = null;
        String css = null;

        if (needLocators) {
            xPath = buildStableXPath(elementInfo, webElement, snapshot);
            css = buildStableCssSelector(elementInfo, webElement, snapshot);
        }

        if (resolvedElementLogger != null) {
            resolvedElementLogger.logResolvedElement(intentPhrase, role, xPath, css, elementInfo);
        }

        if (consistencyCheckEnabled) {
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

    // ----------- locator builders (P.3 hardened) -----------

    String buildStableXPath(DomElementInfo info, WebElement element, CandidatesSnapshot snapshot) {
        String tag = safeTagName(info, element);

        FormContext form = resolveFormContext(element);
        String formKey = safe(form.identifier());

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "//*[@id=" + xpathLiteral(id) + "]";
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

        String classToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses);
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

        String classToken = pickUniqueClassToken(snapshot, tag, formKey, cssClasses);
        if (classToken != null) {
            return cssWithFormPrefix(form, tag + "." + cssEscapeIdentifier(classToken));
        }

        if (!type.isBlank() && isUniqueInContext(snapshot, tag, formKey, DomElementInfo::getType, type)) {
            return cssWithFormPrefix(form, tag + "[type=" + cssAttrLiteral(type) + "]");
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
            if (isUniqueClassTokenInContext(snapshot, tag, formIdentifier, token)) {
                return token;
            }
        }
        return null;
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
