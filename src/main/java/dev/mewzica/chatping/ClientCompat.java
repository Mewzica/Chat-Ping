package dev.mewzica.chatping;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;

/**
 * 客户端侧几个「换个版本就换条路」的取值。
 *
 * <p>两处：</p>
 * <ul>
 *   <li><b>当前界面</b>：26.2 把 {@code Minecraft.screen} 这个公开字段收进了
 *       {@code Gui}，改叫 {@code Gui#screen()}；</li>
 *   <li><b>聊天栏实例</b>：老版本是 {@code Minecraft.gui.getChat()}；26.1 起改成
 *       私有字段 {@code Gui.chat}；26.2 又把它挪到新的 {@code Gui.hud} 上
 *       （{@code Hud.chat}，一样是 private，没有 getter）。</li>
 * </ul>
 *
 * <p>{@code chat} 这里走反射而不是开 accessor：accessor 得写进
 * {@code chatping.mixins.json}，而 {@code Gui.chat} / {@code Hud} 这些成员
 * 在不同版本里根本不存在，一个指向不存在目标的 mixin 会让游戏在启动阶段就崩。
 * 反射还能顺手吃掉「26.1 挂在 Gui 上、26.2 起挂在 Hud 上」这次搬家。</p>
 */
final class ClientCompat {

    /** 聊天栏的私有字段，第一次用到时查一次就缓存。 */
    private static Field chatField;
    /** 26.2 起聊天栏挂在 {@code Gui.hud} 上，这个字段同样缓存下来。 */
    private static Field hudField;
    private static boolean holderResolved;

    private ClientCompat() {
    }

    static Screen screen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }

    static ChatComponent chat() {
        //? if >=26.1 {
        /*Minecraft client = Minecraft.getInstance();
        try {
            Object holder = client.gui;
            if (!holderResolved) {
                try {
                    hudField = client.gui.getClass().getField("hud");
                } catch (NoSuchFieldException noHud) {
                    // 26.1 还没有 Gui.hud，聊天栏就在 Gui 自己身上
                    hudField = null;
                }
                Object target = hudField == null ? holder : hudField.get(holder);
                chatField = target.getClass().getDeclaredField("chat");
                chatField.setAccessible(true);
                holderResolved = true;
            }
            Object target = hudField == null ? holder : hudField.get(holder);
            return (ChatComponent) chatField.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ChatPing: 取不到 ChatComponent", e);
        }
        *///?} else {
        return Minecraft.getInstance().gui.getChat();
        //?}
    }
}
