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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class FakeplayerTranslationModule extends AbstractModule {

    private final String baseName;
    private final Locale defaultLocale;

    public FakeplayerTranslationModule(
            @NotNull String baseName,
            @NotNull Locale defaultLocale
    ) {
        this.baseName = baseName;
        this.defaultLocale = defaultLocale;
    }

    @Provides
    @Singleton
    public @NotNull FakeplayerTranslator translator(@NotNull Plugin plugin) {
        var translator = new FakeplayerTranslator(plugin, this.baseName, this.defaultLocale);
        translator.register();
        return translator;
    }

}