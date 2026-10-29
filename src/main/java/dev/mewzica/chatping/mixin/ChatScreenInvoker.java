package dev.mewzica.chatping.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatScreen.class)
public interface ChatScreenInvoker {

    /** ChatScreen#insertText 是 protected，这里开个口子。 */
    @Invoker("insertText")
    void chatping$insertText(String text, boolean override);

    /** 补全列表要拿输入框的位置和内容。 */
    @Accessor("input")
    EditBox chatping$getChatField();
}
