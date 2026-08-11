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
package io.github.hello09x.fakeplayer.core.compat;

import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.UUID;

public final class PacketEventsCompat {

    private static final String PACKET_EVENTS_CLASS = "com.github.retrooper.packetevents.PacketEvents";

    private PacketEventsCompat() {
    }

    public static void registerFakeChannel(@NotNull UUID uuid, @Nullable Object channel) {
        if (channel == null) {
            return;
        }
        invokeProtocolManager("setChannel", new Class<?>[]{UUID.class, Object.class}, uuid, channel);
    }

    public static void unregisterFakeChannel(@NotNull UUID uuid) {
        invokeProtocolManager("removeChannelById", new Class<?>[]{UUID.class}, uuid);
    }

    private static void invokeProtocolManager(
            @NotNull String method,
            @NotNull Class<?>[] parameterTypes,
            @NotNull Object... arguments
    ) {
        var plugin = findPacketEvents();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        try {
            var packetEvents = plugin.getClass().getClassLoader().loadClass(PACKET_EVENTS_CLASS);
            var api = packetEvents.getMethod("getAPI").invoke(null);
            if (api == null) {
                return;
            }
            var protocolManager = api.getClass().getMethod("getProtocolManager").invoke(api);
            protocolManager.getClass().getMethod(method, parameterTypes).invoke(protocolManager, arguments);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            Main.getInstance().getLogger().warning("PacketEvents compatibility call failed: " + e);
        } catch (InvocationTargetException e) {
            Main.getInstance().getLogger().warning("PacketEvents compatibility call failed: " + e.getCause());
        }
    }

    private static @Nullable Plugin findPacketEvents() {
        return Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(plugin -> plugin.getName().equalsIgnoreCase("packetevents"))
                .findFirst()
                .orElse(null);
    }
}
