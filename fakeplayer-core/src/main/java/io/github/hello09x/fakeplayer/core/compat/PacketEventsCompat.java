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
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PacketEventsCompat {

    private static final String PACKET_EVENTS_CLASS = "com.github.retrooper.packetevents.PacketEvents";
    private static volatile boolean registrationLogged;

    private PacketEventsCompat() {
    }

    public static void registerFakeChannel(@NotNull UUID uuid, @Nullable Object channel) {
        if (channel == null) {
            return;
        }

        var discovery = discoverProtocolManagers();
        if (discovery.targets().isEmpty()) {
            if (discovery.packetEventsDetected()) {
                warning("Detected PacketEvents listeners, but could not access their ProtocolManager instances");
            }
            return;
        }

        var changed = 0;
        var confirmed = 0;
        for (var target : discovery.targets()) {
            try {
                var protocolManager = target.protocolManager();
                var existing = invoke(protocolManager, "getChannel", new Class<?>[]{UUID.class}, uuid);
                if (existing != channel) {
                    invoke(protocolManager, "setChannel", new Class<?>[]{UUID.class, Object.class}, uuid, channel);
                    changed++;
                }
                if (invoke(protocolManager, "getChannel", new Class<?>[]{UUID.class}, uuid) == channel) {
                    confirmed++;
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                warning("PacketEvents fake-channel registration failed: " + causeOf(e));
            }
        }

        var total = discovery.targets().size();
        if (!registrationLogged || confirmed != total) {
            var message = "PacketEvents fake channel for %s: found %d copies, updated %d, confirmed %d"
                    .formatted(uuid, total, changed, confirmed);
            if (confirmed == total) {
                Main.getInstance().getLogger().info(message);
                registrationLogged = true;
            } else {
                warning(message);
            }
        }
    }

    public static void unregisterFakeChannel(@NotNull UUID uuid) {
        for (var target : discoverProtocolManagers().targets()) {
            try {
                invoke(target.protocolManager(), "removeChannelById", new Class<?>[]{UUID.class}, uuid);
            } catch (ReflectiveOperationException | LinkageError e) {
                warning("PacketEvents fake-channel cleanup failed: " + causeOf(e));
            }
        }
    }

    private static @NotNull Discovery discoverProtocolManagers() {
        var sources = new ArrayList<PacketEventsSource>();
        var detected = collectPacketEventsListeners(sources, PlayerJoinEvent.getHandlerList());
        detected |= collectPacketEventsListeners(sources, PlayerLoginEvent.getHandlerList());

        for (var plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().equalsIgnoreCase("packetevents") && plugin.isEnabled()) {
                detected = true;
                sources.add(new PacketEventsSource(
                        plugin.getClass().getClassLoader(),
                        List.of(PACKET_EVENTS_CLASS)
                ));
            }
        }

        var targets = new IdentityHashMap<Object, ProtocolTarget>();
        for (var source : sources) {
            for (var packetEventsClass : source.packetEventsClasses()) {
                var target = loadProtocolTarget(source.classLoader(), packetEventsClass);
                if (target != null) {
                    targets.putIfAbsent(target.protocolManager(), target);
                }
            }
        }
        return new Discovery(List.copyOf(targets.values()), detected);
    }

    private static boolean collectPacketEventsListeners(
            @NotNull List<PacketEventsSource> sources,
            @NotNull HandlerList handlerList
    ) {
        var detected = false;
        for (RegisteredListener registered : handlerList.getRegisteredListeners()) {
            var listenerClass = registered.getListener().getClass();
            var className = listenerClass.getName();
            if (!className.toLowerCase(Locale.ROOT).contains("packetevents")) {
                continue;
            }

            detected = true;
            sources.add(new PacketEventsSource(
                    listenerClass.getClassLoader(),
                    packetEventsClassCandidates(className)
            ));
        }
        return detected;
    }

    private static @NotNull List<String> packetEventsClassCandidates(@NotNull String listenerClassName) {
        var candidates = new LinkedHashSet<String>();
        candidates.add(PACKET_EVENTS_CLASS);

        var bukkitIndex = listenerClassName.indexOf(".bukkit.");
        if (bukkitIndex > 0) {
            var implementationRoot = listenerClassName.substring(0, bukkitIndex);
            addPacketEventsClass(candidates, implementationRoot.replace(
                    "io.github.retrooper",
                    "com.github.retrooper"
            ));
            addPacketEventsClass(candidates, implementationRoot);

            var lastDot = implementationRoot.lastIndexOf('.');
            if (lastDot > 0) {
                var base = implementationRoot.substring(0, lastDot);
                addPacketEventsClass(candidates, base);
                addPacketEventsClass(candidates, base + ".api");
            }
        }

        var implementationPackage = "io.github.retrooper.packetevents";
        var packageIndex = listenerClassName.indexOf(implementationPackage);
        if (packageIndex >= 0) {
            var relocationPrefix = listenerClassName.substring(0, packageIndex);
            candidates.add(relocationPrefix + PACKET_EVENTS_CLASS);
        }
        return List.copyOf(candidates);
    }

    private static void addPacketEventsClass(@NotNull Set<String> candidates, @NotNull String root) {
        candidates.add(root + ".PacketEvents");
    }

    private static @Nullable ProtocolTarget loadProtocolTarget(
            @NotNull ClassLoader classLoader,
            @NotNull String packetEventsClassName
    ) {
        try {
            var packetEventsClass = Class.forName(packetEventsClassName, false, classLoader);
            var api = invoke(null, packetEventsClass, "getAPI", new Class<?>[0]);
            if (api == null) {
                return null;
            }
            var protocolManager = invoke(api, "getProtocolManager", new Class<?>[0]);
            if (protocolManager == null) {
                return null;
            }
            return new ProtocolTarget(protocolManager);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException | LinkageError e) {
            warning("Could not access " + packetEventsClassName + ": " + causeOf(e));
            return null;
        }
    }

    private static @Nullable Object invoke(
            @Nullable Object target,
            @NotNull String methodName,
            @NotNull Class<?>[] parameterTypes,
            @NotNull Object... arguments
    ) throws ReflectiveOperationException {
        return invoke(target, target.getClass(), methodName, parameterTypes, arguments);
    }

    private static @Nullable Object invoke(
            @Nullable Object target,
            @NotNull Class<?> type,
            @NotNull String methodName,
            @NotNull Class<?>[] parameterTypes,
            @NotNull Object... arguments
    ) throws ReflectiveOperationException {
        var method = accessibleMethod(type, methodName, parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException(type.getName() + '#' + methodName);
        }
        return method.invoke(target, arguments);
    }

    private static @Nullable Method accessibleMethod(
            @NotNull Class<?> type,
            @NotNull String methodName,
            @NotNull Class<?>[] parameterTypes
    ) {
        for (var api : type.getInterfaces()) {
            var method = accessibleMethod(api, methodName, parameterTypes);
            if (method != null) {
                return method;
            }
        }

        var parent = type.getSuperclass();
        if (parent != null) {
            var method = accessibleMethod(parent, methodName, parameterTypes);
            if (method != null) {
                return method;
            }
        }

        if (!Modifier.isPublic(type.getModifiers())) {
            return null;
        }
        try {
            return type.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static @NotNull Throwable causeOf(@NotNull Throwable failure) {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return failure;
    }

    private static void warning(@NotNull String message) {
        Main.getInstance().getLogger().warning(message);
    }

    private record PacketEventsSource(
            @NotNull ClassLoader classLoader,
            @NotNull List<String> packetEventsClasses
    ) {
    }

    private record ProtocolTarget(
            @NotNull Object protocolManager
    ) {
    }

    private record Discovery(
            @NotNull List<ProtocolTarget> targets,
            boolean packetEventsDetected
    ) {
    }
}
