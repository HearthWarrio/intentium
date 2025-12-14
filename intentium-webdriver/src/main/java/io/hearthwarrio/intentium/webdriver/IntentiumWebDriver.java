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
 * Responsibilities:
 * <ul>
 *   <li>Resolve a human intent phrase to an {@link IntentRole}</li>
 *   <li>Collect DOM candidates via {@link WebDriverDomMapper}</li>
 *   <li>Select the best candidate via {@link ElementSelector}</li>
 *   <li>Optionally log XPath/CSS via {@link ResolvedElementLogger}</li>
 *   <li>Optionally verify XPath/CSS consistency</li>
 * </ul>
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
     * This avoids accidental candidate de-duplication when {@link DomElementInfo#equals(Object)} matches.
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
     * Internal resolved element data container used for caching within a chain.
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

    public String getXPath(String intentPhrase) {
        ResolvedElement r = resolveIntent(intentPhrase, true);
        return r.xPath;
    }

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
        CandidatesSnapshot snapshot = collectCandidatesSnapshot();
        if (snapshot.domCandidates.isEmpty()) {
            throw new ElementSelectionException("No candidates found on page");
        }
        return resolveIntent(intentPhrase, snapshot, forceLocators);
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

    // ----------- locator builders (v0.1+) -----------

    String buildStableXPath(DomElementInfo info, WebElement element, CandidatesSnapshot snapshot) {
        String tag = safeTagName(info, element);

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "//*[@id=" + xpathLiteral(id) + "]";
        }

        String name = safe(info == null ? null : info.getName());
        if (isUnique(snapshot, tag, d -> d.getName(), name)) {
            return "//" + tag + "[@name=" + xpathLiteral(name) + "]";
        }

        String aria = safe(info == null ? null : info.getAriaLabel());
        if (isUnique(snapshot, tag, d -> d.getAriaLabel(), aria)) {
            return "//" + tag + "[@aria-label=" + xpathLiteral(aria) + "]";
        }

        String placeholder = safe(info == null ? null : info.getPlaceholder());
        if (isUnique(snapshot, tag, d -> d.getPlaceholder(), placeholder)) {
            return "//" + tag + "[@placeholder=" + xpathLiteral(placeholder) + "]";
        }

        String title = safe(info == null ? null : info.getTitle());
        if (isUnique(snapshot, tag, d -> d.getTitle(), title)) {
            return "//" + tag + "[@title=" + xpathLiteral(title) + "]";
        }

        String type = safe(info == null ? null : info.getType());

        if (isUnique(snapshot, tag, d -> d.getName(), name, d -> d.getType(), type)) {
            return "//" + tag + "[@name=" + xpathLiteral(name) + " and @type=" + xpathLiteral(type) + "]";
        }

        if (isUnique(snapshot, tag, d -> d.getAriaLabel(), aria, d -> d.getType(), type)) {
            return "//" + tag + "[@aria-label=" + xpathLiteral(aria) + " and @type=" + xpathLiteral(type) + "]";
        }

        if (isUnique(snapshot, tag, d -> d.getPlaceholder(), placeholder, d -> d.getType(), type)) {
            return "//" + tag + "[@placeholder=" + xpathLiteral(placeholder) + " and @type=" + xpathLiteral(type) + "]";
        }

        if (isUnique(snapshot, tag, d -> d.getTitle(), title, d -> d.getType(), type)) {
            return "//" + tag + "[@title=" + xpathLiteral(title) + " and @type=" + xpathLiteral(type) + "]";
        }

        return "//" + tag;
    }

    String buildStableCssSelector(DomElementInfo info, WebElement element, CandidatesSnapshot snapshot) {
        String tag = safeTagName(info, element);

        String id = safe(info == null ? null : info.getId());
        if (!id.isBlank()) {
            return "#" + cssEscapeIdentifier(id);
        }

        String name = safe(info == null ? null : info.getName());
        if (isUnique(snapshot, tag, d -> d.getName(), name)) {
            return tag + "[name=" + cssAttrLiteral(name) + "]";
        }

        String aria = safe(info == null ? null : info.getAriaLabel());
        if (isUnique(snapshot, tag, d -> d.getAriaLabel(), aria)) {
            return tag + "[aria-label=" + cssAttrLiteral(aria) + "]";
        }

        String placeholder = safe(info == null ? null : info.getPlaceholder());
        if (isUnique(snapshot, tag, d -> d.getPlaceholder(), placeholder)) {
            return tag + "[placeholder=" + cssAttrLiteral(placeholder) + "]";
        }

        String title = safe(info == null ? null : info.getTitle());
        if (isUnique(snapshot, tag, d -> d.getTitle(), title)) {
            return tag + "[title=" + cssAttrLiteral(title) + "]";
        }

        String type = safe(info == null ? null : info.getType());

        if (isUnique(snapshot, tag, d -> d.getName(), name, d -> d.getType(), type)) {
            return tag + "[name=" + cssAttrLiteral(name) + "][type=" + cssAttrLiteral(type) + "]";
        }

        if (isUnique(snapshot, tag, d -> d.getAriaLabel(), aria, d -> d.getType(), type)) {
            return tag + "[aria-label=" + cssAttrLiteral(aria) + "][type=" + cssAttrLiteral(type) + "]";
        }

        if (isUnique(snapshot, tag, d -> d.getPlaceholder(), placeholder, d -> d.getType(), type)) {
            return tag + "[placeholder=" + cssAttrLiteral(placeholder) + "][type=" + cssAttrLiteral(type) + "]";
        }

        if (isUnique(snapshot, tag, d -> d.getTitle(), title, d -> d.getType(), type)) {
            return tag + "[title=" + cssAttrLiteral(title) + "][type=" + cssAttrLiteral(type) + "]";
        }

        return tag;
    }

    // Legacy builders (kept)

    String buildSimpleXPath(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) {
            return "//*[@id=" + xpathLiteral(id) + "]";
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return "//" + element.getTagName() + "[@name=" + xpathLiteral(name) + "]";
        }

        return "//" + element.getTagName();
    }

    String buildSimpleCssSelector(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) {
            return "#" + cssEscapeIdentifier(id);
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return element.getTagName() + "[name=" + cssAttrLiteral(name) + "]";
        }

        return element.getTagName();
    }

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

    private interface StrGetter {
        String get(DomElementInfo info);
    }

    private boolean isUnique(CandidatesSnapshot snapshot, String tag, StrGetter getter, String value) {
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
            String v = getter.get(d);
            if (value.equals(v)) {
                count++;
                if (count > 1) {
                    return false;
                }
            }
        }
        return count == 1;
    }

    private boolean isUnique(
            CandidatesSnapshot snapshot,
            String tag,
            StrGetter getter1,
            String value1,
            StrGetter getter2,
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
            if (value1.equals(getter1.get(d)) && value2.equals(getter2.get(d))) {
                count++;
                if (count > 1) {
                    return false;
                }
            }
        }
        return count == 1;
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
