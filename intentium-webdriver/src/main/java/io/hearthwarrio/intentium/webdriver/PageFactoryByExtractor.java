package io.hearthwarrio.intentium.webdriver;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Best-effort extractor of the underlying {@link By} locator from a PageFactory proxy element.
 * <p>
 * This intentionally uses reflection to avoid relying on Selenium internal classes,
 * which may change between versions.
 * <p>
 * If extraction fails for any reason, this class returns {@code null}.
 */
final class PageFactoryByExtractor {

    private static final int MAX_DEPTH = 6;

    private PageFactoryByExtractor() {
    }

    static By tryExtractBy(WebElement element) {
        try {
            if (element == null) {
                return null;
            }
            Class<?> c = element.getClass();
            if (!Proxy.isProxyClass(c)) {
                return null;
            }

            Object handler = Proxy.getInvocationHandler(element);
            if (handler == null) {
                return null;
            }

            return findByRecursive(handler, 0, new IdentityHashMap<>());
        } catch (Throwable t) {
            return null;
        }
    }

    private static By findByRecursive(Object obj, int depth, Map<Object, Boolean> visited) {
        if (obj == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            return null;
        }
        if (visited.put(obj, Boolean.TRUE) != null) {
            return null;
        }

        if (obj instanceof By) {
            return (By) obj;
        }

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v == null) {
                        continue;
                    }
                    if (v instanceof By) {
                        return (By) v;
                    }

                    Package p = v.getClass().getPackage();
                    String pn = p == null ? "" : p.getName();
                    if (pn.startsWith("java.") || pn.startsWith("javax.") || pn.startsWith("jdk.")) {
                        continue;
                    }

                    By nested = findByRecursive(v, depth + 1, visited);
                    if (nested != null) {
                        return nested;
                    }
                } catch (Throwable ignored) {
                    // best-effort
                }
            }
            cls = cls.getSuperclass();
        }

        return null;
    }
}
