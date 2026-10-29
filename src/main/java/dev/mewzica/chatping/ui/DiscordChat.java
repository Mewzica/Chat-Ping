package dev.mewzica.chatping.ui;

//? if >=1.20.5 {
/*// Discord 版聊天界面的占位实现。
// <p>真正的渲染代码是照 1.20.1 的 {@code GuiGraphics} / {@code ChatScreen} API 写的，
// 1.20.5 之后这几处都变过（{@code GuiGraphics} 拆成 extractor、输入框位置、头像渲染签名），
// 还没逐版本分叉，所以这里先什么都不做 —— 这些版本继续用原版聊天栏，
// 但配置、开关和整条调用链都已经接好了，补齐分叉就能打开。</p>
public final class DiscordChat {

    private DiscordChat() {
    }

    public static boolean enabled() {
        return false;
    }

    public static boolean renderScreen(Object graphics, Object screen, int mouseX, int mouseY, float delta) {
        return false;
    }

    public static boolean click(double mouseX, double mouseY) {
        return false;
    }

    public static void renderGearOnly(Object graphics, Object screen, int mouseX, int mouseY) {
    }

    public static void reset() {
    }
}
*///?} else {
import com.mojang.authlib.GameProfile;
import dev.mewzica.chatping.ChatFormat;
import dev.mewzica.chatping.ChatPingHud;
import dev.mewzica.chatping.Draw;
import dev.mewzica.chatping.mixin.ChatHudAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DiscordChat {

    // 配色照抄 Discord 的深色主题，游戏里看着才像那么回事
    private static final int PANEL = 0xF0313338;
    private static final int HEADER = 0xFF2B2D31;
    private static final int DIVIDER = 0xFF26272B;
    private static final int INPUT_BG = 0xFF383A40;
    private static final int TEXT = 0xFFDBDEE1;
    private static final int MUTED = 0xFF949BA4;
    private static final int ACCENT = 0xFF5865F2;
    private static final int HOVER_BG = 0xFF2E3035;
    private static final int MENTION_FG = 0xFF7CB8F5;
    private static final int GOLD = 0xFFF0C674;
    private static final int QUOTE_BAR = 0xFF4E5058;

    private static final int LINE_H = 10;
    private static final int AVATAR = 16;
    private static final int ICON_W = 11;
    private static final int ICON_GAP = 1;
    private static final long GROUP_WINDOW_MS = 60_000L;

    private static final String AT_GLYPH = "@";
    private static final String REPLY_GLYPH = "\u21A9";
    private static final String EDIT_GLYPH = "\u270E";
    private static final String DELETE_GLYPH = "\u2715";
    private static final String GEAR_GLYPH = "\u2261";

    private static final int COLOR_ICON = 0xFFB5BAC1;
    private static final int COLOR_ICON_HOVER = 0xFFFFE066;
    private static final int COLOR_DELETE_HOVER = 0xFFFF6666;

    private static Field inputField;
    private static boolean inputResolved;

    private static int gearX;
    private static int gearY;
    private static boolean gearVisible;

    private DiscordChat() {
    }

    public static boolean enabled() {
        return ChatPingConfig.discord();
    }

    // ==================================================================
    // 整屏：按 T 打开的那个界面
    // ==================================================================

    public static boolean renderScreen(Object graphicsObj, Object screenObj,
                                       int mouseX, int mouseY, float delta) {
        if (!enabled() || !(graphicsObj instanceof GuiGraphics graphics) || !(screenObj instanceof ChatScreen screen)) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        ChatComponent hud = client.gui.getChat();
        ChatHudAccessor accessor = (ChatHudAccessor) hud;

        int w = screen.width;
        int h = screen.height;
        int x0 = 4;
        int y0 = 4;
        int x1 = w - 4;
        int y1 = h - 4;
        int pw = x1 - x0;

        // 悬停图标的状态每帧重算，这里替原版把帧开起来
        ChatPingHud.beginFrame(mouseX, mouseY);

        // 背景：整块面板 + 顶栏 + 底部分隔
        Draw.fill(graphics, 0, 0, w, h, 0xC0000000);
        Draw.fill(graphics, x0, y0, x1, y1, PANEL);
        Draw.fill(graphics, x0, y0, x1, y0 + 16, HEADER);
        Draw.fill(graphics, x0, y0 + 16, x1, y0 + 17, DIVIDER);

        int inputTop = y1 - 26;
        Draw.fill(graphics, x0, inputTop - 1, x1, inputTop, DIVIDER);

        // 顶栏：# general + 在线人数 + 右侧切换按钮
        Draw.text(graphics, font, "#", x0 + 7, y0 + 4, MUTED);
        Draw.text(graphics, font, "general", x0 + 15, y0 + 4, 0xFFF2F3F5);
        int online = client.getConnection() == null ? 0 : client.getConnection().getOnlinePlayers().size();
        Draw.text(graphics, font, online + " online", x0 + 15 + font.width("general") + 8, y0 + 4, MUTED);

        gearX = x1 - 16;
        gearY = y0 + 2;
        gearVisible = true;
        boolean gearHover = inside(mouseX, mouseY, gearX, gearY, gearX + 12, gearY + 12);
        Draw.text(graphics, font, GEAR_GLYPH, gearX + 3, gearY + 2, gearHover ? 0xFFFFFFFF : MUTED);

        // 消息列表
        int listTop = y0 + 18;
        int listBottom = inputTop - 2;
        renderList(graphics, font, client, hud, accessor, x0, x1, listTop, listBottom, mouseX, mouseY);

        // 输入框
        renderInput(graphics, font, screen, client, x0, inputTop, x1, y1, mouseX, mouseY, delta);
        return true;
    }

    private static void renderList(GuiGraphics graphics, Font font, Minecraft client, ChatComponent hud,
                                   ChatHudAccessor accessor, int x0, int x1, int top, int bottom,
                                   int mouseX, int mouseY) {
        List<GuiMessage> all = accessor.chatping$getMessages();
        if (all.isEmpty()) {
            return;
        }
        String self = ChatFormat.selfName();
        int textLeft = x0 + 8 + AVATAR + 6;
        int textWidth = Math.max(40, x1 - 12 - textLeft);

        List<Row> rows = new ArrayList<>(all.size());
        Row previous = null;
        for (GuiMessage message : all) {
            String flat = message.content().getString();
            String sender = ChatFormat.senderName(flat);
            String body = body(flat, sender);
            boolean grouped = previous != null
                    && previous.sender != null
                    && previous.sender.equals(sender)
                    && message.addedTime() - previous.time < GROUP_WINDOW_MS;
            boolean mentioned = self != null && ChatFormat.mentions(body, self);
            Row row = new Row();
            row.flat = flat;
            row.sender = sender;
            row.body = body;
            row.grouped = grouped;
            row.time = message.addedTime();
            row.mentioned = mentioned;
            row.lines = font.split(bodyComponent(body, mentioned), textWidth);
            row.height = (grouped ? 0 : LINE_H + 1) + row.lines.size() * LINE_H + 3;
            rows.add(row);
            previous = row;
        }

        int available = bottom - top;
        int used = 0;
        int start = rows.size();
        while (start > 0) {
            int next = rows.get(start - 1).height;
            if (used + next > available) {
                break;
            }
            used += next;
            start--;
        }

        // 贴着底部往上排，和 Discord 一样新消息在最下面
        int y = bottom - used;
        for (int i = start; i < rows.size(); i++) {
            Row row = rows.get(i);
            boolean rowHover = mouseY >= y && mouseY < y + row.height;
            if (rowHover) {
                Draw.fill(graphics, x0 + 2, y, x1 - 2, y + row.height, HOVER_BG);
            }
            drawRow(graphics, font, client, hud, row, x0, x1, y, mouseX, mouseY, rowHover);
            y += row.height;
        }
    }

    private static void drawRow(GuiGraphics graphics, Font font, Minecraft client, ChatComponent hud,
                                Row row, int x0, int x1, int y, int mouseX, int mouseY, boolean rowHover) {
        int avatarX = x0 + 8;
        int nameY = y + 2;
        int bodyY = row.grouped ? y + 2 : nameY + LINE_H + 1;

        if (!row.grouped) {
            ResourceLocation skin = skin(client, row.sender);
            PlayerFaceRenderer.draw(graphics, skin, avatarX, nameY - 1, AVATAR);
        }

        int textLeft = x0 + 8 + AVATAR + 6;
        if (!row.grouped) {
            String name = row.sender == null ? "System" : row.sender;
            int nameColor = row.sender == null ? MUTED : nameColor(row.sender);
            Draw.text(graphics, font, name, textLeft, nameY, nameColor);
            Draw.text(graphics, font, clock(row.time), textLeft + font.width(name) + 6, nameY, MUTED);
        }

        int lineY = bodyY;
        for (FormattedCharSequence line : row.lines) {
            graphics.drawString(font, line, textLeft, lineY, row.mentioned ? GOLD : TEXT);
            lineY += LINE_H;
        }

        // 悬停：右侧一排图标，位置交给 ChatPingHud 记着，点击还是原来那套逻辑
        if (!rowHover) {
            return;
        }
        ChatPingHud.hoveredLine = row.flat;
        ChatPingHud.hoveredOwn = row.sender != null && ChatFormat.selfName() != null
                && ChatFormat.selfName().equalsIgnoreCase(row.sender);

        boolean system = row.sender == null;
        boolean own = ChatPingHud.hoveredOwn;
        boolean remote = dev.mewzica.chatping.net.ServerPresence.isActive();
        boolean canEdit = own && remote;
        boolean canDelete = remote && (own || dev.mewzica.chatping.net.ServerPresence.canDeleteOthers());
        if (system && !canDelete) {
            return;
        }

        int slot = x1 - 8 - ICON_W;
        int replyLeft = -1;
        int atLeft = -1;
        int editLeft = -1;
        int deleteLeft = -1;
        if (!system) {
            replyLeft = slot;
            slot -= ICON_GAP + ICON_W;
            atLeft = slot;
            slot -= ICON_GAP + ICON_W;
        }
        if (canEdit) {
            editLeft = slot;
            slot -= ICON_GAP + ICON_W;
        }
        if (canDelete) {
            deleteLeft = slot;
        }

        int iconY = nameY;
        if (canDelete && inside(mouseX, mouseY, deleteLeft, iconY, deleteLeft + ICON_W, iconY + 9)) {
            ChatPingHud.hover = ChatPingHud.Hover.DELETE;
        } else if (canEdit && inside(mouseX, mouseY, editLeft, iconY, editLeft + ICON_W, iconY + 9)) {
            ChatPingHud.hover = ChatPingHud.Hover.EDIT;
        } else if (!system && inside(mouseX, mouseY, replyLeft, iconY, replyLeft + ICON_W, iconY + 9)) {
            ChatPingHud.hover = ChatPingHud.Hover.REPLY;
        } else if (!system && inside(mouseX, mouseY, atLeft, iconY, atLeft + ICON_W, iconY + 9)) {
            ChatPingHud.hover = ChatPingHud.Hover.AT;
        }

        if (system) {
            icon(graphics, font, DELETE_GLYPH, deleteLeft, iconY,
                    ChatPingHud.hover == ChatPingHud.Hover.DELETE ? COLOR_DELETE_HOVER : COLOR_ICON);
            return;
        }
        icon(graphics, font, AT_GLYPH, atLeft, iconY,
                ChatPingHud.hover == ChatPingHud.Hover.AT ? COLOR_ICON_HOVER : COLOR_ICON);
        icon(graphics, font, REPLY_GLYPH, replyLeft, iconY,
                ChatPingHud.hover == ChatPingHud.Hover.REPLY ? COLOR_ICON_HOVER : COLOR_ICON);
        if (canEdit) {
            icon(graphics, font, EDIT_GLYPH, editLeft, iconY,
                    ChatPingHud.hover == ChatPingHud.Hover.EDIT ? COLOR_ICON_HOVER : COLOR_ICON);
        }
        if (canDelete) {
            icon(graphics, font, DELETE_GLYPH, deleteLeft, iconY,
                    ChatPingHud.hover == ChatPingHud.Hover.DELETE ? COLOR_DELETE_HOVER : COLOR_ICON);
        }
    }

    // ==================================================================
    // 输入框
    // ==================================================================

    private static void renderInput(GuiGraphics graphics, Font font, ChatScreen screen, Minecraft client,
                                    int x0, int top, int x1, int y1, int mouseX, int mouseY, float delta) {
        int boxLeft = x0 + 8;
        int boxRight = x1 - 8;
        int boxTop = top + 3;
        int boxBottom = y1 - 4;
        Draw.fill(graphics, boxLeft, boxTop, boxRight, boxBottom, INPUT_BG);

        EditBox box = input(screen);
        if (box == null) {
            return;
        }
        box.setBordered(false);
        box.setX(boxLeft + 6);
        box.setY(boxTop + (boxBottom - boxTop - 8) / 2);
        box.setWidth(boxRight - boxLeft - 34);
        if (box.getValue().isEmpty()) {
            Draw.text(graphics, font, "Message #general", boxLeft + 6, box.getY(), 0xFF6D6F78);
        }
        box.render(graphics, mouseX, mouseY, delta);

        // 右下角那颗发送键只是个视觉锚点，真发还是回车
        int sendSize = 12;
        int sendX = boxRight - sendSize - 5;
        int sendY = boxTop + (boxBottom - boxTop - sendSize) / 2;
        Draw.fill(graphics, sendX, sendY, sendX + sendSize, sendY + sendSize, ACCENT);
        Draw.text(graphics, font, ">", sendX + 4, sendY + 2, 0xFFFFFFFF);
    }

    // ==================================================================
    // 点击 / 切换
    // ==================================================================

    // @return true 表示这一下已经被我们吃掉了
    public static boolean click(double mouseX, double mouseY) {
        if (!enabled() || !gearVisible) {
            return false;
        }
        if (inside((int) mouseX, (int) mouseY, gearX, gearY, gearX + 12, gearY + 12)) {
            ChatPingConfig.toggle();
            gearVisible = false;
            return true;
        }
        return false;
    }

    // 原版样式下也留一个切换按钮，否则切过去就回不来了。
    public static void renderGearOnly(Object graphicsObj, Object screenObj, int mouseX, int mouseY) {
        if (enabled() || !(graphicsObj instanceof GuiGraphics graphics) || !(screenObj instanceof ChatScreen screen)) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        gearX = screen.width - 18;
        gearY = 6;
        gearVisible = true;
        boolean hover = inside(mouseX, mouseY, gearX, gearY, gearX + 12, gearY + 12);
        Draw.fill(graphics, gearX, gearY, gearX + 12, gearY + 12, hover ? HEADER : 0x802B2D31);
        Draw.text(graphics, font, GEAR_GLYPH, gearX + 3, gearY + 2, hover ? 0xFFFFFFFF : MUTED);
    }

    public static void reset() {
        gearVisible = false;
    }

    // ==================================================================
    // 小工具
    // ==================================================================

    private static final class Row {
        String flat;
        String sender;
        String body;
        boolean grouped;
        boolean mentioned;
        long time;
        List<FormattedCharSequence> lines;
        int height;
    }

    // 去掉 {@code <名字>} 前缀，只留正文。
    private static String body(String flat, String sender) {
        if (sender == null) {
            return flat;
        }
        String prefix = "<" + sender + ">";
        if (flat.startsWith(prefix)) {
            String rest = flat.substring(prefix.length());
            return rest.startsWith(" ") ? rest.substring(1) : rest;
        }
        return flat;
    }

    // 正文里把 {@code @名字} 染成蓝色；整条 @ 到自己就整条金色。
    private static Component bodyComponent(String body, boolean mentioned) {
        if (mentioned) {
            return Component.literal(body).withStyle(ChatFormatting.GOLD);
        }
        MutableComponent out = Component.literal("");
        int i = 0;
        while (i < body.length()) {
            int at = body.indexOf('@', i);
            if (at < 0) {
                out.append(Component.literal(body.substring(i)));
                break;
            }
            if (at > i) {
                out.append(Component.literal(body.substring(i, at)));
            }
            int end = at + 1;
            while (end < body.length() && ChatFormat.isNameChar(body.charAt(end))) {
                end++;
            }
            if (end == at + 1) {
                out.append(Component.literal("@"));
                i = at + 1;
                continue;
            }
            out.append(Component.literal(body.substring(at, end))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MENTION_FG & 0xFFFFFF))));
            i = end;
        }
        return out;
    }

    private static ResourceLocation skin(Minecraft client, String sender) {
        UUID id = null;
        if (sender != null && client.getConnection() != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(sender);
            if (info != null) {
                GameProfile profile = info.getProfile();
                if (profile != null) {
                    id = profile.getId();
                }
            }
        }
        return id == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.getDefaultSkin(id);
    }

    // 名字颜色跟原版聊天里一致，看起来才不突兀。
    private static int nameColor(String sender) {
        int hash = sender.hashCode();
        int[] palette = {0xFF57D9A3, 0xFFF2A0C0, 0xFFF0C674, 0xFF7CB8F5, 0xFFB79CF0, 0xFFF0997B};
        return palette[Math.floorMod(hash, palette.length)];
    }

    private static String clock(long millis) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return String.format("%02d:%02d",
                calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE));
    }

    private static void icon(GuiGraphics graphics, Font font, String glyph, int left, int y, int color) {
        Draw.text(graphics, font, glyph, left + (ICON_W - font.width(glyph)) / 2, y, color);
    }

    private static boolean inside(int mx, int my, int left, int top, int right, int bottom) {
        return mx >= left && mx < right && my >= top && my < bottom;
    }

    // 原版 ChatScreen 的输入框是 protected，反射拿一次就够。
    private static EditBox input(ChatScreen screen) {
        if (!inputResolved) {
            inputResolved = true;
            try {
                inputField = ChatScreen.class.getDeclaredField("input");
                inputField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                inputField = null;
            }
        }
        if (inputField == null) {
            return null;
        }
        try {
            return (EditBox) inputField.get(screen);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
//?}
