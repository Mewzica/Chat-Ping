package dev.mewzica.chatping.mixin;

//? if >=26.1 {
/*import dev.mewzica.chatping.ChatEditor;
import dev.mewzica.chatping.ChatFormat;
import dev.mewzica.chatping.ChatPingHud;
import dev.mewzica.chatping.DeleteConfirm;
import dev.mewzica.chatping.PendingReply;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    // 26.1 把鼠标事件打包成了 MouseButtonEvent，坐标和按键都得从里面取。
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chatping$onMouseClicked(MouseButtonEvent event, boolean doubled,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) {
            return;
        }
        double mouseX = event.x();
        double mouseY = event.y();
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

    // 关闭聊天框时丢掉没发出去的回复 / 没提交的编辑。
    @Inject(method = "removed", at = @At("TAIL"))
    private void chatping$onRemoved(CallbackInfo ci) {
        DeleteConfirm.close();
        ChatEditor.cancelEdit();
        PendingReply.clear();
    }

    // 预览条和删除确认框都画在聊天界面最上层。
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void chatping$renderOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY,
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

    // 编辑模式下的回车：不聊天，改成把「改哪条、改成什么」发给服务端。
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void chatping$commitEdit(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        if (ChatEditor.isEditing() && ChatEditor.commit(chatText)) {
            cir.setReturnValue(true);
        }
    }

    // 发送时把待发送的回复拼到正文前面。
    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
    private String chatping$prependReply(String chatText) {
        if (chatText == null || chatText.isBlank() || chatText.startsWith("/") || !PendingReply.has()) {
            return chatText;
        }
        if (PendingReply.isEditing()) {
            return chatText;
        }
        String prefix = PendingReply.prefix();
        PendingReply.clear();
        return prefix + chatText;
    }
}
*///?} else {
import dev.mewzica.chatping.ChatEditor;
import dev.mewzica.chatping.ChatFormat;
import dev.mewzica.chatping.ChatPingHud;
import dev.mewzica.chatping.DeleteConfirm;
import dev.mewzica.chatping.PendingReply;
import dev.mewzica.chatping.ui.DiscordChat;
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
        // 右上角那个 ≡ 是样式开关，它永远排在最前面
        if (DiscordChat.click(mouseX, mouseY)) {
            cir.setReturnValue(true);
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

    /**
     * Discord 样式下整个界面由我们自己画，原版那条渲染路径直接掐掉。
     *
     * <p>输入框还是原版那个 {@code EditBox}（输入法、选区、历史全都靠它），
     * 只是每帧被挪到我们画的圆角框里再自己渲染一遍 —— 重写一个输入框
     * 才是真的会丢功能。</p>
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void chatping$renderDiscord(GuiGraphics context, int mouseX, int mouseY,
                                        float delta, CallbackInfo ci) {
        if (DiscordChat.renderScreen(context, this, mouseX, mouseY, delta)) {
            ci.cancel();
        }
    }

    /** 关闭聊天框时丢掉没发出去的回复 / 没提交的编辑。 */
    @Inject(method = "removed", at = @At("TAIL"))
    private void chatping$onRemoved(CallbackInfo ci) {
        DeleteConfirm.close();
        ChatEditor.cancelEdit();
        PendingReply.clear();
        DiscordChat.reset();
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
        // 原版样式下也得留一个切换按钮，否则切过去就回不来了
        DiscordChat.renderGearOnly(context, screen, mouseX, mouseY);
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
//?}
