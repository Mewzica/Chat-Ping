package dev.mewzica.chatping.platform;

import net.minecraft.server.level.ServerPlayer;

/**
 * 一个模组加载器要提供的全部能力。
 *
 * <p>业务代码（{@code ChatEditor} / {@code ChatPingHud} / {@code ChatText} /
 * {@code ServerNetwork} / 全部 mixin）只碰 MC 自己的 API，一行加载器相关的代码都没有。
 * 需要加载器帮忙的只有下面这几件事，全部收口在这个接口里：</p>
 *
 * <ol>
 *   <li><b>网络</b>：发 / 收自定义包，以及「对方装没装 mod」的判断；</li>
 *   <li><b>事件</b>：进服 / 退服 / 每 tick / 一条聊天发出去了 / 客户端断开；</li>
 *   <li><b>权限</b>：查一个权限节点（LuckPerms 有就用，没有就退回原版 op 等级）。</li>
 * </ol>
 *
 * <p>每个加载器在入口点里 new 一个自己的实现、塞进 {@link Platforms}，
 * 然后调用共享的 {@code ChatPingServer.init(platform)} / {@code ChatPingClient.init(platform)}。
 * 之后所有静态调用点（{@code Net.xxx} / {@code NodePermissions.check}）都转发到这里。</p>
 *
 * <p>这样一来加一个加载器 = 只写一个 {@code Platform} 实现 + 一个入口点，
 * 23 个 MC 版本共用的那 3000 行一行都不用碰。</p>
 */
public interface Platform {

    /** 加载器名，只用于日志和产物命名，例如 {@code fabric} / {@code forge} / {@code neoforge}。 */
    String name();

    // ------------------------------------------------------------------
    // 网络
    // ------------------------------------------------------------------

    /**
     * 注册自定义包的类型。
     *
     * <p>只有 MC 1.20.5+ 的 Fabric 需要（{@code PayloadTypeRegistry}）；
     * Forge / NeoForge 在创建通道时就注册完了，实现为空。</p>
     */
    void registerPayloads();

    /** 注册「服务端收客户端包」的回调。 */
    void registerServerReceivers();

    /** 注册「客户端收服务端包」的回调。 */
    void registerClientReceivers();

    /** 这个玩家（的客户端）收得到握手包吗 —— 也就是「客户端装没装本 mod」。 */
    boolean canReceiveHandshake(ServerPlayer player);

    void sendHandshake(ServerPlayer player, boolean canDeleteOthers);

    boolean canReceiveUpdate(ServerPlayer player);

    void sendUpdate(ServerPlayer player, String senderName, String oldBody, String newRaw);

    boolean canReceiveRemove(ServerPlayer player);

    void sendRemove(ServerPlayer player, String display);

    /** 客户端 → 服务端：请求编辑自己发过的那条。 */
    void sendEdit(String oldBody, String newText);

    /** 客户端 → 服务端：请求删除某一条。 */
    void sendPurge(String senderName, String body, String display);

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    /**
     * 注册服务端侧事件。
     *
     * <p>注意这些事件在「客户端内置服务端」（单人开房间）里也要能触发，
     * 所以不能只在专用服务端注册。</p>
     */
    void registerServerEvents(ServerHooks hooks);

    /** 注册客户端侧事件。目前只有「断开连接」一件事。 */
    void registerClientEvents(Runnable onDisconnect);

    // ------------------------------------------------------------------
    // 权限
    // ------------------------------------------------------------------

    /**
     * @param defaultLevel 没有权限插件时要求的原版 op 等级
     */
    boolean hasPermission(ServerPlayer player, String node, int defaultLevel);
}
