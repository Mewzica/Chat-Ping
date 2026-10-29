package dev.mewzica.chatping;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * 绘制调用的薄壳。
 *
 * <p>26.1 把整条 GUI 渲染管线换掉了，绘制上下文从 {@code GuiGraphics} 变成
 * {@code GuiGraphicsExtractor}，画文字的方法也从 {@code drawString} 改名成 {@code text}。
 * 这两个类的名字和签名都不一样，没法用同一个变量声明，所以这里用 {@code Object}
 * 收口，只在真正调绘制的那一刻转成当版本的类型。</p>
 *
 * <p>调用方（{@link ChatPingHud} / {@link DeleteConfirm} / {@link PendingReply}）
 * 因此完全不用关心是哪个版本，一律传 {@code Object}。</p>
 */
public final class Draw {

    private Draw() {
    }

    public static void fill(Object graphics, int left, int top, int right, int bottom, int color) {
        //? if >=26.1 {
        /*((GuiGraphicsExtractor) graphics).fill(left, top, right, bottom, color);
        *///?} else {
        ((GuiGraphics) graphics).fill(left, top, right, bottom, color);
        //?}
    }

    public static void text(Object graphics, Font font, String text, int x, int y, int color) {
        //? if >=26.1 {
        /*((GuiGraphicsExtractor) graphics).text(font, text, x, y, color);
        *///?} else {
        // 1.21.6 起 drawString 返回 void，老版本返回 int；当语句用两边都合法。
        ((GuiGraphics) graphics).drawString(font, text, x, y, color);
        //?}
    }

    public static void text(Object graphics, Font font, Component text, int x, int y, int color) {
        //? if >=26.1 {
        /*((GuiGraphicsExtractor) graphics).text(font, text, x, y, color);
        *///?} else {
        ((GuiGraphics) graphics).drawString(font, text, x, y, color);
        //?}
    }
}
