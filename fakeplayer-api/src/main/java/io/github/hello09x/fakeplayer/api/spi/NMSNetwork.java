/*
 * Modified by yigemingzii, August 2026
 * - Exposed optional fake channels for packet library compatibility
 */
package io.github.hello09x.fakeplayer.api.spi;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface NMSNetwork {

    /**
     * 绑定一个虚拟的游戏连接
     *
     * @param server 服务器
     * @param player 假人玩家
     */
    @NotNull NMSServerGamePacketListener placeNewPlayer(@NotNull Server server, @NotNull Player player);

    /**
     * 获取服务侧游戏数据包监听器
     * <p>在获取之前需要先执行了 {@link #placeNewPlayer(Server, Player)} 才会初始化值</p>
     */
    @NotNull
    NMSServerGamePacketListener getServerGamePacketListener() throws IllegalStateException;

    /**
     * 获取底层虚拟 Channel，用于兼容需要识别假连接的网络插件。
     *
     * @return 当前版本未公开 Channel 时返回 {@code null}
     */
    default @Nullable Object getChannel() {
        return null;
    }

}
