package dev.mewzica.chatping;

import dev.mewzica.chatping.mixin.ChatHudAccessor;
import dev.mewzica.chatping.mixin.ChatScreenInvoker;
import dev.mewzica.chatping.net.Net;
import dev.mewzica.chatping.net.ServerPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
//? if >=26.1 {
/*import net.minecraft.client.multiplayer.chat.GuiMessage;
*///?} else {
import net.minecraft.client.GuiMessage;
//?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 消息编辑与删除的客户端侧。
 *
 * <p>两条链路：</p>
 * <ul>
 *   <li><b>发请求</b>：把「要动哪条」告诉服务端。定位不用消息 ID，
 *       用「发送者 + 正文」—— 正文是玩家自己敲的字，同一个人同一时刻不会连发两条一样的，
 *       服务端取最近一条就够，比维护一套全局消息 ID 简单得多。</li>
 *   <li><b>收广播</b>：服务端校验通过后广播 {@code chatping:update}，
 *       这边在本地聊天栏里找到对应行，改内容或整条删掉，再让原版重建折行。</li>
 * </ul>
 */
public final class ChatEditor {

    /** 正在编辑的那条消息在 {@code messages} 里的下标，-1 表示没在编辑。 */
    private static int editingIndex = -1;
    /** 被编辑那条消息原来的样子，退出编辑时要还原。 */
    private static GuiMessage editingOriginal;
    /** 被编辑消息的正文，发请求时用它定位。 */
    private static String editingBody;

    private ChatEditor() {
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    public static boolean isEditing() {
        return editingBody != null;
    }

    public static String editingBody() {
        return editingBody;
    }

    // ------------------------------------------------------------------
    // 进入 / 退出编辑
    // ------------------------------------------------------------------

    /**
     * 点铅笔：把原文塞进输入框，原消息染蓝，输入框上方挂「正在编辑消息」。
     */
    public static void beginEdit(ChatScreen screen, String senderName, String displayText) {
        if (!ServerPresence.isActive()) {
            return;
        }
        String body = ChatText.bodyOf(displayText);
        if (body.isEmpty()) {
            return;
        }
        int index = indexOf(senderName, body);
        if (index < 0) {
            return;
        }

        cancelEdit();

        List<GuiMessage> messages = messages();
        GuiMessage line = messages.get(index);
        editingIndex = index;
        editingOriginal = line;
        editingBody = body;

        // 原消息变蓝：整条消息的所有片段统一刷成青色，一眼能看出「这条正在改」。
        messages.set(index, withContent(line,
                ChatFormat.tint(line.content(), ChatFormat.EDITING_COLOR)));
        refresh();

        ((ChatScreenInvoker) screen).chatping$insertText(body, true);
        PendingReply.setEditing();
    }

    /** 退出编辑：还原颜色、清空预览条。 */
    public static void cancelEdit() {
        if (editingBody == null) {
            return;
        }
        List<GuiMessage> messages = messages();
        if (editingIndex >= 0 && editingIndex < messages.size() && editingOriginal != null) {
            messages.set(editingIndex, editingOriginal);
            refresh();
        }
        editingIndex = -1;
        editingOriginal = null;
        editingBody = null;
        PendingReply.clear();
    }

    /**
     * 按了回车、且当前在编辑模式。
     *
     * @return true 表示这次发送已经被当成「编辑」吃掉了，不要再走原版聊天
     */
    public static boolean commit(String newText) {
        if (editingBody == null) {
            return false;
        }
        String body = editingBody;
        cancelEdit();

        // 清空正文等于取消编辑，不发任何包
        if (newText == null || newText.isBlank() || !ServerPresence.isActive()) {
            return true;
        }

        ChatPingClient.LOGGER.info("commit edit: body=[{}] new=[{}]", body, newText);
        Net.sendEdit(body, newText);
        return true;
    }

    // ------------------------------------------------------------------
    // 删除
    // ------------------------------------------------------------------

    /**
     * 点删除按钮 → 确认 → 这里发请求。
     *
     * <p>上报三样东西，缺一不可：</p>
     * <ul>
     *   <li>{@code senderName} 让服务端在账本里定位（系统消息为空）；</li>
     *   <li>{@code body} 是账本里记的正文，和 {@code senderName} 一起唯一定位一条；</li>
     *   <li>{@code display} 是整行原文，账本里对不上时（系统消息 / 已过期）
     *       各客户端就靠它去认自己屏幕上那一行。</li>
     * </ul>
     */
    public static void requestDelete(String displayText) {
        if (!ServerPresence.isActive() || displayText == null || displayText.isEmpty()) {
            return;
        }
        String senderName = ChatFormat.senderName(displayText);
        String body = ChatText.bodyOf(displayText);
        // 自己正在编辑的就是这条，先退出编辑免得界面卡在蓝色状态
        if (body.equals(editingBody)) {
            cancelEdit();
        }
        ChatPingClient.LOGGER.info("request delete: sender={} body=[{}] display=[{}]",
                senderName, body, displayText);
        Net.sendPurge(senderName == null ? "" : senderName, body, displayText);
    }

    // ------------------------------------------------------------------
    // 收到服务端广播
    // ------------------------------------------------------------------

    /**
     * 服务端广播「这条被改了」。
     *
     * @param senderName 消息作者
     * @param oldBody    改之前那条消息的正文（用来定位）
     * @param newRaw     改之后的完整原文
     */
    public static void applyRemote(String senderName, String oldBody, String newRaw) {
        if (newRaw == null || newRaw.isEmpty()) {
            return;
        }
        List<GuiMessage> messages = messages();
        int index = indexOf(senderName, oldBody);
        ChatPingClient.LOGGER.info("update received: sender={} oldBody=[{}] newRaw=[{}] matchedIndex={}",
                senderName, oldBody, newRaw, index);
        if (index < 0) {
            return;
        }
        GuiMessage line = messages.get(index);

        // 正在编辑的这条被改了，编辑状态先撤掉，否则会指向一条已经不存在的内容
        if (oldBody.equals(editingBody)) {
            editingIndex = -1;
            editingOriginal = null;
            editingBody = null;
            PendingReply.clear();
        }

        messages.set(index, withContent(line,
                ChatFormat.decorate(Component.literal(newRaw))));
        refresh();
    }

    /**
     * 服务端广播「这条被删了」，各客户端自己把那一行摘掉。
     *
     * <p>删除不看发送者也不看账本 —— 管理员连系统消息都能删，那些条目服务端根本没有记录，
     * 所以只能靠整行文本认。先按整行精确比，对不上再退一步按正文比：
     * 别的 mod 改写过的行（加了前缀、改了颜色）整行对不上，但正文还能对上。</p>
     */
    public static void applyPurge(String displayText) {
        if (displayText == null || displayText.isEmpty()) {
            return;
        }
        List<GuiMessage> messages = messages();
        String target = ChatText.flattenNewlines(displayText);
        String targetBody = ChatText.bodyOf(displayText);

        int index = -1;
        for (int i = 0; i < messages.size(); i++) {
            // messages 是 addFirst 存的，下标 0 就是最新那条；同文本重复时先命中的也是最新的
            String text = ChatText.flattenNewlines(messages.get(i).content().getString());
            if (text.equals(target)) {
                index = i;
                break;
            }
        }
        if (index < 0 && !targetBody.isEmpty()) {
            for (int i = 0; i < messages.size(); i++) {
                if (ChatText.bodyOf(messages.get(i).content().getString()).equals(targetBody)) {
                    index = i;
                    break;
                }
            }
        }
        ChatPingClient.LOGGER.info("remove received: display=[{}] matchedIndex={}", displayText, index);
        if (index < 0) {
            return;
        }

        if (index == editingIndex) {
            editingIndex = -1;
            editingOriginal = null;
            editingBody = null;
            PendingReply.clear();
        } else if (editingIndex > index) {
            editingIndex--;
        }
        messages.remove(index);
        refresh();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static List<GuiMessage> messages() {
        return ((ChatHudAccessor) ClientCompat.chat()).chatping$getMessages();
    }

    private static void refresh() {
        ChatComponent hud = ClientCompat.chat();
        ((ChatHudAccessor) hud).chatping$refresh();
    }

    /**
     * 拿一条已有消息当模板，只换正文。
     *
     * <p>26.1 给 {@code GuiMessage} 加了第四个字段 {@code source}（记录这条是玩家说的
     * 还是系统消息），构造器从 4 个参数变成 5 个。原地改一条消息时把原对象的
     * {@code source} 原样带过去，语义才不会变。</p>
     */
    private static GuiMessage withContent(GuiMessage line, Component content) {
        //? if >=26.1 {
        /*return new GuiMessage(line.addedTime(), content, line.signature(),
                line.source(), line.tag());
        *///?} else {
        return new GuiMessage(line.addedTime(), content, line.signature(), line.tag());
        //?}
    }

    /**
     * 在聊天栏里找「senderName 说的、正文是 body」的那一条，取最新的。
     *
     * @return 下标，找不到返回 -1
     */
    private static int indexOf(String senderName, String body) {
        if (senderName == null || body == null || body.isEmpty()) {
            return -1;
        }
        List<GuiMessage> messages = messages();
        for (int i = 0; i < messages.size(); i++) {
            String text = messages.get(i).content().getString();
            String who = ChatFormat.senderName(text);
            if (who == null || !who.equalsIgnoreCase(senderName)) {
                continue;
            }
            if (ChatText.bodyOf(text).equals(body)) {
                return i;
            }
        }
        return -1;
    }
}
