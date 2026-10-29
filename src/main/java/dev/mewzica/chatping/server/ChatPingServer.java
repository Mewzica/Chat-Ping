package dev.mewzica.chatping.server;

import dev.mewzica.chatping.Compat;
import dev.mewzica.chatping.ChatText;
import dev.mewzica.chatping.net.Net;
import dev.mewzica.chatping.platform.Platform;
import dev.mewzica.chatping.platform.ServerHooks;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端侧逻辑。
 *
 * <p>这里一行加载器相关的代码都没有：事件由加载器实现 {@code Platform}
 * 之后回调 {@link ServerHooks} 的四个方法，网络走 {@link Net}。
 * 所以同一份代码在 Fabric / Forge / NeoForge 上跑的是同一个类。</p>
 *
 * <p>注意事件在<b>任何环境</b>都注册，不区分专用服务端和「单人开房间」——
 * 后者跑的是内置服务端，只注册专用服务端事件会让整套功能在单人存档里失效。</p>
 *
 * <p>装了之后能做三件纯客户端做不到的事：</p>
 * <ol>
 *   <li><b>ding</b> 由服务端发 {@code ClientboundSoundPacket}，所以就算被 @ 的人用的是
 *       原版客户端，也一样能听到；</li>
 *   <li><b>编辑 / 删除</b> 有地方做权限校验和广播，改一次所有人都能看到；</li>
 *   <li><b>握手</b>：装了服务端 mod 就要求客户端也装，否则直接踢 ——
 *       不然两边的聊天栏长得不一样，编辑删除也对不上。</li>
 * </ol>
 */
public final class ChatPingServer implements ServerHooks {

    public static final String MOD_ID = "chatping";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 提示音音高，和客户端本地播放保持一致。 */
    private static final float DING_PITCH = 1.5F;

    /**
     * 进服之后等多久再查「客户端装没装 mod」。
     *
     * <p>不能一进服就查：进服事件触发时客户端还没收到 {@code GameJoin}，
     * 自然也还没把 {@code minecraft:register} 发过来，这时候 {@code canSend}
     * 一定是 false，会把所有人都踢掉。</p>
     */
    private static final int HANDSHAKE_DELAY_TICKS = 10;

    /** 玩家 UUID → 进服时的 tick 数，用来算握手延迟。 */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private ChatPingServer() {
    }

    /** 由加载器入口点在注册完平台实现之后调一次。 */
    public static void init(Platform platform) {
        // 1.20.5+ 的 Fabric 要先注册 payload 类型才能收发；其它实现里是空方法
        platform.registerPayloads();

        platform.registerServerEvents(new ChatPingServer());
        ServerNetwork.register();

        LOGGER.info("ChatPing server loaded: mention ding (vanilla-client friendly), "
                + "message edit/delete, client mod required [{}]", platform.name());
    }

    // ------------------------------------------------------------------
    // ServerHooks
    // ------------------------------------------------------------------

    @Override
    public void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            PENDING.put(player.getUUID(), server.getTickCount());
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        checkHandshake(server);
    }

    @Override
    public void onChatMessage(ServerPlayer sender, String content, String decorated) {
        if (content == null || content.isEmpty()) {
            return;
        }
        // decorated 是原版加过 <名字> 前缀之后真正发出去的那串，编辑时要用它还原前缀
        MessageLog.record(sender.getUUID(), Compat.profileName(sender.getGameProfile()), content, decorated);
        dingMentioned(sender, content);
    }

    // ------------------------------------------------------------------
    // 握手
    // ------------------------------------------------------------------

    private static void checkHandshake(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> pending = it.next();
            if (now - pending.getValue() < HANDSHAKE_DELAY_TICKS) {
                continue;
            }
            it.remove();

            ServerPlayer player = server.getPlayerList().getPlayer(pending.getKey());
            if (player == null) {
                continue;
            }
            if (!Net.canReceiveHandshake(player)) {
                player.connection.disconnect(
                        Component.translatable("chatping.kick.required").withStyle(ChatFormatting.RED));
                continue;
            }
            // 客户端收到了这个包，就知道「服务端也装了」，之后不再自己播 ding；
            // 顺便把「能不能删别人的消息」带过去 —— 权限节点由 LuckPerms 判定，
            // 客户端拿 hasPermissionLevel 猜不出来（有节点但没 op 的人会被判错）
            Net.sendHandshake(player, NodePermissions.check(player, NodePermissions.ADMIN_DELETE_MSG, 2));
        }
    }

    // ------------------------------------------------------------------
    // 提及提示音
    // ------------------------------------------------------------------

    private static void dingMentioned(ServerPlayer sender, String content) {
        if (content.indexOf('@') < 0) {
            return;
        }
        MinecraftServer server = sender.level().getServer();
        if (server == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        // 真有人叫 admin 的时候，@admin 就是普通提及，不该把管理员一起叫起来
        boolean adminNameTaken = false;
        for (ServerPlayer player : players) {
            if (ChatText.ADMIN_NAME.equalsIgnoreCase(Compat.profileName(player.getGameProfile()))) {
                adminNameTaken = true;
                break;
            }
        }
        // 发送侧不设门槛：谁都能叫管理员，拦下来只会让普通玩家没法求助
        boolean group = !adminNameTaken && ChatText.mentionsAdmins(content);

        for (ServerPlayer player : players) {
            if (player == sender) {
                continue;   // 自己发的不用提醒自己
            }
            String name = Compat.profileName(player.getGameProfile());
            if (ChatText.mentions(content, name) || (group && receivesAdminMention(player))) {
                playDing(player);
            }
        }
    }

    /**
     * 这个玩家要不要收 {@code @admin}。
     *
     * <p>由 {@code chatping.admin.mention} 决定。没装 LuckPerms 时它等价于 op 等级 ≥ 2；
     * 装了之后服主可以把某个组设成「能收管理员呼叫但不是 op」，这种人也会响。</p>
     */
    private static boolean receivesAdminMention(ServerPlayer player) {
        return NodePermissions.check(player, NodePermissions.ADMIN_MENTION, 2);
    }

    private static void playDing(ServerPlayer player) {
        // 坐标取玩家自己身上，距离 0，等于在耳边响
        player.connection.send(new ClientboundSoundPacket(
                SoundEvents.NOTE_BLOCK_PLING,
                SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(),
                1.0F, DING_PITCH,
                player.getRandom().nextLong()));
    }
}
