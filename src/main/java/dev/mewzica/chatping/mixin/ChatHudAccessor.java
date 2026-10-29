package dev.mewzica.chatping.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * 直接读写聊天栏内部的 {@code messages} 列表。
 *
 * <p>原版删消息只能按 {@code MessageSignatureData} 删，而且只对「服务端签名过、
 * 客户端也确实存了签名」的消息有效。我们要删的是自己广播的消息，
 * 走签名那条路很脆，所以干脆拿到底层列表直接改，改完再让原版自己重建
 * {@code visibleMessages}（就是 {@code refresh()}）。</p>
 */
@Mixin(ChatComponent.class)
public interface ChatHudAccessor {

    @Accessor("allMessages")
    List<GuiMessage> chatping$getMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> chatping$getVisibleMessages();

    /** 原版的私有方法：按当前宽度把 {@code messages} 重新折行成 {@code visibleMessages}。 */
    @Invoker("refreshTrimmedMessage")
    void chatping$refresh();
}
