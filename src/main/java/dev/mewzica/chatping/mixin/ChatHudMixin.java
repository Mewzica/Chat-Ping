package dev.mewzica.chatping.mixin;

//? if >=26.1 {
/*import dev.mewzica.chatping.ChatFormat;
import dev.mewzica.chatping.ChatPing;
import dev.mewzica.chatping.ChatPingHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// 26.1 把整条聊天栏渲染管线重写了：render 变成 extractRenderState，
// 逐行绘制从「自己算 y 然后调 drawString」变成「喂给 ChatGraphicsAccess.handleMessage」，
// 而且拆分成了背景 / 前景两遍。所以挂钩方式必须整套换掉。
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    // 前景那一遍才画字，图标也挂在这里。注入点在 popMatrix 之前 ——
    // 那会儿矩阵上还压着聊天栏自己的 scale(s).translate(4, 0)，
    // 所以下面所有坐标都是聊天坐标系，和 1.20.1 那套算法完全一致。
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Matrix3x2fStack;popMatrix()Lorg/joml/Matrix3x2fStack;"))
    private void chatping$drawHud(GuiGraphicsExtractor graphics, Font font, int tickCount,
                                  int mouseX, int mouseY, ChatComponent.DisplayMode mode,
                                  boolean focused, CallbackInfo ci) {
        if (!mode.foreground) {
            return;
        }
        ChatComponent hud = (ChatComponent) (Object) this;
        ChatPingHud.beginFrame(mouseX, mouseY);

        double scale = ((ChatHudAccessor) hud).chatping$getScale();
        if (scale <= 0.0D) {
            return;
        }
        // 下面三个量的算法照抄原版私有 extractRenderState，改一个像素按钮就会偏。
        double spacing = Minecraft.getInstance().options.chatLineSpacing().get();
        int entryHeight = (int) (9.0D * (spacing + 1.0D));
        int entryBottomToMessageY = (int) Math.round(8.0D * (spacing + 1.0D) - 4.0D * spacing);
        int chatBottom = Mth.floor((graphics.guiHeight() - 40) / (float) scale);

        List<GuiMessage.Line> lines = ((ChatHudAccessor) hud).chatping$getVisibleMessages();
        int scroll = ((ChatHudAccessor) hud).chatping$getScroll();
        int count = Math.min(lines.size() - scroll, hud.getLinesPerPage());
        // 原版 forEachLine 是「最上面一行先喂」，这里保持同样的顺序：
        // ChatPingHud 靠「上一条是不是某条消息的结尾」来拼完整文本，顺序错了会拼反。
        for (int i = count - 1; i >= 0; i--) {
            GuiMessage.Line line = lines.get(i + scroll);
            int y = chatBottom - i * entryHeight - entryBottomToMessageY;
            ChatPingHud.setEndOfEntry(line.endOfEntry());
            ChatPingHud.onLineRendered(hud, graphics, font, line.content(), y);
        }
    }

    // 所有聊天消息进 ChatComponent 之前先过一遍：判断有没有 @ 自己（播音效），
    // 再把提及高亮 / 引用原文这些显示上的改动塞回去。
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;"
                    + "Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
                    + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private Component chatping$decorateIncoming(Component message) {
        if (message == null) {
            return null;
        }
        ChatPing.onIncoming(message);
        return ChatFormat.decorate(message);
    }
}
*///?} else {
import dev.mewzica.chatping.ChatFormat;
import dev.mewzica.chatping.ChatPing;
import dev.mewzica.chatping.ChatPingHud;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    /** 记下这一帧的鼠标位置，后面逐行绘制时要用。 */
    @Inject(method = "render", at = @At("HEAD"))
    private void chatping$beginFrame(GuiGraphics context, int currentTick, int mouseX, int mouseY,
                                     CallbackInfo ci) {
        ChatPingHud.beginFrame(mouseX, mouseY);
    }

    /** 顺便把「这一行是不是这条消息的最后一行」记下来。 */
    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/GuiMessage$Line;"
                            + "content()Lnet/minecraft/util/FormattedCharSequence;"))
    private FormattedCharSequence chatping$captureEndOfEntry(GuiMessage.Line visible) {
        ChatPingHud.setEndOfEntry(visible.endOfEntry());
        return visible.content();
    }

    /**
     * 原版画每一行聊天文字都会走这里（只有这一个调用点用的是 FormattedCharSequence 重载），
     * 借它拿到这一行的精确 y 坐标，画完文字后顺便把悬停图标补上。
     *
     * <p>1.21.6 把 {@code GuiGraphics#drawString} 的返回值从 {@code int}（画完后的 x）
     * 改成了 {@code void}，所以重定向的描述符和返回值都得跟着换一套。</p>
     */
    //? if >=1.21.6 {
    /*@Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString"
                            + "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"))
    private void chatping$drawChatLine(GuiGraphics context, Font textRenderer, FormattedCharSequence text,
                                       int x, int y, int color) {
        context.drawString(textRenderer, text, x, y, color);
        ChatPingHud.onLineRendered((ChatComponent) (Object) this, context, textRenderer, text, y);
    }
    *///?} else {
    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString"
                            + "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int chatping$drawChatLine(GuiGraphics context, Font textRenderer, FormattedCharSequence text,
                                      int x, int y, int color) {
        int result = context.drawString(textRenderer, text, x, y, color);
        ChatPingHud.onLineRendered((ChatComponent) (Object) this, context, textRenderer, text, y);
        return result;
    }
    //?}

    /**
     * 所有聊天消息进 ChatComponent 之前先过一遍：判断有没有 @ 自己（播音效），
     * 再把提及高亮 / 引用原文这些显示上的改动塞回去。
     */
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;"
                    + "Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private Component chatping$decorateIncoming(Component message) {
        if (message == null) {
            return null;
        }
        ChatPing.onIncoming(message);
        return ChatFormat.decorate(message);
    }
}
//?}
