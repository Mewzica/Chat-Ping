package dev.mewzica.chatping;

import dev.mewzica.chatping.mixin.ChatHudAccessor;
import dev.mewzica.chatping.net.ServerPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.FormattedCharSequence;

/**
 * 聊天栏上的悬停按钮。
 *
 * <p>原版聊天栏没有「鼠标悬停某一行的区域」这个概念，所以这里的做法是：
 * 在 {@code ChatComponent.render} 每画完一行文字时回调进来，用这一行的 y 坐标反推出
 * 它的矩形范围，判断鼠标是不是停在这一行上。</p>
 *
 * <p>一条消息可能因为太长被折成好几行，所以按钮统一画在「这一条消息的最后一行」，
 * 而且只画一组 —— 否则一条长消息会在每行末尾都冒出一对图标，看起来会很乱。</p>
 *
 * <p>图标从右往左是 {@code ↩ @ ✎ ✕}，位置从右边缘固定往左排，所以有没有 ✎ ✕
 * 都不会让 {@code @ ↩} 左右乱跳。谁能看到哪些：</p>
 * <ul>
 *   <li>别人的玩家消息：{@code @ ↩}；管理员额外多一个 {@code ✕}；</li>
 *   <li>自己的玩家消息：{@code @ ↩ ✎ ✕}；</li>
 *   <li>系统消息：普通玩家什么都不显示，管理员只显示一个 {@code ✕}。</li>
 * </ul>
 *
 * <p>后两种都要服务端也装了 mod 才给 —— 编辑 / 删除是广播给所有人的，
 * 纯客户端环境下只能改自己的屏幕，不如不显示。</p>
 */
public final class ChatPingHud {

    /** 鼠标当前压在哪一个图标上。 */
    public enum Hover {
        NONE, AT, REPLY, EDIT, DELETE
    }

    /** 单个图标的点击区宽度（缩放后像素）。 */
    private static final int ICON_W = 11;
    private static final int GAP = 1;

    private static final String AT_GLYPH = "@";
    /** U+21A9，回形箭头，取自 unifont，原版字体里一定有。 */
    private static final String REPLY_GLYPH = "\u21A9";
    /** U+270E，铅笔。 */
    private static final String EDIT_GLYPH = "\u270E";
    /** U+2715，叉号。 */
    private static final String DELETE_GLYPH = "\u2715";

    private static final int COLOR_NORMAL = 0xFFE6E6E6;
    private static final int COLOR_HOVER = 0xFFFFE066;
    private static final int COLOR_DELETE_HOVER = 0xFFFF6666;
    private static final int COLOR_BACKDROP = 0xF0121218;

    /** 鼠标压在哪个图标上（每帧重算）。 */
    public static Hover hover = Hover.NONE;
    /** 鼠标所在那条消息的完整显示文本，没有悬停在任何消息上时为 null。 */
    public static String hoveredLine;
    /** 这条消息是不是自己发的（只有自己发的才给编辑 / 删除）。 */
    public static boolean hoveredOwn;

    private static int mouseX;
    private static int mouseY;

    /** 由 Visible.content() 的拦截喂进来：当前这一行是不是这条消息的最后一行。 */
    private static boolean endOfEntry;

    /** 当前正在扫描的这条消息，逐行拼起来的完整文本。 */
    private static final StringBuilder entryText = new StringBuilder();
    /** 上一条被扫描的行是不是某条消息的结尾（用来判断新消息从哪开始）。 */
    private static boolean previousWasEnd = true;
    /** 鼠标已经落在当前这条消息的范围内，等它最后一行出现时再画按钮。 */
    private static boolean insideEntry;

    private ChatPingHud() {
    }

    /** 每帧渲染开始时重置状态。 */
    public static void beginFrame(int mx, int my) {
        mouseX = mx;
        mouseY = my;
        hover = Hover.NONE;
        hoveredLine = null;
        hoveredOwn = false;
        endOfEntry = false;
        entryText.setLength(0);
        previousWasEnd = true;
        insideEntry = false;
    }

    /** 由 {@code ChatComponentLine.Visible#content()} 的拦截喂入。 */
    public static void setEndOfEntry(boolean value) {
        endOfEntry = value;
    }

    /**
     * 由 {@code ChatComponent.render} 里每一行的文字绘制回调进来。
     *
     * @param y 这一行文字在聊天坐标系里的顶部 y
     */
    public static void onLineRendered(ChatComponent hud, Object context, Font textRenderer,
                                      FormattedCharSequence text, int y) {
        String flat = flatten(text);
        if (previousWasEnd) {
            entryText.setLength(0);
        }
        entryText.append(flat);
        previousWasEnd = endOfEntry;

        Minecraft client = Minecraft.getInstance();
        if (!(ClientCompat.screen() instanceof ChatScreen)) {
            return;
        }

        // getScale()/getWidth() 在 1.21.11 变成了 private，只能走 accessor
        double scale = ((ChatHudAccessor) hud).chatping$getScale();
        if (scale <= 0.0D) {
            return;
        }

        double spacing = client.options.chatLineSpacing().get();
        int lineHeight = (int) (9.0D * (spacing + 1.0D));
        // 与原版 ChatComponent.render 里那个 lineOffset 完全一致，用来把文字 y 反推成整行的上下边界。
        int lineOffset = (int) Math.round(-8.0D * (spacing + 1.0D) + 4.0D * spacing);

        double localX = mouseX / scale - 4.0D;
        double localY = mouseY / scale;
        double top = y - lineOffset - lineHeight;
        double bottom = y - lineOffset;
        if (localY >= top && localY < bottom) {
            insideEntry = true;
        }
        if (!insideEntry || !endOfEntry) {
            return;
        }
        // 走到这条消息的最后一行了，按钮画在这里
        insideEntry = false;

        String line = entryText.toString();
        String sender = ChatFormat.senderName(line);
        // 系统消息（进出服 / 死亡 / 命令反馈 / 服务器公告）没有玩家发送者
        boolean system = sender == null;
        String self = ChatFormat.selfName();
        boolean own = !system && self != null && self.equalsIgnoreCase(sender);
        // 编辑 / 删除要靠服务端广播给所有人，纯客户端环境下自己改只对自己生效，不如不给
        boolean remote = ServerPresence.isActive();
        boolean canEdit = own && remote;
        // 管理员什么都能删；普通玩家只能删自己的。
        // 「是不是管理员」由服务端按 LuckPerms 的 chatping.delete.others 算好发过来，
        // 客户端不自己看 op 等级 —— 有权限节点但没 op 的玩家也能删。
        boolean canDelete = remote && (own || ServerPresence.canDeleteOthers());

        // 普通玩家看系统消息：一个按钮都不给
        if (system && !canDelete) {
            return;
        }

        hoveredLine = line;
        hoveredOwn = own;

        int width = (int) Math.ceil(((ChatHudAccessor) hud).chatping$getChatWidth() / scale);
        // 从右边缘往左依次占槽。@ ↩ 的位置和以前完全一样，
        // 这样别人的消息多出一个 ✕ 时，@ ↩ 也不会跟着左右乱跳。
        int slot = width - ICON_W;
        int replyLeft = -1;
        int atLeft = -1;
        int editLeft = -1;
        int deleteLeft = -1;

        if (!system) {
            replyLeft = slot;
            slot -= GAP + ICON_W;
            atLeft = slot;
            slot -= GAP + ICON_W;
        }
        if (canEdit) {
            editLeft = slot;
            slot -= GAP + ICON_W;
        }
        if (canDelete) {
            deleteLeft = slot;
        }

        int leftmost;
        if (system) {
            leftmost = deleteLeft;
        } else if (canDelete) {
            leftmost = deleteLeft;
        } else if (canEdit) {
            leftmost = editLeft;
        } else {
            leftmost = atLeft;
        }

        boolean inZone = localX >= leftmost - 2 && localX <= width;
        if (inZone) {
            if (canDelete && localX >= deleteLeft && localX < deleteLeft + ICON_W) {
                hover = Hover.DELETE;
            } else if (canEdit && localX >= editLeft && localX < editLeft + ICON_W) {
                hover = Hover.EDIT;
            } else if (!system && localX >= replyLeft && localX < replyLeft + ICON_W) {
                hover = Hover.REPLY;
            } else if (!system && localX >= atLeft && localX < atLeft + ICON_W) {
                hover = Hover.AT;
            }
        }

        // 不透明底衬，避免长消息的文字从图标底下透出来
        Draw.fill(context, leftmost - 3, y - 2, width + 1, y + 11, COLOR_BACKDROP);

        if (system) {
            drawIcon(context, textRenderer, DELETE_GLYPH, deleteLeft, y,
                    hover == Hover.DELETE ? COLOR_DELETE_HOVER : COLOR_NORMAL);
            return;
        }
        drawIcon(context, textRenderer, AT_GLYPH, atLeft, y,
                hover == Hover.AT ? COLOR_HOVER : COLOR_NORMAL);
        drawIcon(context, textRenderer, REPLY_GLYPH, replyLeft, y,
                hover == Hover.REPLY ? COLOR_HOVER : COLOR_NORMAL);
        if (canEdit) {
            drawIcon(context, textRenderer, EDIT_GLYPH, editLeft, y,
                    hover == Hover.EDIT ? COLOR_HOVER : COLOR_NORMAL);
        }
        if (canDelete) {
            drawIcon(context, textRenderer, DELETE_GLYPH, deleteLeft, y,
                    hover == Hover.DELETE ? COLOR_DELETE_HOVER : COLOR_NORMAL);
        }
    }

    private static void drawIcon(Object context, Font textRenderer,
                                 String glyph, int left, int y, int color) {
        Draw.text(context, textRenderer, glyph,
                left + (ICON_W - textRenderer.width(glyph)) / 2, y, color);
    }

    private static String flatten(FormattedCharSequence text) {
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
