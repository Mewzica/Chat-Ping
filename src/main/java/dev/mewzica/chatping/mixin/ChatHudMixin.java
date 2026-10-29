package dev.mewzica.chatping.mixin;

import dev.mewzica.chatping.ChatFormat;
import dev.mewzica.chatping.ChatPing;
import dev.mewzica.chatping.ChatPingHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
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
     */
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
