package dev.mewzica.chatping.server;

import dev.mewzica.chatping.ChatText;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.UUID;

/**
 * 服务端自己的消息账本。
 *
 * <p>用途有两个：</p>
 * <ol>
 *   <li><b>定位</b>：客户端说「我要改/删我刚发的『你好』」，服务端得知道那是哪一条。
 *       这里按「发送者 + 正文」倒着找最近一条，同一个人同一时刻不会连发两条一模一样的，
 *       够用了，也省掉一整套全局消息 ID 的下发与同步。</li>
 *   <li><b>留档</b>：用户明确要求「删掉的消息服务端要保留日志」。
 *       所以删除只是把 {@link Entry#deleted} 打上，条目不从队列里拿掉。</li>
 * </ol>
 *
 * <p>纯内存，重启即清空 —— 聊天记录本来也不该往磁盘上写。</p>
 */
public final class MessageLog {

    /** 留多久之前的消息还允许被编辑 / 删除。 */
    private static final long TTL_MS = 30L * 60L * 1000L;
    /** 最多记多少条，超了从最旧的开始丢。 */
    private static final int MAX_ENTRIES = 500;

    public static final class Entry {
        public final UUID sender;
        public final String senderName;
        public final long time;
        /**
         * 原版格式化之后、正文之前的那一段，通常就是 {@code <Player376> }。
         *
         * <p>必须留着：玩家敲的原文里没有名字前缀，而聊天栏里显示的那条有。
         * 编辑之后如果只把原文广播回去，客户端就会把整行换成没有名字的裸文本，
         * 于是 {@code senderName()} 找不到发送者，编辑 / 删除按钮也跟着消失，
         * 第二次就再也改不动了。</p>
         */
        public final String prefix;
        /** 玩家敲的原文，可能带 {@code @名字 “原文” } 引用段。 */
        public String raw;
        /** 剥掉引用段后的正文，用来和客户端上报的内容对上。 */
        public String body;
        /** 已经被删除：还留在账本里，但不再允许二次操作。 */
        public boolean deleted;

        Entry(UUID sender, String senderName, long time, String prefix, String raw, String body) {
            this.sender = sender;
            this.senderName = senderName;
            this.time = time;
            this.prefix = prefix;
            this.raw = raw;
            this.body = body;
        }
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private MessageLog() {
    }

    /**
     * 记一条账。
     *
     * @param raw       玩家敲的原文（{@code SignedMessage#getSignedContent()}）
     * @param formatted 原版格式化之后真正发出去的那串（{@code SignedMessage#getContent()}），
     *                  用来把 {@code <名字> } 前缀抠出来
     */
    public static synchronized Entry record(UUID sender, String senderName, String raw, String formatted) {
        long now = System.currentTimeMillis();
        Entry entry = new Entry(sender, senderName, now, extractPrefix(formatted, raw, senderName), raw,
                ChatText.stripQuote(raw));
        ENTRIES.addLast(entry);
        prune(now);
        return entry;
    }

    /**
     * 切出正文之前的前缀，通常是 {@code <Player376> }。
     *
     * <p>{@code SignedMessage#getContent()} 给的是玩家敲的原文，<b>本来就不带名字前缀</b> ——
     * 前缀是服务端广播时才按 {@code chat.type.text} 格式拼上去的，聊天事件里根本拿不到。
     * 所以这里的 {@code formatted} 多数情况下就等于 {@code raw}，按长度差截出来是空串。
     * 一旦广播空前缀，客户端整行替换之后名字就没了，{@code senderName()} 也就认不出
     * 这条是谁发的，编辑 / 删除按钮跟着一起消失。所以拿不到就按原版默认格式补一个。</p>
     *
     * <p>万一 {@code formatted} 真带了别的装饰（队伍前缀之类）且能被 {@code raw} 对上尾巴，
     * 就用真实的那个，比硬编码更准。</p>
     */
    private static String extractPrefix(String formatted, String raw, String senderName) {
        if (formatted != null && raw != null
                && formatted.length() > raw.length()
                && formatted.endsWith(raw)) {
            return formatted.substring(0, formatted.length() - raw.length());
        }
        if (senderName == null || senderName.isEmpty()) {
            return "";
        }
        return "<" + senderName + "> ";
    }

    /** 找这个玩家最近发的、正文是 body 的那条；已删除的和过期的都不算。 */
    public static synchronized Entry find(UUID sender, String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Iterator<Entry> it = ENTRIES.descendingIterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (now - entry.time > TTL_MS) {
                break;  // 倒序，再往前只会更旧
            }
            if (entry.deleted) {
                continue;
            }
            if (entry.sender.equals(sender) && entry.body.equals(body)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 按「发送者名字 + 正文」找最近一条。
     *
     * <p>删除请求是客户端发来的，客户端只知道屏幕上那行写的是谁、正文是什么，
     * 拿不到 {@code UUID}，所以这里只能按名字匹配。名字大小写不敏感 ——
     * 原版聊天里显示的名字可能被队伍前缀改过大小写，但玩家列表里的名字不会。</p>
     */
    public static synchronized Entry findByName(String senderName, String body) {
        if (senderName == null || senderName.isEmpty() || body == null || body.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Iterator<Entry> it = ENTRIES.descendingIterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (now - entry.time > TTL_MS) {
                break;
            }
            if (entry.deleted) {
                continue;
            }
            if (entry.senderName.equalsIgnoreCase(senderName) && entry.body.equals(body)) {
                return entry;
            }
        }
        return null;
    }

    private static void prune(long now) {
        while (!ENTRIES.isEmpty()) {
            Entry oldest = ENTRIES.peekFirst();
            if (oldest != null && (ENTRIES.size() > MAX_ENTRIES || now - oldest.time > TTL_MS)) {
                ENTRIES.pollFirst();
            } else {
                break;
            }
        }
    }
}
