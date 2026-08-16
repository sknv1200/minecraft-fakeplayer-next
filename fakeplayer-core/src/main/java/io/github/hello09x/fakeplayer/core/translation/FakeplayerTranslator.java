/*
 * Copyright 2026 yigemingzii
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.hello09x.fakeplayer.core.translation;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;

public final class FakeplayerTranslator {

    private final Plugin plugin;
    private final String baseName;
    private final Locale defaultLocale;
    private final ClassLoader dataFolderLoader;
    private final ClassLoader jarLoader;
    private final Set<Locale> loadedLocales = new HashSet<>();
    private final Map<String, Map<Locale, MessageFormat>> translations = new HashMap<>();

    private Object globalSource;

    private static final ResourceBundle.Control UTF8_CONTROL = new ResourceBundle.Control() {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            // Do not let the JVM default locale replace the requested locale. If a
            // localized bundle is unavailable, ResourceBundle should use the root
            // (English) bundle.
            return null;
        }

        @Override
        public ResourceBundle newBundle(
                String baseName,
                Locale locale,
                String format,
                ClassLoader loader,
                boolean reload
        ) throws IllegalAccessException, InstantiationException, IOException {
            if (!"java.properties".equals(format)) {
                return super.newBundle(baseName, locale, format, loader, reload);
            }

            var resourceName = toResourceName(toBundleName(baseName, locale), "properties");
            var stream = reload ? null : loader.getResourceAsStream(resourceName);
            if (reload) {
                var resource = loader.getResource(resourceName);
                if (resource != null) {
                    URLConnection connection = resource.openConnection();
                    connection.setUseCaches(false);
                    stream = connection.getInputStream();
                }
            }
            if (stream == null) {
                return null;
            }

            var input = stream;
            try (input; var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    };

    public FakeplayerTranslator(
            @NotNull Plugin plugin,
            @NotNull String baseName,
            @NotNull Locale defaultLocale
    ) {
        this.plugin = plugin;
        this.baseName = baseName;
        this.defaultLocale = defaultLocale;
        this.jarLoader = plugin.getClass().getClassLoader();
        try {
            this.dataFolderLoader = new URLClassLoader(new URL[]{plugin.getDataFolder().toURI().toURL()});
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Failed to create translation class loader", e);
        }
    }

    public synchronized void register() {
        if (this.globalSource != null) {
            return;
        }

        try {
            var loader = adventureClassLoader();
            var translatorClass = Class.forName("net.kyori.adventure.translation.Translator", false, loader);
            var keyClass = Class.forName("net.kyori.adventure.key.Key", false, loader);
            var triStateClass = Class.forName("net.kyori.adventure.util.TriState", false, loader);
            var key = keyClass
                    .getMethod("key", String.class, String.class)
                    .invoke(null, this.plugin.getName().toLowerCase(Locale.ROOT), "translator");

            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "name" -> {
                        return key;
                    }
                    case "translate" -> {
                        if (args == null || args.length != 2) {
                            return null;
                        }
                        if (args[0] instanceof String translationKey) {
                            return this.translate(translationKey, (Locale) args[1]);
                        }
                        return null;
                    }
                    case "canTranslate" -> {
                        return args != null && args.length == 2
                                && this.canTranslate((String) args[0], (Locale) args[1]);
                    }
                    case "hasAnyTranslations" -> {
                        // Locales are loaded on demand, so an empty cache does not mean that
                        // this source has no translations.
                        return notSet(triStateClass);
                    }
                    case "toString" -> {
                        return "FakeplayerTranslator[" + this.plugin.getName() + "]";
                    }
                    case "hashCode" -> {
                        return System.identityHashCode(proxy);
                    }
                    case "equals" -> {
                        return args != null && args.length == 1 && proxy == args[0];
                    }
                    default -> {
                        return null;
                    }
                }
            };

            var source = Proxy.newProxyInstance(loader, new Class<?>[]{translatorClass}, handler);
            var globalTranslatorClass = Class.forName(
                    "net.kyori.adventure.translation.GlobalTranslator",
                    false,
                    loader
            );
            var globalTranslator = globalTranslatorClass.getMethod("translator").invoke(null);
            globalTranslatorClass
                    .getMethod("addSource", translatorClass)
                    .invoke(globalTranslator, source);
            this.globalSource = source;
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Failed to register translator with Adventure", e);
        }
    }

    public synchronized void reload() {
        this.translations.clear();
        this.loadedLocales.clear();
        ResourceBundle.clearCache(this.dataFolderLoader);
        ResourceBundle.clearCache(this.jarLoader);
    }

    public synchronized void unregister() {
        if (this.globalSource == null) {
            return;
        }

        try {
            var loader = adventureClassLoader();
            var translatorClass = Class.forName("net.kyori.adventure.translation.Translator", false, loader);
            var globalTranslatorClass = Class.forName(
                    "net.kyori.adventure.translation.GlobalTranslator",
                    false,
                    loader
            );
            var globalTranslator = globalTranslatorClass.getMethod("translator").invoke(null);
            globalTranslatorClass
                    .getMethod("removeSource", translatorClass)
                    .invoke(globalTranslator, this.globalSource);
            this.globalSource = null;
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Failed to unregister translator from Adventure", e);
        }
    }

    private synchronized @Nullable MessageFormat translate(
            @NotNull String key,
            @Nullable Locale locale
    ) {
        var effective = locale == null ? this.defaultLocale : locale;
        this.loadLocale(effective);
        var format = this.findTranslation(key, effective);
        if (format == null && !effective.equals(this.defaultLocale)) {
            this.loadLocale(this.defaultLocale);
            format = this.findTranslation(key, this.defaultLocale);
        }
        return format;
    }

    private synchronized boolean canTranslate(
            @NotNull String key,
            @Nullable Locale locale
    ) {
        return this.translate(key, locale) != null;
    }

    private void loadLocale(@NotNull Locale locale) {
        if (this.loadedLocales.contains(locale)) {
            return;
        }

        try {
            for (var classLoader : List.of(this.dataFolderLoader, this.jarLoader)) {
                try {
                    var bundle = ResourceBundle.getBundle(
                            this.baseName,
                            locale,
                            classLoader,
                            UTF8_CONTROL
                    );
                    for (var bundleKey : bundle.keySet()) {
                        this.translations
                                .computeIfAbsent(bundleKey, ignored -> new HashMap<>())
                                .put(locale, new MessageFormat(bundle.getString(bundleKey), locale));
                    }
                    return;
                } catch (MissingResourceException ignored) {
                    // Try the next class loader.
                }
            }
        } finally {
            this.loadedLocales.add(locale);
        }
    }

    private @Nullable MessageFormat findTranslation(@NotNull String key, @NotNull Locale locale) {
        var byLocale = this.translations.get(key);
        return byLocale == null ? null : byLocale.get(locale);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @NotNull Object notSet(@NotNull Class<?> triStateClass) {
        return Enum.valueOf((Class) triStateClass, "NOT_SET");
    }

    private static @NotNull ClassLoader adventureClassLoader() {
        var serverLoader = Bukkit.class.getClassLoader();
        if (serverLoader != null
                && canLoad(serverLoader, "net.kyori.adventure.translation.Translator")
                && canLoad(serverLoader, "net.kyori.adventure.translation.GlobalTranslator")) {
            return serverLoader;
        }
        throw new IllegalStateException("Adventure is not available from the server class loader");
    }

    private static boolean canLoad(@NotNull ClassLoader classLoader, @NotNull String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

}
