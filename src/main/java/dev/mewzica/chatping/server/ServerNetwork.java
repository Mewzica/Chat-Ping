package dev.mewzica.chatping.server;

import dev.mewzica.chatping.Compat;
import dev.mewzica.chatping.ChatText;
import dev.mewzica.chatping.net.Net;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import java.util.regex.Matcher;

/**
 * 服务端收包与广播。
 *
 * <p>客户端只说「我要动正文为 X 的那条」，服务端自己校验发送者身份和时间，
 * 通过之后广播 {@code chatping:update} 给所有装了 mod 的客户端。</p>
 *
 * <p>真正的收包注册在 {@link Net#registerServerReceivers()} 里 ——
 * Fabric 的包 API 在 1.20.5 断过一次，差异全被 {@link Net} 吃掉了，
 * 这里只留「收到之后干什么」。</p>
 */
public final class ServerNetwork {

    /** 单条消息上限，和原版聊天框一致。 */
    private static final int MAX_LENGTH = 256;

    private ServerNetwork() {
    }

    public static void register() {
        Net.registerServerReceivers();
    }

    // ------------------------------------------------------------------

    /** 客户端请求编辑某条自己发过的消息。由 {@link Net} 的收包回调转进来。 */
    public static void onEditRequest(MinecraftServer server, ServerPlayer player,
                                     String oldBody, String newText) {
        handleEdit(server, player, oldBody, newText);
    }

    private static void handleEdit(MinecraftServer server, ServerPlayer player,
                                   String oldBody, String newText) {
        if (newText == null || newText.isBlank()) {
            return;
        }
        if (newText.length() > MAX_LENGTH) {
            warn(player, Component.translatable("chatping.error.too_long", MAX_LENGTH));
            return;
        }
        MessageLog.Entry entry = MessageLog.find(player.getUUID(), oldBody);
        if (entry == null) {
            warn(player, Component.translatable("chatping.error.not_found"));
            return;
        }

        String newRaw = rebuild(entry.raw, newText);
        entry.raw = newRaw;
        entry.body = ChatText.stripQuote(newRaw);
        // 广播出去的是「前缀 + 新原文」：客户端那边整行替换，不带前缀的话玩家名就没了
        String outgoing = entry.prefix + newRaw;
        ChatPingServer.LOGGER.info("edit ok: sender={} oldBody=[{}] prefix=[{}] -> [{}]",
                entry.senderName, oldBody, entry.prefix, outgoing);
        broadcast(server, entry.senderName, oldBody, outgoing);
    }

    /**
     * 删除一条消息。
     *
     * <p>和编辑不一样，删除<b>不要求</b>是自己的消息 —— 管理员可以删任何一条，
     * 包括系统消息。所以判定分两种：</p>
     * <ul>
     *   <li><b>账本里有</b>（说明是玩家发的）：作者本人可以删，管理员也可以删；</li>
     *   <li><b>账本里没有</b>（系统消息、公告、命令反馈，或者太旧已经被清掉）：
     *       只认管理员。</li>
     * </ul>
     *
     * <p>服务端自己不删任何东西 —— 它本来就没有聊天栏，账本里那条也只是打上
     * {@link MessageLog.Entry#deleted} 标记，条目继续留着。这就是用户要的「服务端保留日志」。</p>
     */
    /** 客户端请求删除某条消息。由 {@link Net} 的收包回调转进来。 */
    public static void onPurgeRequest(MinecraftServer server, ServerPlayer player,
                                      String senderName, String body, String display) {
        handlePurge(server, player, senderName, body, display);
    }

    private static void handlePurge(MinecraftServer server, ServerPlayer player,
                                    String senderName, String body, String display) {
        if (display == null || display.isBlank()) {
            return;
        }
        boolean admin = NodePermissions.check(player, NodePermissions.ADMIN_DELETE_MSG, 2);
        MessageLog.Entry entry = MessageLog.findByName(senderName, body);

        if (entry != null) {
            if (!entry.sender.equals(player.getUUID()) && !admin) {
                warn(player, Component.translatable("chatping.error.not_owner"));
                return;
            }
            entry.deleted = true;
        } else if (!admin) {
            warn(player, Component.translatable("chatping.error.not_admin"));
            return;
        }

        ChatPingServer.LOGGER.info("[purge] {} ({}) removed {}: [{}]",
                Compat.profileName(player.getGameProfile()),
                admin ? "admin" : "author",
                entry != null ? "message" : "system-message",
                display);
        broadcastRemove(server, display);
    }

    /**
     * 把原文里的正文部分换成新内容，引用段原样保留。
     *
     * <p>比如原来发的是 {@code @Steve “<Steve> 晚上开黑吗” 八点}，
     * 改成「九点」之后应该还是同一条回复，只是正文变了。</p>
     */
    private static String rebuild(String raw, String newBody) {
        Matcher m = ChatText.QUOTED.matcher(raw);
        if (m.find() && m.start() == 0) {
            return raw.substring(0, m.end()) + newBody;
        }
        return newBody;
    }

    private static void broadcast(MinecraftServer server, String senderName, String oldBody, String newRaw) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!Net.canReceiveUpdate(player)) {
                continue;
            }
            Net.sendUpdate(player, senderName, oldBody, newRaw);
        }
    }

    /**
     * 广播「把这一行删掉」。
     *
     * <p>只发整行文本，不发下标：每个客户端的聊天栏内容不完全一样（有人中途才进服、
     * 有人改过聊天过滤），按文本让各自去认是最稳的。同文本重复出现时从最新那条删，
     * 各端结果也一致。</p>
     */
    private static void broadcastRemove(MinecraftServer server, String display) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!Net.canReceiveRemove(player)) {
                continue;
            }
            Net.sendRemove(player, display);
        }
    }

    private static void warn(ServerPlayer player, MutableComponent message) {
        player.displayClientMessage(Component.literal("[ChatPing] ").withStyle(ChatFormatting.GRAY)
                .append(message.withStyle(ChatFormatting.RED)), false);
    }
}
