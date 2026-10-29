package dev.mewzica.chatping.mixin;

import dev.mewzica.chatping.ChatEditor;
import dev.mewzica.chatping.ChatFormat;
import dev.mewzica.chatping.ChatPingHud;
import dev.mewzica.chatping.DeleteConfirm;
import dev.mewzica.chatping.PendingReply;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    /**
     * 抢在原版处理点击之前，判断这一下是不是点在了：
     * 删除确认框 / 回复预览条的 x / 聊天栏右侧的四个图标上。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chatping$onMouseClicked(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) {
            return;
        }
        // 确认框开着的时候它优先，点哪儿都先由它处理
        if (DeleteConfirm.mouseClicked(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        if (PendingReply.clickCancel(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        if (ChatPingHud.hover == ChatPingHud.Hover.NONE) {
            return;
        }
        String line = ChatPingHud.hoveredLine;
        if (line == null) {
            return;
        }

        String name = ChatFormat.senderName(line);
        switch (ChatPingHud.hover) {
            case REPLY -> PendingReply.set(name, ChatFormat.sanitizeQuote(line));
            case EDIT -> ChatEditor.beginEdit((ChatScreen) (Object) this, name, line);
            case DELETE -> DeleteConfirm.open(line, mouseX, mouseY,
                    ((ChatScreen) (Object) this).width, ((ChatScreen) (Object) this).height);
            default -> ((ChatScreenInvoker) (Object) this)
                    .chatping$insertText(name != null ? "@" + name + " " : "@", false);
        }
        cir.setReturnValue(true);
    }

    /** 关闭聊天框时丢掉没发出去的回复 / 没提交的编辑。 */
    @Inject(method = "removed", at = @At("TAIL"))
    private void chatping$onRemoved(CallbackInfo ci) {
        DeleteConfirm.close();
        ChatEditor.cancelEdit();
        PendingReply.clear();
    }

    /** 预览条和删除确认框都画在聊天界面最上层。 */
    @Inject(method = "render", at = @At("TAIL"))
    private void chatping$renderOverlays(GuiGraphics context, int mouseX, int mouseY,
                                         float delta, CallbackInfo ci) {
        Font textRenderer = Minecraft.getInstance().font;
        ChatScreen screen = (ChatScreen) (Object) this;
        if (PendingReply.has()) {
            PendingReply.render(context, textRenderer, screen, mouseX, mouseY);
        }
        if (DeleteConfirm.isOpen()) {
            DeleteConfirm.render(context, textRenderer, mouseX, mouseY);
        }
    }

    /**
     * 编辑模式下的回车：不聊天，改成把「改哪条、改成什么」发给服务端。
     */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void chatping$commitEdit(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        if (ChatEditor.isEditing() && ChatEditor.commit(chatText)) {
            cir.setReturnValue(true);
        }
    }

    /** 发送时把待发送的回复拼到正文前面。 */
    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
    private String chatping$prependReply(String chatText) {
        // 正文是空的就原样返回：原版 sendMessage 会自己拦掉空串，
        // 否则只挂了个回复、正文一个字没打，也会把 @名字 “原文” 单独发出去。
        if (chatText == null || chatText.isBlank() || chatText.startsWith("/") || !PendingReply.has()) {
            return chatText;
        }
        // 编辑模式走自己的链路，这里不能拼引用
        if (PendingReply.isEditing()) {
            return chatText;
        }
        String prefix = PendingReply.prefix();
        PendingReply.clear();
        return prefix + chatText;
    }
}
