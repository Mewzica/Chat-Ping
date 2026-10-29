package dev.mewzica.chatping;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import dev.mewzica.chatping.net.ServerPresence;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.Optional;
import java.util.regex.Matcher;

/**
 * 聊天文本的解析与重写（客户端部分）。
 *
 * <p>纯字符串逻辑都在 {@link ChatText} 里，这个类只负责「把它画成什么样」：
 * 染色、加点击事件、把引用段拆成单独一行。</p>
 */
public final class ChatFormat {

    public static final String QUOTE_OPEN = ChatText.QUOTE_OPEN;
    public static final String QUOTE_CLOSE = ChatText.QUOTE_CLOSE;
    public static final String CLICK_MARKER = ChatText.CLICK_MARKER;

    private static final TextColor MENTION_ME = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
    private static final TextColor MENTION_OTHER = TextColor.fromRgb(0x5AA9FF);
    /** {@code @admin} 群体提及：红色，和普通的蓝色提及区分开，一眼看出这是叫管理员。 */
    private static final TextColor MENTION_ADMIN = TextColor.fromRgb(0xFF6B6B);

    /** 正在编辑的那条消息整条染成这个颜色（比提及蓝更亮一点，好区分）。 */
    public static final TextColor EDITING_COLOR = TextColor.fromRgb(0x66E0FF);

    private ChatFormat() {
    }

    /**
     * 把整条消息的所有片段统一刷成一个颜色。
     *
     * <p>不能用 {@code copy().setStyle(...)} —— {@link #decorate} 出来的每个片段都显式
     * 设过颜色，根节点上的样式根本传不下去，只能逐段重建。</p>
     */
    public static Component tint(Component source, TextColor color) {
        MutableComponent out = Component.empty();
        source.visit((style, string) -> {
            out.append(Component.literal(string).setStyle(style.withColor(color)));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** 名字字符集，和 Minecraft 用户名一致。 */
    public static boolean isNameChar(char c) {
        return ChatText.isNameChar(c);
    }

    /** 消息里有没有 @ 到 name（不区分大小写）。 */
    public static boolean mentions(String flat, String name) {
        return ChatText.mentions(flat, name);
    }

    public static String selfName() {
        String name = Minecraft.getInstance().getUser().getName();
        return name == null || name.isEmpty() ? null : name;
    }

    // ------------------------------------------------------------------
    // 渲染前的重写
    // ------------------------------------------------------------------

    /**
     * 把一条聊天消息改写成最终要显示的样子。
     *
     * <ul>
     *   <li>@ 到自己的消息：整条染成金色（提及部分再加粗），一眼就能看到；</li>
     *   <li>别人的消息：只有 {@code @名字} 这一段是蓝色，可以点。</li>
     *   <li>回复：额外在上一行插入灰色的引用原文。</li>
     * </ul>
     */
    public static Component decorate(Component original) {
        String flat = original.getString();
        // 绝大多数消息跟本 mod 无关，直接原样返回，避免无谓的重建。
        if (flat.indexOf('@') < 0) {
            return original;
        }

        String me = selfName();
        boolean forMe = me != null && ChatText.mentions(flat, me);

        Matcher quoted = ChatText.QUOTED.matcher(flat);
        if (!quoted.find()) {
            return rebuild(original, -1, -1, forMe);
        }

        String quote = quoted.group(2).replace('\n', ' ').replace('\r', ' ');
        if (quote.length() > ChatText.MAX_QUOTE) {
            quote = quote.substring(0, ChatText.MAX_QUOTE) + "\u2026";
        }

        MutableComponent out = Component.empty();
        out.append(Component.literal(QUOTE_OPEN + quote + QUOTE_CLOSE)
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)));
        // 换行：ChatHud 会按 \n 拆成多行，先出现的在上面，正好是「原文在上、回复在下」。
        out.append("\n");
        // 回复行里去掉 @name “原文” 这一段，只留正常聊天内容。
        out.append(rebuild(original, quoted.start(), quoted.end(), forMe));
        return out;
    }

    /**
     * 按样式分段重建文本。
     *
     * @param skipFrom 要丢弃的区间起点（-1 表示不丢弃）
     * @param skipTo   要丢弃的区间终点
     * @param forMe    这条消息有没有 @ 到自己
     */
    private static MutableComponent rebuild(Component source, int skipFrom, int skipTo, boolean forMe) {
        MutableComponent out = Component.empty();
        int[] offset = {0};
        FormattedText.StyledContentConsumer<Void> visitor = (style, string) -> {
            int start = offset[0];
            offset[0] += string.length();
            appendSegment(out, style, clip(string, start, skipFrom, skipTo), forMe);
            return Optional.empty();
        };
        source.visit(visitor, Style.EMPTY);
        return out;
    }

    private static String clip(String segment, int start, int skipFrom, int skipTo) {
        if (skipFrom < 0 || start >= skipTo || start + segment.length() <= skipFrom) {
            return segment;
        }
        StringBuilder sb = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            int absolute = start + i;
            if (absolute < skipFrom || absolute >= skipTo) {
                sb.append(segment.charAt(i));
            }
        }
        return sb.toString();
    }

    private static void appendSegment(MutableComponent out, Style style, String segment, boolean forMe) {
        if (segment.isEmpty()) {
            return;
        }
        // @ 到自己：整条消息都刷成金色
        Style base = forMe ? style.withColor(MENTION_ME) : style;

        Matcher m = ChatText.MENTION.matcher(segment);
        int last = 0;
        while (m.find()) {
            // 只挡 a@b.com 这种邮箱，Hi@Steve 照常当提及
            if (ChatText.isEmailLike(segment, m.start(), m.end())) {
                continue;
            }
            if (m.start() > last) {
                out.append(Component.literal(segment.substring(last, m.start())).setStyle(base));
            }
            out.append(Component.literal(m.group()).setStyle(mentionStyle(style, m.group(1), forMe)));
            last = m.end();
        }
        if (last < segment.length()) {
            out.append(Component.literal(segment.substring(last)).setStyle(base));
        }
    }

    private static Style mentionStyle(Style base, String name, boolean forMe) {
        boolean group = !forMe && isAdminGroup(name);
        TextColor color = forMe ? MENTION_ME : (group ? MENTION_ADMIN : MENTION_OTHER);
        return base.withColor(color)
                .withBold(forMe || group)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        CLICK_MARKER + "@" + name + " "));
    }

    /**
     * {@code @admin} 什么时候算「叫管理员」，而不是「@ 一个叫 admin 的人」。
     *
     * <p>两个条件缺一不可：名字就是 admin、玩家列表里没有真叫 admin 的。
     * 再加上服务端装了 mod —— 纯客户端环境下 {@code @admin} 没有任何特殊含义，
     * 画成红的只会骗人。</p>
     */
    private static boolean isAdminGroup(String name) {
        return ChatText.ADMIN_NAME.equalsIgnoreCase(name)
                && !isOnlinePlayer(ChatText.ADMIN_NAME)
                && ServerPresence.isActive();
    }

    // ------------------------------------------------------------------
    // 悬停图标用到的辅助
    // ------------------------------------------------------------------

    /**
     * 从一行聊天文本里找出「这行是谁说的」。
     *
     * <p>先把引用行剥掉再找：回复消息显示成 {@code “<AltPlayer> 原文”} 加
     * {@code <Player376> 正文} 两行，不剥的话从头扫会先撞上引用里的
     * {@code <AltPlayer>}，把这条消息错判成别人发的 —— 编辑 / 删除按钮就会
     * 挂到错误的人头上，点了服务端也认不出来。</p>
     *
     * <p>只认能在玩家列表里对上号的名字，所以进出服、死亡、命令反馈这类系统消息
     * 一律返回 null —— 它们本来就不该有按钮。</p>
     */
    public static String senderName(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String tail = ChatText.stripDisplayQuote(line).stripLeading();

        // 正常情况下发送者就在行首，优先按前缀认
        Matcher prefix = ChatText.ANGLE_PREFIX.matcher(tail);
        if (prefix.find() && isOnlinePlayer(prefix.group(1))) {
            return prefix.group(1);
        }

        Matcher angle = ChatText.ANGLE.matcher(tail);
        while (angle.find()) {
            String candidate = angle.group(1);
            if (isOnlinePlayer(candidate)) {
                return candidate;
            }
        }
        Matcher colon = ChatText.COLON.matcher(tail);
        while (colon.find()) {
            String candidate = colon.group(1);
            if (isOnlinePlayer(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean isOnlinePlayer(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            return false;
        }
        for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
            if (entry.getProfile().getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** 把一行聊天文本收拾成可以安全塞进 “…” 里当引用的一段字符串。 */
    public static String sanitizeQuote(String line) {
        return ChatText.sanitizeQuote(line);
    }
}
