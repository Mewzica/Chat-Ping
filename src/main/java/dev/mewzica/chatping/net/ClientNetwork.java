package dev.mewzica.chatping.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * 客户端收服务端发来的包。
 *
 * <p>真正的收包注册在 {@link Net#registerClientReceivers()} 里 ——
 * Fabric 的包 API 在 1.20.5 断过一次，差异全被 {@link Net} 吃掉了。
 * 这里只剩「断开连接要把状态清干净」这件和包无关的事。</p>
 */
public final class ClientNetwork {

    private ClientNetwork() {
    }

    public static void register() {
        Net.registerClientReceivers();

        // 断开连接就忘掉「服务端装了 mod」这件事，下次可能是别的服
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerPresence.reset());
    }
}
