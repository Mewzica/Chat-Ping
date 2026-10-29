package dev.mewzica.chatping.server;

import dev.mewzica.chatping.Compat;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * 权限检查，兼容 LuckPerms（以及任何实现了 {@code fabric-permissions-api} 的插件）。
 *
 * <p>为什么全程用反射，而不是直接 {@code import me.lucko.fabric.api.permissions.v0.Permissions}：</p>
 * <ul>
 *   <li>那个 API 是 LuckPerms 以 jar-in-jar 形式带进来的，<b>没装 LP 的服务器上根本不存在</b>。
 *       直接引用会在类加载阶段就抛 {@code NoClassDefFoundError}，服务端连启动都过不去；</li>
 *   <li>反射找不到类时静默回退到原版 op 等级判断，这样「装了 LP」和「没装 LP」
 *       是同一份 jar，服主不用为了权限功能换包。</li>
 * </ul>
 *
 * <p>两个节点的语义：</p>
 * <table border="1">
 *   <caption>权限节点</caption>
 *   <tr><th>节点</th><th>作用</th><th>没装权限插件时</th></tr>
 *   <tr><td>{@value #ADMIN_DELETE_MSG}</td><td>删除别人的消息、以及系统消息</td><td>op 等级 ≥ 2</td></tr>
 *   <tr><td>{@value #ADMIN_MENTION}</td><td>接收 {@code @admin} 群体提及的通知</td><td>op 等级 ≥ 2</td></tr>
 * </table>
 *
 * <p>{@code @admin} 的<b>发送</b>侧不设门槛 —— 普通玩家求助管理员是刚需，
 * 拦下来只会让人没法喊人。所以节点管的是「谁能收到」：被配了这个节点的人
 * 才会在别人 {@code @admin} 时听到 ding。</p>
 *
 * <p>默认值就是 {@code Permissions.check(entity, node, defaultRequiredLevel)} 的第三个参数，
 * 没装权限插件时它等价于 {@code entity.hasPermissionLevel(level)}。</p>
 */
public final class NodePermissions {

    /** 删除别人的消息 / 系统消息。 */
    public static final String ADMIN_DELETE_MSG = "chatping.admin.deletemsg";

    /** 接收 {@code @admin} 群体提及。 */
    public static final String ADMIN_MENTION = "chatping.admin.mention";

    private static final String API_CLASS = "me.lucko.fabric.api.permissions.v0.Permissions";

    /** 反射拿到的 {@code check(Entity, String, int)}；null 表示环境里没有权限插件。 */
    private static Method checkMethod;
    private static boolean resolved;

    private NodePermissions() {
    }

    /**
     * @param defaultLevel 没装权限插件时要求的 op 等级（0 表示所有人）
     */
    public static boolean check(ServerPlayer player, String node, int defaultLevel) {
        Method method = method();
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
    private static synchronized Method method() {
        if (resolved) {
            return checkMethod;
        }
        resolved = true;
        try {
            Class<?> api = Class.forName(API_CLASS);
            checkMethod = api.getMethod("check", Entity.class, String.class, int.class);
        } catch (Throwable ignored) {
            checkMethod = null;
        }
        return checkMethod;
    }
}
