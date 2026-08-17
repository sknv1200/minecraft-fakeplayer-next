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
 *
 * Modified by yigemingzii, July 2026 for Minecraft 1.21.11 support.
 */
package io.github.hello09x.fakeplayer.v1_21_11.network;

import io.github.hello09x.fakeplayer.api.spi.NMSServerGamePacketListener;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import lombok.Lombok;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class FakeServerGamePacketListenerImpl extends ServerGamePacketListenerImpl implements NMSServerGamePacketListener {

    private final FakeplayerManager manager = Main.getInjector().getInstance(FakeplayerManager.class);
    private final static Logger log = Main.getInstance().getLogger();

    public FakeServerGamePacketListenerImpl(
            @NotNull MinecraftServer server,
            @NotNull Connection connection,
            @NotNull ServerPlayer player,
            @NotNull CommonListenerCookie cookie
    ) {
        super(server, connection, player, cookie);
        Optional.ofNullable(Bukkit.getPlayer(player.getUUID()))
                .ifPresent(p -> this.addChannel(p, BUNGEE_CORD_CORRECTED_CHANNEL));
    }

    private boolean addChannel(@NotNull Player player, @NotNull String channel) {
        try {
            var method = player.getClass().getMethod("addChannel", String.class);
            var ret = method.invoke(player, channel);
            if (ret instanceof Boolean success) {
                return success;
            }
            return true;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

    @Override
    public void send(Packet<?> packet) {
        // ========== 新增：拦截背包数据包，截断物品列表防止越界 ==========
        if (packet instanceof ClientboundContainerSetContentPacket containerPacket) {
            try {
                // 通过反射获取内部 items 字段
                Field itemsField = ClientboundContainerSetContentPacket.class.getDeclaredField("items");
                itemsField.setAccessible(true);
                List<ItemStack> items = (List<ItemStack>) itemsField.get(containerPacket);
                
                // 假人背包最大槽位数（默认为72，如果容器大小不同可调整）
                int maxSize = 72;
                if (items.size() > maxSize) {
                    // 截断列表，保留前 maxSize 个物品
                    List<ItemStack> newItems = new ArrayList<>(items.subList(0, maxSize));
                    itemsField.set(containerPacket, newItems);
                    log.info("Truncated container items from " + items.size() + " to " + maxSize);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                log.warning("Failed to truncate container packet: " + e.getMessage());
                // 如果反射失败，仍然发送原包（可能仍会崩溃，但已尽力）
            }
            // 发送修改后的数据包（可能被截断）
            super.send(packet);
            return;
        }
        // ========== 原有特殊包处理（保持不变） ==========
        if (packet instanceof ClientboundCustomPayloadPacket p) {
            this.handleCustomPayloadPacket(p);
        } else if (packet instanceof ClientboundSetEntityMotionPacket p) {
            this.handleClientboundSetEntityMotionPacket(p);
        }
        // 其余数据包忽略（符合原插件设计）
    }

    /**
     * 玩家被击退的动作由客户端完成, 假人没有客户端因此手动完成这个动作
     */
    public void handleClientboundSetEntityMotionPacket(@NotNull ClientboundSetEntityMotionPacket packet) {
        if (packet.getId() == this.player.getId() && this.player.hurtMarked) {
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                this.player.hurtMarked = true;
                var movement = packet.getMovement();
                this.player.lerpMotion(movement);
            });
        }
    }

    private void handleCustomPayloadPacket(@NotNull ClientboundCustomPayloadPacket packet) {
        var payload = packet.payload();
        var resourceLocation = payload.type().id();
        var channel = resourceLocation.getNamespace() + ":" + resourceLocation.getPath();

        if (!channel.equals(BUNGEE_CORD_CORRECTED_CHANNEL)) {
            return;
        }

        if (!(payload instanceof DiscardedPayload discardedPayload)) {
            return;
        }

        var recipient = Bukkit
                .getOnlinePlayers()
                .stream()
                .filter(manager::isNotFake)
                .findAny()
                .orElse(null);

        if (recipient == null) {
            log.warning("Failed to forward a plugin message cause non real players in the server");
            return;
        }

        var message = getDiscardedPayloadData(discardedPayload);
        recipient.sendPluginMessage(Main.getInstance(), BUNGEE_CORD_CHANNEL, message);
    }

    private byte[] getDiscardedPayloadData(@NotNull DiscardedPayload payload) {
        try {
            return payload.data().array();
        } catch (NoSuchMethodError e) {
            try {
                return (byte[]) payload.getClass().getMethod("data").invoke(payload);   // 1.21.5 actual is  `public final byte[] data() {}`
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                throw Lombok.sneakyThrow(e);
            }
        }
    }
}
