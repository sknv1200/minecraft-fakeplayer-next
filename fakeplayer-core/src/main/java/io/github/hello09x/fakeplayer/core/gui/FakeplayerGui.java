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
package io.github.hello09x.fakeplayer.core.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.devtools.core.utils.ComponentUtils;
import io.github.hello09x.fakeplayer.api.spi.ActionType;
import io.github.hello09x.fakeplayer.core.command.Permission;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.core.manager.action.ActionManager;
import io.github.hello09x.fakeplayer.core.util.Attributes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

@Singleton
public final class FakeplayerGui implements Listener {

    private static final int MAIN_SIZE = 54;
    private static final int SELECTOR_SIZE = 54;
    private static final int SELECTOR_PAGE_SIZE = 45;

    private final Plugin plugin;
    private final FakeplayerManager manager;
    private final ActionManager actionManager;

    @Inject
    public FakeplayerGui(Plugin plugin, FakeplayerManager manager, ActionManager actionManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.actionManager = actionManager;
    }

    public void open(@NotNull Player viewer) {
        var gui = new GuiInventory(MAIN_SIZE, translation(viewer, "fakeplayer.gui.title").color(DARK_GREEN));
        fill(gui, 0, MAIN_SIZE, Material.GRAY_STAINED_GLASS_PANE);

        var target = getCurrentTarget(viewer);
        var canSelect = viewer.hasPermission(Permission.select);
        gui.set(4, targetItem(viewer, target), canSelect ? event -> openSelector(viewer, 1) : null);

        commandButton(gui, viewer, 10, Material.LIME_DYE,
                "fakeplayer.gui.spawn.name", "fakeplayer.gui.spawn.description",
                Permission.spawn, null, false, "fp spawn", false);
        actionButton(gui, viewer, 12, Material.PLAYER_HEAD,
                "fakeplayer.gui.select.name", "fakeplayer.gui.select.description",
                Permission.select, event -> openSelector(viewer, 1));
        commandButton(gui, viewer, 14, Material.WRITABLE_BOOK,
                "fakeplayer.gui.status.name", "fakeplayer.gui.status.description",
                Permission.status, target, true, command("status", target), false);
        commandButton(gui, viewer, 15, Material.CHEST,
                "fakeplayer.gui.inventory.name", "fakeplayer.gui.inventory.description",
                Permission.invsee, target, true, command("invsee", target), false);
        commandButton(gui, viewer, 16, Material.ENDER_PEARL,
                "fakeplayer.gui.teleport.name", "fakeplayer.gui.teleport.description",
                Permission.tp, target, true, command("tp", target), false);
        commandButton(gui, viewer, 17, Material.LEAD,
                "fakeplayer.gui.tphere.name", "fakeplayer.gui.tphere.description",
                Permission.tphere, target, true, command("tphere", target), false);

        commandButton(gui, viewer, 19, Material.IRON_SWORD,
                "fakeplayer.gui.attack-once.name", "fakeplayer.gui.attack-once.description",
                Permission.attack, target, true, command("attack once", target), true);
        commandButton(gui, viewer, 20, Material.DIAMOND_SWORD,
                "fakeplayer.gui.attack-continuous.name", "fakeplayer.gui.attack-continuous.description",
                Permission.attack, target, true, command("attack continuous", target), true);
        commandButton(gui, viewer, 21, Material.IRON_PICKAXE,
                "fakeplayer.gui.mine-once.name", "fakeplayer.gui.mine-once.description",
                Permission.mine, target, true, command("mine once", target), true);
        commandButton(gui, viewer, 22, Material.DIAMOND_PICKAXE,
                "fakeplayer.gui.mine-continuous.name", "fakeplayer.gui.mine-continuous.description",
                Permission.mine, target, true, command("mine continuous", target), true);
        commandButton(gui, viewer, 23, Material.LEVER,
                "fakeplayer.gui.use-once.name", "fakeplayer.gui.use-once.description",
                Permission.use, target, true, command("use once", target), true);
        commandButton(gui, viewer, 24, Material.REPEATER,
                "fakeplayer.gui.use-continuous.name", "fakeplayer.gui.use-continuous.description",
                Permission.use, target, true, command("use continuous", target), true);
        commandButton(gui, viewer, 25, Material.BARRIER,
                "fakeplayer.gui.stop.name", "fakeplayer.gui.stop.description",
                Permission.stop, target, true, command("stop", target), true);

        commandButton(gui, viewer, 28, Material.RABBIT_FOOT,
                "fakeplayer.gui.jump.name", "fakeplayer.gui.jump.description",
                Permission.jump, target, true, command("jump once", target), true);
        commandButton(gui, viewer, 29, Material.LEATHER_BOOTS,
                "fakeplayer.gui.move.name", "fakeplayer.gui.move.description",
                Permission.move, target, true, command("move forward", target), true);
        commandButton(gui, viewer, 30, Material.GRAY_DYE,
                "fakeplayer.gui.sneak.name", "fakeplayer.gui.sneak.description",
                Permission.sneak, target, true, command("sneak", target), true);
        commandButton(gui, viewer, 31, Material.SUGAR,
                "fakeplayer.gui.sprint.name", "fakeplayer.gui.sprint.description",
                Permission.sprint, target, true, command("sprint", target), true);
        commandButton(gui, viewer, 32, Material.FEATHER,
                "fakeplayer.gui.drop.name", "fakeplayer.gui.drop.description",
                Permission.drop, target, true, command("drop once", target), true);
        commandButton(gui, viewer, 33, Material.HOPPER,
                "fakeplayer.gui.drop-stack.name", "fakeplayer.gui.drop-stack.description",
                Permission.dropstack, target, true, command("dropstack once", target), true);
        commandButton(gui, viewer, 34, Material.TOTEM_OF_UNDYING,
                "fakeplayer.gui.swap.name", "fakeplayer.gui.swap.description",
                Permission.swap, target, true, command("swap", target), true);

        var sleeping = target != null && target.isSleeping();
        commandButton(gui, viewer, 37, Material.RED_BED,
                sleeping ? "fakeplayer.gui.wakeup.name" : "fakeplayer.gui.sleep.name",
                sleeping ? "fakeplayer.gui.wakeup.description" : "fakeplayer.gui.sleep.description",
                sleeping ? Permission.wakeup : Permission.sleep, target, true,
                command(sleeping ? "wakeup" : "sleep", target), true);
        commandButton(gui, viewer, 38, Material.SPYGLASS,
                "fakeplayer.gui.look-me.name", "fakeplayer.gui.look-me.description",
                Permission.look, target, true, command("look me", target), true);
        commandButton(gui, viewer, 39, Material.COMPASS,
                "fakeplayer.gui.turn-back.name", "fakeplayer.gui.turn-back.description",
                Permission.turn, target, true, command("turn back", target), true);
        commandButton(gui, viewer, 40, Material.COMPARATOR,
                "fakeplayer.gui.config.name", "fakeplayer.gui.config.description",
                Permission.config, null, false, "fp config list", false);
        actionButton(gui, viewer, 45, Material.CLOCK,
                "fakeplayer.gui.refresh.name", "fakeplayer.gui.refresh.description",
                null, event -> open(viewer));
        actionButton(gui, viewer, 49, Material.BARRIER,
                "fakeplayer.gui.close.name", "fakeplayer.gui.close.description",
                null, event -> viewer.closeInventory());

        var canKill = viewer.hasPermission(Permission.kill) && target != null;
        var killLore = new ArrayList<Component>();
        killLore.add(translation(viewer, "fakeplayer.gui.kill.description").color(GRAY));
        addAvailabilityLore(killLore, viewer, Permission.kill, target, true,
                "fakeplayer.gui.common.shift-click");
        gui.set(53, item(Material.SKELETON_SKULL,
                        translation(viewer, "fakeplayer.gui.kill.name").color(RED), killLore),
                canKill ? event -> {
                    if (event.isShiftClick()) {
                        execute(viewer, command("kill", target), false);
                    }
                } : null);

        viewer.openInventory(gui.getInventory());
    }

    public void openCommand(@NotNull Player viewer, @NotNull CommandArguments ignored) {
        open(viewer);
    }

    public void openSelector(@NotNull Player viewer, int requestedPage) {
        if (!viewer.hasPermission(Permission.select)) {
            return;
        }

        var targets = getAvailableTargets(viewer);
        var pages = Math.max(1, (targets.size() + SELECTOR_PAGE_SIZE - 1) / SELECTOR_PAGE_SIZE);
        var page = Math.max(1, Math.min(requestedPage, pages));
        var gui = new GuiInventory(SELECTOR_SIZE, translation(
                viewer,
                "fakeplayer.gui.selector.title",
                text(page),
                text(pages)
        ).color(DARK_GREEN));

        var selected = manager.getSelection(viewer);
        var from = (page - 1) * SELECTOR_PAGE_SIZE;
        var to = Math.min(from + SELECTOR_PAGE_SIZE, targets.size());
        for (var index = from; index < to; index++) {
            var target = targets.get(index);
            var selectedTarget = selected != null && selected.getUniqueId().equals(target.getUniqueId());
            gui.set(index - from, selectorItem(viewer, target, selectedTarget), event -> {
                manager.setSelection(viewer, target);
                viewer.sendMessage(translatable(
                        "fakeplayer.command.select.success.selected",
                        text(target.getName(), WHITE)
                ).color(GRAY));
                open(viewer);
            });
        }

        if (targets.isEmpty()) {
            gui.set(22, item(Material.BARRIER,
                    translation(viewer, "fakeplayer.gui.target.none").color(RED),
                    List.of(translation(viewer, "fakeplayer.gui.target.none-description").color(GRAY))), null);
        }

        fill(gui, 45, SELECTOR_SIZE, Material.GRAY_STAINED_GLASS_PANE);
        if (page > 1) {
            actionButton(gui, viewer, 45, Material.ARROW,
                    "fakeplayer.gui.previous.name", "fakeplayer.gui.previous.description",
                    null, event -> openSelector(viewer, page - 1));
        }
        actionButton(gui, viewer, 49, Material.OAK_DOOR,
                "fakeplayer.gui.back.name", "fakeplayer.gui.back.description",
                null, event -> open(viewer));
        if (page < pages) {
            actionButton(gui, viewer, 53, Material.SPECTRAL_ARROW,
                    "fakeplayer.gui.next.name", "fakeplayer.gui.next.description",
                    null, event -> openSelector(viewer, page + 1));
        }

        viewer.openInventory(gui.getInventory());
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiInventory gui)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        var action = gui.getAction(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiInventory) {
            event.setCancelled(true);
        }
    }

    private void commandButton(
            @NotNull GuiInventory gui,
            @NotNull Player viewer,
            int slot,
            @NotNull Material material,
            @NotNull String nameKey,
            @NotNull String descriptionKey,
            @NotNull String permission,
            @Nullable Player target,
            boolean requiresTarget,
            @NotNull String command,
            boolean reopen
    ) {
        var enabled = viewer.hasPermission(permission) && (!requiresTarget || target != null);
        var lore = new ArrayList<Component>();
        lore.add(translation(viewer, descriptionKey).color(GRAY));
        addAvailabilityLore(lore, viewer, permission, target, requiresTarget,
                "fakeplayer.gui.common.click");
        gui.set(slot, item(material, translation(viewer, nameKey).color(enabled ? GREEN : RED), lore),
                enabled ? event -> execute(viewer, command, reopen) : null);
    }

    private void actionButton(
            @NotNull GuiInventory gui,
            @NotNull Player viewer,
            int slot,
            @NotNull Material material,
            @NotNull String nameKey,
            @NotNull String descriptionKey,
            @Nullable String permission,
            @NotNull Consumer<InventoryClickEvent> action
    ) {
        var enabled = permission == null || viewer.hasPermission(permission);
        var lore = new ArrayList<Component>();
        lore.add(translation(viewer, descriptionKey).color(GRAY));
        if (enabled) {
            lore.add(translation(viewer, "fakeplayer.gui.common.click").color(YELLOW));
        } else {
            lore.add(translation(viewer, "fakeplayer.gui.common.no-permission").color(RED));
        }
        gui.set(slot, item(material, translation(viewer, nameKey).color(enabled ? GREEN : RED), lore),
                enabled ? action : null);
    }

    private void addAvailabilityLore(
            @NotNull List<Component> lore,
            @NotNull Player viewer,
            @NotNull String permission,
            @Nullable Player target,
            boolean requiresTarget,
            @NotNull String enabledKey
    ) {
        if (!viewer.hasPermission(permission)) {
            lore.add(translation(viewer, "fakeplayer.gui.common.no-permission").color(RED));
        } else if (requiresTarget && target == null) {
            lore.add(translation(viewer, "fakeplayer.gui.common.no-target").color(RED));
        } else {
            lore.add(translation(viewer, enabledKey).color(YELLOW));
        }
    }

    private void execute(@NotNull Player viewer, @NotNull String command, boolean reopen) {
        viewer.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            viewer.performCommand(command);
            if (reopen) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (viewer.isOnline()) {
                        open(viewer);
                    }
                });
            }
        });
    }

    private @NotNull ItemStack targetItem(@NotNull Player viewer, @Nullable Player target) {
        var canSelect = viewer.hasPermission(Permission.select);
        if (target == null) {
            var lore = new ArrayList<Component>();
            lore.add(translation(viewer, "fakeplayer.gui.target.none-description").color(GRAY));
            lore.add(translation(viewer, canSelect
                    ? "fakeplayer.gui.common.click"
                    : "fakeplayer.gui.common.no-permission").color(canSelect ? YELLOW : RED));
            return item(Material.BARRIER,
                    translation(viewer, "fakeplayer.gui.target.none").color(RED),
                    lore);
        }

        var lore = targetLore(viewer, target);
        lore.add(translation(viewer, canSelect
                ? "fakeplayer.gui.target.change"
                : "fakeplayer.gui.common.no-permission").color(canSelect ? YELLOW : RED));
        return playerHead(target,
                translation(viewer, "fakeplayer.gui.target.name", text(target.getName())).color(GOLD),
                lore);
    }

    private @NotNull ItemStack selectorItem(@NotNull Player viewer, @NotNull Player target, boolean selected) {
        var lore = targetLore(viewer, target);
        if (selected) {
            lore.add(translation(viewer, "fakeplayer.gui.target.selected").color(GREEN));
        }
        lore.add(translation(viewer, "fakeplayer.gui.selector.select").color(YELLOW));
        return playerHead(target, text(target.getName(), selected ? GREEN : GOLD), lore);
    }

    private @NotNull List<Component> targetLore(@NotNull Player viewer, @NotNull Player target) {
        var location = target.getLocation();
        var maxHealth = Optional.ofNullable(target.getAttribute(Attributes.maxHealth()))
                .map(AttributeInstance::getValue)
                .orElse(20D);
        var actions = actionManager.getActiveActions(target).stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(ActionType::translationKey)
                .map(key -> ComponentUtils.toString(translatable(key), viewer.locale()))
                .collect(Collectors.joining(", "));
        if (actions.isEmpty()) {
            actions = ComponentUtils.toString(
                    translatable("fakeplayer.gui.target.actions-none"), viewer.locale());
        }

        return new ArrayList<>(List.of(
                translation(viewer, "fakeplayer.gui.target.creator",
                        text(Optional.ofNullable(manager.getCreatorName(target)).orElse("-"))).color(GRAY),
                translation(viewer, "fakeplayer.gui.target.health",
                        text(format(target.getHealth())), text(format(maxHealth))).color(GRAY),
                translation(viewer, "fakeplayer.gui.target.food", text(target.getFoodLevel())).color(GRAY),
                translation(viewer, "fakeplayer.gui.target.location",
                        text(location.getWorld().getName()),
                        text(location.getBlockX()),
                        text(location.getBlockY()),
                        text(location.getBlockZ())).color(GRAY),
                translation(viewer, "fakeplayer.gui.target.actions", text(actions)).color(GRAY)
        ));
    }

    private @NotNull List<Player> getAvailableTargets(@NotNull Player viewer) {
        var targets = viewer.isOp() ? manager.getAll() : manager.getAll(viewer);
        return targets.stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private @Nullable Player getCurrentTarget(@NotNull Player viewer) {
        var selected = manager.getSelection(viewer);
        if (selected != null && (viewer.isOp() || manager.get(viewer, selected.getName()) != null)) {
            return selected;
        }

        var targets = getAvailableTargets(viewer);
        return targets.size() == 1 ? targets.get(0) : null;
    }

    private static @NotNull String command(@NotNull String operation, @Nullable Player target) {
        return target == null ? "fp " + operation : "fp " + operation + " " + target.getName();
    }

    private static @NotNull String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static @NotNull ItemStack playerHead(
            @NotNull Player target,
            @NotNull Component name,
            @NotNull List<Component> lore
    ) {
        var item = item(Material.PLAYER_HEAD, name, lore);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static @NotNull ItemStack item(
            @NotNull Material material,
            @NotNull Component name,
            @NotNull List<Component> lore
    ) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(noItalic(name));
        meta.lore(lore.stream().map(FakeplayerGui::noItalic).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(ITALIC, false);
    }

    private static @NotNull Component translation(
            @NotNull Player viewer,
            @NotNull String key,
            @NotNull Component... arguments
    ) {
        return text(ComponentUtils.toString(translatable(key, arguments), viewer.locale()));
    }

    private static void fill(@NotNull GuiInventory gui, int from, int to, @NotNull Material material) {
        var filler = item(material, Component.empty(), List.of());
        for (var slot = from; slot < to; slot++) {
            gui.set(slot, filler, null);
        }
    }

    private static final class GuiInventory implements InventoryHolder {

        private final Inventory inventory;
        private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

        private GuiInventory(int size, @NotNull Component title) {
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        private void set(
                int slot,
                @NotNull ItemStack item,
                @Nullable Consumer<InventoryClickEvent> action
        ) {
            inventory.setItem(slot, item);
            if (action == null) {
                actions.remove(slot);
            } else {
                actions.put(slot, action);
            }
        }

        private @Nullable Consumer<InventoryClickEvent> getAction(int slot) {
            return actions.get(slot);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
