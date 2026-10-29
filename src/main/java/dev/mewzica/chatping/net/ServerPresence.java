package dev.mewzica.chatping.net;

/**
 * 「当前这个服务器装了 ChatPing 服务端」这个事实，外加服务端授予我的删除能力。
 *
 * <p>服务端在玩家进服后发一次 handshake，收到就置 true。
 * 编辑 / 删除按钮只在 true 的时候才画出来 —— 纯客户端环境下改了自己这边，
 * 别人看到的还是旧的，不如干脆不给按钮。</p>
 *
 * <p>「能不能删别人的消息和系统消息」由服务端按 {@code chatping.admin.deletemsg}
 * 算好后随握手包下发，客户端不自己看 op 等级：LuckPerms 给的权限节点跟原版 op
 * 是两回事，玩家完全可能「有节点但不是 op」，靠 {@code hasPermissionLevel} 猜会猜错。</p>
 *
 * <p>同理，服务端装了 mod 之后 ding 由服务端发（原版客户端也能听到），
 * 客户端就不能再自己播一遍，否则会响两声。</p>
 */
public final class ServerPresence {

    private static boolean active;
    private static boolean canDeleteOthers;

    private ServerPresence() {
    }

    public static boolean isActive() {
        return active;
    }

    /** 能删别人的消息和系统消息（自己的消息本来就能删，不需要这个）。 */
    public static boolean canDeleteOthers() {
        return active && canDeleteOthers;
    }

    public static void setActive(boolean deleteOthers) {
        active = true;
        canDeleteOthers = deleteOthers;
    }

    public static void reset() {
        active = false;
        canDeleteOthers = false;
    }
}
