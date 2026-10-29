package dev.mewzica.chatping.platform.fabric;

import dev.mewzica.chatping.Compat;
import dev.mewzica.chatping.platform.Platform;
import dev.mewzica.chatping.platform.ServerHooks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

/**
 * Fabric 的实现。
 *
 * <p>这一层只做三件事，一行版本分叉都没有：</p>
 * <ol>
 *   <li><b>网络</b>：全部转发给 {@link FabricNet}。那边是 MC 1.20.5 断代前后的两套实现，
 *       版本判断按 Stonecutter 的写法落在那个文件里；</li>
 *   <li><b>事件</b>：把 Fabric 的事件回调转成 {@link ServerHooks} 的四个方法；</li>
 *   <li><b>权限</b>：{@code fabric-permissions-api}（LuckPerms 以 jar-in-jar 带进来的那套）。</li>
 * </ol>
 *
 * <p>把网络拆到 {@code FabricNet} 里，是为了让这个文件在 23 个 MC 版本上是同一份 ——
 * 需要分叉的东西全集中在一个文件里，改动面小。</p>
 */
public final class FabricPlatform implements Platform {

    private static final String PERMISSIONS_API = "me.lucko.fabric.api.permissions.v0.Permissions";

    /** 反射拿到的 {@code check(Entity, String, int)}；null 表示环境里没有权限插件。 */
    private static Method checkMethod;
    private static boolean permsResolved;

    @Override
    public String name() {
        return "fabric";
    }

    // ==================================================================
    // 网络：转发
    // ==================================================================

    @Override
    public void registerPayloads() {
        FabricNet.registerPayloads();
    }

    @Override
    public void registerServerReceivers() {
        FabricNet.registerServerReceivers();
    }

    @Override
    public void registerClientReceivers() {
        FabricNet.registerClientReceivers();
    }

    @Override
    public boolean canReceiveHandshake(ServerPlayer player) {
        return FabricNet.canReceiveHandshake(player);
    }

    @Override
    public void sendHandshake(ServerPlayer player, boolean canDeleteOthers) {
        FabricNet.sendHandshake(player, canDeleteOthers);
    }

    @Override
    public boolean canReceiveUpdate(ServerPlayer player) {
        return FabricNet.canReceiveUpdate(player);
    }

    @Override
    public void sendUpdate(ServerPlayer player, String senderName, String oldBody, String newRaw) {
        FabricNet.sendUpdate(player, senderName, oldBody, newRaw);
    }

    @Override
    public boolean canReceiveRemove(ServerPlayer player) {
        return FabricNet.canReceiveRemove(player);
    }

    @Override
    public void sendRemove(ServerPlayer player, String display) {
        FabricNet.sendRemove(player, display);
    }

    @Override
    public void sendEdit(String oldBody, String newText) {
        FabricNet.sendEdit(oldBody, newText);
    }

    @Override
    public void sendPurge(String senderName, String body, String display) {
        FabricNet.sendPurge(senderName, body, display);
    }

    // ==================================================================
    // 事件
    // ==================================================================

    @Override
    public void registerServerEvents(ServerHooks hooks) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player != null) {
                hooks.onPlayerJoin(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                hooks.onPlayerLeave(handler.player);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(hooks::onServerTick);

        // 玩家每发一条聊天就记一笔账，顺手把被 @ 的人叮一下
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                hooks.onChatMessage(sender, message.signedContent(),
                        message.decoratedContent().getString()));
    }

    @Override
    public void registerClientEvents(Runnable onDisconnect) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect.run());
    }

    // ==================================================================
    // 权限
    // ==================================================================

    /**
     * 兼容 LuckPerms（以及任何实现了 {@code fabric-permissions-api} 的插件）。
     *
     * <p>那个 API 是 LuckPerms 以 jar-in-jar 形式带进来的，<b>没装 LP 的服务器上
     * 根本不存在</b>，直接引用会在类加载阶段就抛 {@code NoClassDefFoundError}，
     * 服务端连启动都过不去。所以反射找，找不到就静默退回原版 op 等级判断 ——
     * 「装了 LP」和「没装 LP」是同一份 jar。</p>
     */
    @Override
    public boolean hasPermission(ServerPlayer player, String node, int defaultLevel) {
        Method method = permissionMethod();
        if (method != null) {
            try {
                Object result = method.invoke(null, player, node, defaultLevel);
                if (result instanceof Boolean value) {
                    return value;
                }
            } catch (Throwable ignored) {
                // 权限插件内部炸了不能带着聊天功能一起挂，退回原版判断
            }
        }
        return Compat.hasOpLevel(player, defaultLevel);
    }

    /** 只解析一次，之后复用同一个 {@link Method}。 */
    private static synchronized Method permissionMethod() {
        if (permsResolved) {
            return checkMethod;
        }
        permsResolved = true;
        try {
            Class<?> api = Class.forName(PERMISSIONS_API);
            checkMethod = api.getMethod("check", Entity.class, String.class, int.class);
        } catch (Throwable ignored) {
            checkMethod = null;
        }
        return checkMethod;
    }
}
