package dev.mewzica.chatping;

import dev.mewzica.chatping.net.ServerPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * 处理「收到消息」这一侧的逻辑：判断这条消息有没有 @ 到自己，有就播一声 ding。
 *
 * <p>两种部署方式共用这段代码：</p>
 * <ul>
 *   <li><b>纯客户端</b>：每个装了本 mod 的客户端自己扫描收到的聊天内容，本地播音，
 *       所以服务端不需要装任何东西；</li>
 *   <li><b>服务端也装了</b>：ding 由服务端发 {@code ClientboundSoundPacket}（原版客户端也能听到），
 *       这边就必须闭嘴，否则一次提及会响两声。</li>
 * </ul>
 */
public final class ChatPing {

    /** 提示音音高，1.5 比默认高一点，听起来更像「叮」。 */
    private static final float DING_PITCH = 1.5F;

    private ChatPing() {
    }

    public static void onIncoming(Component message) {
        // 服务端会替我们播，别再响一遍
        if (ServerPresence.isActive()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        String me = client.getUser().getName();
        if (me == null || me.isEmpty()) {
            return;
        }
        String flat = message.getString();
        if (flat.indexOf('@') < 0) {
            return;
        }
        // 自己发的不用提醒自己（服务端那条路径也是这么跳过的，这里对齐一下）
        if (flat.startsWith("<" + me + ">")) {
            return;
        }

        boolean mentioned = ChatFormat.mentions(flat, me);
        // 纯客户端模式下没人帮我们算「谁能收 @admin」，只能看自己是不是 op。
        // 服务端装了 mod 的话这条根本走不到 —— 上面已经 return 了。
        boolean adminCall = !mentioned
                && ChatText.mentionsAdmins(flat)
                && !ChatFormat.isOnlinePlayer(ChatText.ADMIN_NAME)
                && client.player.hasPermissions(2);
        if (!mentioned && !adminCall) {
            return;
        }

        client.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, DING_PITCH));
    }
}
