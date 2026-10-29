package dev.mewzica.chatping.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mewzica.chatping.ChatSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/**
 * 把 {@code @玩家名} 的候选塞进原版的聊天补全流程。
 *
 * <p>原版 {@code refresh()} 在「不是斜杠命令」的分支里，会用
 * {@code SharedSuggestionProvider.suggestMatching(getChatSuggestions(), builder)} 算候选，
 * 但那个集合永远是空的，所以窗口从不出现。这里把它换掉，
 * 再在末尾主动调一次 {@code show(false)} 让原版把窗口画出来。</p>
 */
@Mixin(CommandSuggestions.class)
public abstract class ChatInputSuggestorMixin {

    @Redirect(method = "updateCommandInfo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/commands/SharedSuggestionProvider;suggest"
                            + "(Ljava/lang/Iterable;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)"
                            + "Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<Suggestions> chatping$mentionSuggest(Iterable<String> candidates,
                                                                          SuggestionsBuilder builder) {
        Suggestions mine = ChatSuggestions.build(builder);
        if (mine != null) {
            return CompletableFuture.completedFuture(mine);
        }
        return SharedSuggestionProvider.suggest(candidates, builder);
    }

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void chatping$showMentionWindow(CallbackInfo ci) {
        if (ChatSuggestions.consumeMatched()) {
            ((CommandSuggestions) (Object) this).showSuggestions(false);
        }
    }
}
