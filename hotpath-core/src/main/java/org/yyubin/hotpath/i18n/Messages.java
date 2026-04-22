package org.yyubin.hotpath.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Thin wrapper around {@link ResourceBundle} for parameterised message lookup.
 *
 * <p>To add a new language, create {@code messages_<lang>.properties} (UTF-8)
 * on the classpath root (e.g. {@code messages_ja.properties}) and pass
 * {@code --lang ja} on the command line.</p>
 */
public class Messages {

    private static final String BASE_NAME    = "messages";
    private static final String FALLBACK_TAG = "ko";

    private final ResourceBundle bundle;
    private final String         langTag;

    private Messages(ResourceBundle bundle, String langTag) {
        this.bundle  = bundle;
        this.langTag = langTag;
    }

    /**
     * Loads the message bundle for the given IETF language tag (e.g. {@code "ko"}, {@code "en"}).
     * Falls back to Korean if no matching bundle is found.
     */
    public static Messages of(String langTag) {
        Locale locale = Locale.forLanguageTag(langTag);
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BASE_NAME, locale, Utf8Control.INSTANCE);
            String resolved = bundle.getLocale().getLanguage();
            if (!resolved.isEmpty() && !resolved.equals(locale.getLanguage())) {
                throw new MissingResourceException("No bundle for language", BASE_NAME, langTag);
            }
            return new Messages(bundle, langTag);
        } catch (MissingResourceException e) {
            Locale fallback = Locale.forLanguageTag(FALLBACK_TAG);
            ResourceBundle bundle = ResourceBundle.getBundle(BASE_NAME, fallback, Utf8Control.INSTANCE);
            return new Messages(bundle, FALLBACK_TAG);
        }
    }

    /** Returns the resolved IETF language tag (e.g. {@code "ko"}, {@code "en"}). */
    public String langTag() {
        return langTag;
    }

    /** Returns the raw message string for the given key. */
    public String get(String key) {
        return bundle.getString(key);
    }

    /**
     * Returns the message for {@code key} formatted with {@link String#format}.
     * Format specifiers follow {@code printf} conventions (e.g. {@code %s}, {@code %.1f}).
     */
    public String format(String key, Object... args) {
        return String.format(bundle.getString(key), args);
    }

    // -------------------------------------------------------------------------

    /** ResourceBundle.Control that reads .properties files as UTF-8. */
    private static final class Utf8Control extends ResourceBundle.Control {

        static final Utf8Control INSTANCE = new Utf8Control();

        @Override
        public List<String> getFormats(String baseName) {
            return FORMAT_PROPERTIES;
        }

        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload)
                throws IOException {
            String bundleName    = toBundleName(baseName, locale);
            String resourceName  = toResourceName(bundleName, "properties");
            URL    url           = loader.getResource(resourceName);
            if (url == null) return null;

            URLConnection conn = url.openConnection();
            if (reload) conn.setUseCaches(false);

            try (InputStream is = conn.getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }
}
