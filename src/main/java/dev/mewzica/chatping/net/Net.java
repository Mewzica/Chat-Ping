package dev.mewzica.chatping.net;

import dev.mewzica.chatping.platform.Platforms;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 ↔ 服务端自定义包的门面。
 *
 * <p>上层（{@link dev.mewzica.chatping.ChatEditor} /
 * {@link dev.mewzica.chatping.server.ServerNetwork} / {@code ChatPingServer}）
 * 只认这里的一组方法，至于底下是 Fabric 的 {@code ClientPlayNetworking}、
 * Forge 的 {@code SimpleChannel} 还是 NeoForge 的 {@code PayloadRegistrar}，
 * 全在 {@code Platform} 的实现里。</p>
 *
 * <p>通道语义（五个逻辑通道，每个加载器按自己的方式编码）：</p>
 * <ul>
 *   <li>{@code handshake} 服务端 → 客户端，告诉客户端「服务端也装了 mod」，
 *       顺便带上传「能不能删别人的消息」；</li>
 *   <li>{@code edit} 客户端 → 服务端，请求改某条自己发过的消息；</li>
 *   <li>{@code purge} 客户端 → 服务端，请求删某一条消息（管理员可删别人的 / 系统消息）；</li>
 *   <li>{@code update} 服务端 → 客户端，广播「某条消息被改了」；</li>
 *   <li>{@code remove} 服务端 → 客户端，广播「把这一行删掉」。</li>
 * </ul>
 */
public final class Net {

    private Net() {
    }

    public static void registerPayloads() {
        Platforms.get().registerPayloads();
    }

    public static void registerServerReceivers() {
        Platforms.get().registerServerReceivers();
    }

    public static void registerClientReceivers() {
        Platforms.get().registerClientReceivers();
    }

    public static boolean canReceiveHandshake(ServerPlayer player) {
        return Platforms.get().canReceiveHandshake(player);
    }

    public static void sendHandshake(ServerPlayer player, boolean canDeleteOthers) {
        Platforms.get().sendHandshake(player, canDeleteOthers);
    }

    public static void sendEdit(String oldBody, String newText) {
        Platforms.get().sendEdit(oldBody, newText);
    }

    public static void sendPurge(String senderName, String body, String display) {
        Platforms.get().sendPurge(senderName, body, display);
    }

    public static boolean canReceiveUpdate(ServerPlayer player) {
        return Platforms.get().canReceiveUpdate(player);
    }

    public static void sendUpdate(ServerPlayer player, String senderName, String oldBody, String newRaw) {
        Platforms.get().sendUpdate(player, senderName, oldBody, newRaw);
    }

    public static void sendRemove(ServerPlayer player, String display) {
        Platforms.get().sendRemove(player, display);
    }

    public static boolean canReceiveRemove(ServerPlayer player) {
        return Platforms.get().canReceiveRemove(player);
    }
}
