package dev.mewzica.chatping;

import dev.mewzica.chatping.mixin.ChatScreenInvoker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 输入框上方那条「待发送状态」预览。
 *
 * <p>两种模式共用同一块地方：</p>
 * <ul>
 *   <li><b>回复</b>：点聊天栏的 ↩ 之后挂上，显示 {@code ↩ “原文”}，
 *       发送时拼成 {@code @名字 “原文” 正文}；</li>
 *   <li><b>编辑</b>：点铅笔之后挂上，显示 {@code ✎ 正在编辑消息}，
 *       发送时不拼任何东西，走 {@link ChatEditor#commit}。</li>
 * </ul>
 *
 * <p>右边都有个 x，点了就取消。</p>
 */
public final class PendingReply {

    public enum Mode {
        NONE, REPLY, EDIT
    }

    private static final int BAR_H = 13;
    private static final int BG = 0xF0101014;
    private static final int FG_QUOTE = 0xFFA8A8A8;
    private static final int FG_EDIT = 0xFF66E0FF;
    private static final int FG_X = 0xFFAAAAAA;
    private static final int FG_X_HOVER = 0xFFFF6666;

    private static Mode mode = Mode.NONE;
    private static String target;
    private static String quote;

    /** x 按钮的屏幕区域，render 时算好，点击时直接用。 */
    private static int xLeft;
    private static int xTop;
    private static int xRight;
    private static int xBottom;

    private PendingReply() {
    }

    public static void set(String targetName, String quoted) {
        mode = Mode.REPLY;
        target = targetName;
        quote = quoted;
    }

    public static void setEditing() {
        mode = Mode.EDIT;
        target = null;
        quote = null;
    }

    public static void clear() {
        mode = Mode.NONE;
        target = null;
        quote = null;
    }

    public static boolean has() {
        return mode != Mode.NONE;
    }

    public static boolean isEditing() {
        return mode == Mode.EDIT;
    }

    /** 发送时拼在正文前面的那一段（只有回复模式才有）。 */
    public static String prefix() {
        if (mode != Mode.REPLY) {
            return "";
        }
        String mention = target != null ? "@" + target + " " : "";
        return mention + ChatText.QUOTE_OPEN + quote + ChatText.QUOTE_CLOSE + " ";
    }

    public static void render(Object context, Font textRenderer, ChatScreen screen,
                              int mouseX, int mouseY) {
        if (!has()) {
            return;
        }
        EditBox field = ((ChatScreenInvoker) screen).chatping$getChatField();

        int left = field.getX() - 2;
        int right = field.getX() + field.getWidth() + 2;
        int bottom = field.getY() - 2;
        int top = bottom - BAR_H;

        Draw.fill(context, left, top, right, bottom, BG);

        xRight = right - 2;
        xLeft = xRight - 9;
        xTop = top + 2;
        xBottom = bottom - 2;

        boolean hoverX = mouseX >= xLeft && mouseX <= xRight && mouseY >= xTop && mouseY <= xBottom;

        if (mode == Mode.EDIT) {
            // 走语言文件，切语言自动跟着变；取成 String 只是为了按像素宽度截断
            String shown = textRenderer.plainSubstrByWidth(
                    Component.translatable("chatping.edit.banner").getString(), xLeft - left - 6);
            Draw.text(context, textRenderer, shown, left + 3, top + 2, FG_EDIT);
        } else {
            String label = "\u21A9 " + ChatText.QUOTE_OPEN + quote + ChatText.QUOTE_CLOSE;
            String shown = textRenderer.plainSubstrByWidth(label, xLeft - left - 6);
            Draw.text(context, textRenderer, shown, left + 3, top + 2, FG_QUOTE);
        }
        Draw.text(context, textRenderer, "x", xLeft + 1, top + 2,
                hoverX ? FG_X_HOVER : FG_X);
    }

    /** 点到了 x 就取消当前这条待发送状态。 */
    public static boolean clickCancel(double mouseX, double mouseY) {
        if (!has()) {
            return false;
        }
        if (mouseX >= xLeft && mouseX <= xRight && mouseY >= xTop && mouseY <= xBottom) {
            boolean wasEditing = mode == Mode.EDIT;
            if (wasEditing) {
                // 编辑模式要连颜色一起还原，交给 ChatEditor 处理
                ChatEditor.cancelEdit();
                clear();
            } else {
                clear();
            }
            return true;
        }
        return false;
    }
}
