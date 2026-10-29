package dev.mewzica.chatping.mixin;

import dev.mewzica.chatping.ChatFormat;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.gui.screens.Screen.class)
public abstract class ScreenMixin {

    /**
     * 聊天里点 @名字 的时候，原版只会走 SUGGEST_COMMAND 那套（会用新文本顶掉整个输入框）。
     * 这里改成往当前输入框里追加，避免把已经打了一半的内容冲掉。
     */
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void chatping$onTextClick(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ChatScreen)) {
            return;
        }
        if (style == null) {
            return;
        }
        ClickEvent click = style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.SUGGEST_COMMAND) {
            return;
        }
        String value = click.getValue();
        if (value == null || !value.startsWith(ChatFormat.CLICK_MARKER)) {
            return;
        }
        ((ChatScreenInvoker) (Object) this).chatping$insertText(
                value.substring(ChatFormat.CLICK_MARKER.length()), false);
        cir.setReturnValue(true);
    }
}
