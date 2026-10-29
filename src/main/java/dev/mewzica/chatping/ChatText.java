package dev.mewzica.chatping;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯字符串层面的聊天文本处理，客户端和服务端共用。
 *
 * <p>这个类刻意不引用任何 {@code net.minecraft.client.*}，所以专用服务端也能安全加载。
 * 带渲染、带 {@code MinecraftClient} 的那些逻辑留在 {@link ChatFormat} 里。</p>
 *
 * <p>线上格式（玩家真正敲进聊天框的那串纯文本）：</p>
 * <pre>
 *   提及：  &#64;Steve 你好
 *   回复：  &#64;Steve “&lt;Steve&gt; 原文” 你的回复
 * </pre>
 */
public final class ChatText {

    /** @Steve */
    public static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_]{1,16})");

    /** @Steve “原文” */
    public static final Pattern QUOTED = Pattern.compile("@([A-Za-z0-9_]{1,16}) \u201C([^\u201D]*)\u201D ?");

    /** 原版聊天格式 &lt;Steve&gt; 文本 */
    public static final Pattern ANGLE = Pattern.compile("<([A-Za-z0-9_]{1,16})>");

    /**
     * 群体提及的目标名：{@code @admin} 一次叫上所有在线管理员。
     *
     * <p>不做成「角色系统」是因为代价完全不对等 —— 一个固定的保留名字就够用了，
     * 而且它天然会被当成普通文本发出去，原版客户端也能收到，不需要任何额外协议。</p>
     */
    public static final String ADMIN_NAME = "admin";

    /**
     * {@code @admin}，且后面不能紧跟名字字符。
     *
     * <p>为什么不用 {@link #MENTION} 取 group 再比：那样 {@code @administrator}
     * 会被拆成 {@code administrator}、{@code @admin_x} 会被拆成 {@code admin_x}，
     * 虽然对 {@code admin} 的判断结果碰巧一样，但语义上是「@ 了一个长名字的人」，
     * 不该把全体管理员叫起来。这里直接卡死右边界。</p>
     */
    public static final Pattern ADMIN_MENTION =
            Pattern.compile("@" + ADMIN_NAME + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);

    /** 行首的 &lt;Steve&gt; 前缀（带一个可选空格） */
    public static final Pattern ANGLE_PREFIX = Pattern.compile("^<([A-Za-z0-9_]{1,16})> ?");

    /** 部分服务端的 Steve: 文本 */
    public static final Pattern COLON = Pattern.compile("(?:^|\\s)([A-Za-z0-9_]{1,16})\\s*[:：]");

    /**
     * 聊天栏里「引用行」的样子：开头一对引号包起来，后面跟空白或换行。
     *
     * <p>回复消息显示成两行 —— 上面一行 {@code “原文”}、下面一行 {@code <名字> 正文}。
     * 聊天栏折行之后这两行会被拼回同一串，所以剥正文之前得先把这段引用去掉，
     * 否则引用里出现的 {@code <别人>} 会被当成发送者、正文也会整串带进去。</p>
     */
    public static final Pattern DISPLAY_QUOTE = Pattern.compile("^\u201C[^\u201D]*\u201D[ \n]*");

    public static final String QUOTE_OPEN = "\u201C";   // “
    public static final String QUOTE_CLOSE = "\u201D";  // ”

    /** 点击提及时代码自己处理，用一个不可能出现在正常文本里的前缀做标记。 */
    public static final String CLICK_MARKER = "\u0001";

    /** 引用原文最多保留多少个字符，太长会把聊天栏撑爆。 */
    public static final int MAX_QUOTE = 90;

    private ChatText() {
    }

    /** 名字字符集，和 Minecraft 用户名一致。 */
    public static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    /**
     * 这个 {@code @} 是不是 e-mail 里的那个（{@code a@b.com}），是的话就不当提及。
     *
     * <p>以前的做法是「{@code @} 前面紧贴名字字符就一律跳过」，本意是挡邮箱，
     * 但顺手把 {@code Hi@Steve} 这种「不空格直接 @」也挡掉了 —— 中文玩家尤其爱这么打，
     * 结果就是被 @ 的人既没有提示音也没有高亮，看着像 mod 坏了。</p>
     *
     * <p>现在只认真正的邮箱形状：前面紧贴名字字符 <b>并且</b> 名字后面紧跟
     * {@code .} + 字母数字（{@code b.com} 里的 {@code .com}）。这样
     * {@code a@b.com} 被挡掉，{@code Hi@Steve}、{@code 2023@Steve} 正常算提及。</p>
     *
     * @param flat    整串文本
     * @param at      {@code @} 的下标
     * @param nameEnd 名字结束后的下标（即 {@code m.end()}）
     */
    public static boolean isEmailLike(String flat, int at, int nameEnd) {
        if (at == 0 || !isNameChar(flat.charAt(at - 1))) {
            return false;
        }
        return nameEnd + 1 < flat.length()
                && flat.charAt(nameEnd) == '.'
                && isNameChar(flat.charAt(nameEnd + 1));
    }

    /**
     * 消息里有没有 @ 到 name（不区分大小写）。
     *
     * <p>引用段里的 {@code @} 同样算数 —— 回复一条 @ 过别人的消息时，被引用的那个人
     * 也要收到提示音，不然「我回复的那句话本来就在叫他」这件事就丢了。</p>
     */
    public static boolean mentions(String flat, String name) {
        if (flat == null || name == null || name.isEmpty()) {
            return false;
        }
        Matcher m = MENTION.matcher(flat);
        while (m.find()) {
            if (isEmailLike(flat, m.start(), m.end())) {
                continue;
            }
            if (m.group(1).equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 消息里有没有 {@code @admin}。
     *
     * <p>调用方还得自己确认「没有真名叫 admin 的玩家」再当成群体提及。
     * 边界判断和 {@link #mentions} 保持一致，否则会出现「服务端响了、客户端没高亮」这种割裂。</p>
     */
    public static boolean mentionsAdmins(String flat) {
        if (flat == null) {
            return false;
        }
        Matcher m = ADMIN_MENTION.matcher(flat);
        while (m.find()) {
            if (isEmailLike(flat, m.start(), m.end())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 剥掉 {@code @名字 “原文” } 这一段前缀，剩下真正的正文。
     *
     * <p>服务端拿到的原始聊天内容就是玩家敲的那串，回复消息里带着引用段；
     * 而客户端界面上引用段已经被拆成单独一行了。两边都要能还原出同一个「正文」，
     * 编辑 / 删除请求才匹配得上。</p>
     */
    public static String stripQuote(String raw) {
        if (raw == null) {
            return "";
        }
        Matcher m = QUOTED.matcher(raw);
        if (m.find() && m.start() == 0) {
            return raw.substring(m.end()).trim();
        }
        return raw.trim();
    }

    /**
     * 剥掉聊天栏显示时加在最前面的引用行。
     *
     * <p>只剥开头的 —— 普通消息前面有 {@code <名字> }，不会以引号开头，
     * 所以玩家正文里自带引号也不会被误伤。</p>
     */
    public static String stripDisplayQuote(String displayText) {
        if (displayText == null) {
            return "";
        }
        Matcher m = DISPLAY_QUOTE.matcher(displayText);
        if (m.find() && m.start() == 0) {
            return displayText.substring(m.end());
        }
        return displayText;
    }

    /**
     * 从「已经显示在聊天栏上的一条消息」里剥出正文。
     *
     * <p>先去掉引用行，再去掉 {@code <名字>} 前缀。结果和服务端那边的
     * {@link #stripQuote} 是一致的 —— 编辑 / 删除请求就是靠这个对上账的。</p>
     */
    public static String bodyOf(String displayText) {
        if (displayText == null || displayText.isEmpty()) {
            return "";
        }
        String s = stripDisplayQuote(displayText).stripLeading();
        Matcher m = ANGLE_PREFIX.matcher(s);
        if (m.find()) {
            s = s.substring(m.end());
        }
        return s.trim();
    }

    /** 把一行聊天文本收拾成可以安全塞进 “…” 里当引用的一段字符串。 */
    public static String sanitizeQuote(String line) {
        if (line == null) {
            return "";
        }
        String s = line.replace('\n', ' ').replace('\r', ' ')
                .replace(QUOTE_OPEN, "\"")
                .replace(QUOTE_CLOSE, "\"");
        if (s.length() > MAX_QUOTE) {
            s = s.substring(0, MAX_QUOTE) + "\u2026";
        }
        return s.trim();
    }

    /** 去掉换行，用来把 {@code ChatHudLine.content().getString()} 和逐行拼接的结果对齐。 */
    public static String flattenNewlines(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\n", "").replace("\r", "");
    }
}
