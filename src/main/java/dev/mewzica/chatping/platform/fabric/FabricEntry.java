package dev.mewzica.chatping.platform.fabric;

import dev.mewzica.chatping.ChatPingClient;
import dev.mewzica.chatping.platform.Platforms;
import dev.mewzica.chatping.server.ChatPingServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric / Quilt 的入口点（{@code fabric.mod.json} 里两个 entrypoint 都指向这里）。
 *
 * <p>Quilt 直接读 {@code fabric.mod.json}，所以这个类同时服务 Quilt ——
 * 不需要另外一份产物。</p>
 *
 * <p>顺序很重要：{@link #onInitialize()} 先把平台实现塞进 {@link Platforms}，
 * 之后 {@link #onInitializeClient()} 才能拿到它。Fabric 保证 main 先于 client。</p>
 */
public final class FabricEntry implements ModInitializer, ClientModInitializer {

    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platforms.set(platform);
        ChatPingServer.init(platform);
    }

    @Override
    public void onInitializeClient() {
        ChatPingClient.init(Platforms.get());
    }
}
