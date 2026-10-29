package dev.mewzica.chatping.server;

import dev.mewzica.chatping.platform.Platforms;
import net.minecraft.server.level.ServerPlayer;

/**
 * 权限检查。
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
 * <p>真正查权限的代码在各个 {@code Platform} 实现里，因为每个加载器的权限插件
 * 长得完全不一样：Fabric 走 {@code fabric-permissions-api}，Forge / NeoForge 走
 * {@code net.luckperms.api.LuckPermsProvider}。这里只留节点名和统一的调用入口。</p>
 *
 * <p>全程用反射的原因没变：权限 API 是权限插件以 jar-in-jar 形式带进来的，
 * <b>没装插件的服务器上根本不存在</b>，直接引用会让服务端连启动都过不去。</p>
 */
public final class NodePermissions {

    /** 删除别人的消息 / 系统消息。 */
    public static final String ADMIN_DELETE_MSG = "chatping.admin.deletemsg";

    /** 接收 {@code @admin} 群体提及。 */
    public static final String ADMIN_MENTION = "chatping.admin.mention";

    private NodePermissions() {
    }

    /**
     * @param defaultLevel 没装权限插件时要求的 op 等级（0 表示所有人）
     */
    public static boolean check(ServerPlayer player, String node, int defaultLevel) {
        return Platforms.get().hasPermission(player, node, defaultLevel);
    }
}
