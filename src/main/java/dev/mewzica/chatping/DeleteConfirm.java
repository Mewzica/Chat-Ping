package dev.mewzica.chatping;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 删除消息的确认框。
 *
 * <p>用户要的是「点了删除按钮之后，在按钮旁边弹一个确认框，UI 要原版风格」。
 * 原版没有这种贴边小面板，所以这里按原版的两套现成视觉拼出来：</p>
 * <ul>
 *   <li>面板底和边框照抄原版 tooltip（深紫黑底 {@code #100010} + 紫色描边），
 *       一眼就是「Minecraft 的东西」；</li>
 *   <li>两个按钮按原版按钮的配色手绘（{@code #6C6C6C} 底、上左高光、下右暗边、
 *       悬停转 {@code #76849B}），不用 GUI 贴图 —— 那套 sprite 在 1.20.1 之后改过好几次，
 *       后面做多版本时手绘反而稳。</li>
 * </ul>
 *
 * <p>标题下面那行小字是给管理员看的：删自己的消息和删别人的 / 系统消息，
 * 责任完全不一样，点下去之前得知道自己在删什么。</p>
 *
 * <p>坐标是屏幕坐标，直接画在 {@code ChatScreen.render} 的末尾，不受聊天缩放影响。</p>
 */
public final class DeleteConfirm {

    private static final int PANEL_W = 132;
    private static final int PANEL_H = 44;
    private static final int BTN_W = 60;
    private static final int BTN_H = 12;

    // 原版 tooltip 配色
    private static final int PANEL_BG = 0xF0100010;
    private static final int BORDER_OUTER = 0xFF5000FF;
    private static final int BORDER_INNER = 0xFF28007F;

    // 原版按钮配色
    private static final int BTN_BG = 0xFF6C6C6C;
    private static final int BTN_BG_HOVER = 0xFF76849B;
    private static final int BTN_LIGHT = 0xFF8B8B8B;
    private static final int BTN_DARK = 0xFF3F3F3F;
    private static final int BTN_TEXT = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int SUBTITLE_COLOR = 0xFF9A9A9A;

    private static boolean open;
    private static String displayText;
    /** 副标题（谁的消息）已经是翻译后的组件，语言切换后重开一次就是新语言。 */
    private static Component subtitle;

    private static int panelX;
    private static int panelY;

    private DeleteConfirm() {
    }

    public static boolean isOpen() {
        return open;
    }

    /**
     * 在鼠标位置弹出来。
     *
     * @param displayTextIn 要删的那一行的完整显示文本，确认后原样交给服务端
     * @param mouseX        点击删除按钮时的屏幕 x，面板右边缘对齐到这里
     * @param mouseY        点击删除按钮时的屏幕 y，面板顶边从这里往下一点
     */
    public static void open(String displayTextIn, double mouseX, double mouseY,
                            int screenWidth, int screenHeight) {
        open = true;
        displayText = displayTextIn;

        String who = ChatFormat.senderName(displayTextIn);
        subtitle = who == null
                ? Component.translatable("chatping.delete.subtitle.system")
                : Component.translatable("chatping.delete.subtitle.player", who);

        int x = (int) Math.round(mouseX) - PANEL_W + 4;
        int y = (int) Math.round(mouseY) + 4;
        // 别跑出屏幕
        x = Math.max(2, Math.min(x, screenWidth - PANEL_W - 2));
        y = Math.max(2, Math.min(y, screenHeight - PANEL_H - 2));
        panelX = x;
        panelY = y;
    }

    public static void close() {
        open = false;
        displayText = null;
        subtitle = null;
    }

    public static void render(Object context, Font textRenderer, int mouseX, int mouseY) {
        if (!open) {
            return;
        }

        // tooltip 风格：外层紫边 + 内层深紫边 + 深底
        Draw.fill(context, panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BORDER_OUTER);
        Draw.fill(context, panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + PANEL_H - 1, BORDER_INNER);
        Draw.fill(context, panelX + 2, panelY + 2, panelX + PANEL_W - 2, panelY + PANEL_H - 2, PANEL_BG);

        Component title = Component.translatable("chatping.delete.title");
        int titleX = panelX + (PANEL_W - textRenderer.width(title)) / 2;
        Draw.text(context, textRenderer, title, titleX, panelY + 6, TEXT_COLOR);

        int subX = panelX + (PANEL_W - textRenderer.width(subtitle)) / 2;
        Draw.text(context, textRenderer, subtitle, subX, panelY + 18, SUBTITLE_COLOR);

        int btnY = panelY + PANEL_H - BTN_H - 4;
        int yesX = panelX + 4;
        int noX = panelX + PANEL_W - BTN_W - 4;

        drawButton(context, textRenderer, yesX, btnY,
                Component.translatable("chatping.delete.confirm"), isInside(mouseX, mouseY, yesX, btnY));
        drawButton(context, textRenderer, noX, btnY,
                Component.translatable("chatping.delete.cancel"), isInside(mouseX, mouseY, noX, btnY));
    }

    /**
     * @return true 表示这次点击被确认框吃掉了（点按钮 / 点面板 / 点面板外都算）
     */
    public static boolean mouseClicked(double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        int btnY = panelY + PANEL_H - BTN_H - 4;
        int yesX = panelX + 4;
        int noX = panelX + PANEL_W - BTN_W - 4;

        if (isInside(mouseX, mouseY, yesX, btnY)) {
            String text = displayText;
            close();
            ChatEditor.requestDelete(text);
            return true;
        }
        // 点「取消」或者点面板外面都算放弃，点击都要吃掉，
        // 否则会穿透到聊天栏、顺手触发下层的 @ / 回复 按钮。
        close();
        return true;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H;
    }

    private static void drawButton(Object context, Font textRenderer,
                                   int x, int y, Component label, boolean hover) {
        Draw.fill(context, x, y, x + BTN_W, y + BTN_H, hover ? BTN_BG_HOVER : BTN_BG);
        Draw.fill(context, x, y, x + BTN_W, y + 1, hover ? 0xFF9AA8BF : BTN_LIGHT);
        Draw.fill(context, x, y, x + 1, y + BTN_H, hover ? 0xFF9AA8BF : BTN_LIGHT);
        Draw.fill(context, x, y + BTN_H - 1, x + BTN_W, y + BTN_H, BTN_DARK);
        Draw.fill(context, x + BTN_W - 1, y, x + BTN_W, y + BTN_H, BTN_DARK);

        int textX = x + (BTN_W - textRenderer.width(label)) / 2;
        Draw.text(context, textRenderer, label, textX, y + 2, BTN_TEXT);
    }
}
