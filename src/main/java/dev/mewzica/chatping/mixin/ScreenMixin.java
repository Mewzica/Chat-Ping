package dev.mewzica.chatping.mixin;

import dev.mewzica.chatping.ChatFormat;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 聊天里点 {@code @名字} 的时候，原版会把输入框内容整个换成建议文本。
 * 这里改成「往当前输入框里追加」，避免把已经打了一半的内容冲掉。
 *
 * <p><b>注入点跨版本变过一次</b>，所以这个文件有两套实现，类名保持一致
 * （{@code chatping.mixins.json} 里只写了一个 {@code ScreenMixin}）：</p>
 * <ul>
 *   <li><b>1.20.1 ~ 1.21.10</b>：{@code Screen#handleComponentClicked(Style)}，
 *       所有界面共用，所以要自己判断 {@code this} 是不是 {@link ChatScreen}；</li>
 *   <li><b>1.21.11 起</b>：这个方法从 {@code Screen} 挪到了 {@code ChatScreen}，
 *       而且多了一个 {@code insertionClickMode} 参数。</li>
 * </ul>
 *
 * <p>取点击事件里的文本统一交给 {@link ChatFormat#clickSuggestion(Style)}，
 * 那边处理 {@code ClickEvent} 在 1.21.5 的改版。</p>
 *
 * <p>条件一律写 {@code >=}：这个 Stonecutter 配置下 {@code <} 恒为 false，
 * 写 {@code <1.21.11} 会被渲染成 1.21.11 那一支，编译能过、运行期注入失败。
 * 所以新版本的实现放 if 支、老版本的放 else 支。</p>
 */
//? if >=1.21.11 {
/*@Mixin(ChatScreen.class)
public abstract class ScreenMixin {

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void chatping$onTextClick(Style style, boolean insertionClickMode,
                                      CallbackInfoReturnable<Boolean> cir) {
        // insertionClickMode 是原版「点击就插入」那条路，交给它自己处理。
        if (insertionClickMode) {
            return;
        }
        String insert = ChatFormat.clickSuggestion(style);
        if (insert == null) {
            return;
        }
        ((ChatScreenInvoker) (Object) this).chatping$insertText(insert, false);
        cir.setReturnValue(true);
    }
}
*///?} else {
import net.minecraft.client.gui.screens.Screen;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void chatping$onTextClick(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ChatScreen)) {
            return;
        }
        String insert = ChatFormat.clickSuggestion(style);
        if (insert == null) {
            return;
        }
        ((ChatScreenInvoker) (Object) this).chatping$insertText(insert, false);
        cir.setReturnValue(true);
    }
}
//?}
