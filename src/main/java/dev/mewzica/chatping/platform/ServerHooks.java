package dev.mewzica.chatping.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端要接的四个事件。
 *
 * <p>由 {@code ChatPingServer} 实现，加载器那边只管在合适的时机回调 ——
 * 具体用哪个事件总线（Fabric 的 {@code ServerTickEvents} / Forge 的
 * {@code TickEvent} / NeoForge 的 {@code ServerTickEvent.Post}）是实现方的事。</p>
 */
public interface ServerHooks {

    /** 玩家刚进服。此时客户端还没收到 GameJoin，不能立刻查装没装 mod。 */
    void onPlayerJoin(ServerPlayer player);

    void onPlayerLeave(ServerPlayer player);

    /** 每个服务端 tick 末尾。 */
    void onServerTick(MinecraftServer server);

    /**
     * 一条聊天真正发出去了（原版已经加好 {@code <名字>} 前缀）。
     *
     * @param sender    发送者
     * @param content   玩家敲的正文，不含前缀
     * @param decorated 加完前缀之后真正显示的那串，编辑时要靠它还原前缀
     */
    void onChatMessage(ServerPlayer sender, String content, String decorated);
}
