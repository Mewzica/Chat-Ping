package dev.mewzica.chatping.mixin;

import net.minecraft.client.gui.components.ChatComponent;
//? if >=26.1 {
/*import net.minecraft.client.multiplayer.chat.GuiMessage;
*///?} else {
import net.minecraft.client.GuiMessage;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * 直接读写聊天栏内部的 {@code allMessages} 列表。
 *
 * <p>原版删消息只能按 {@code MessageSignatureData} 删，而且只对「服务端签名过、
 * 客户端也确实存了签名」的消息有效。我们要删的是自己广播的消息，
 * 走签名那条路很脆，所以干脆拿到底层列表直接改，改完再让原版自己重建
 * {@code trimmedMessages}（就是 {@code refresh()}）。</p>
 *
 * <p>26.1 的改动：{@code GuiMessage} 从 {@code net.minecraft.client} 搬到了
 * {@code net.minecraft.client.multiplayer.chat}；重建折行的方法名从单数
 * {@code refreshTrimmedMessage} 变成复数。</p>
 */
@Mixin(ChatComponent.class)
public interface ChatHudAccessor {

    @Accessor("allMessages")
    List<GuiMessage> chatping$getMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> chatping$getVisibleMessages();

    /** 原版的私有方法：按当前宽度把 {@code allMessages} 重新折行成 {@code trimmedMessages}。 */
    //? if >=26.1 {
    /*@Invoker("refreshTrimmedMessages")
    void chatping$refresh();
    *///?} else {
    @Invoker("refreshTrimmedMessage")
    void chatping$refresh();
    //?}

    /**
     * 聊天栏缩放。
     *
     * <p>1.21.11 把这个方法改成了 private，直接调编译不过，所以统一走 accessor。</p>
     */
    @Invoker("getScale")
    double chatping$getScale();

    /** 聊天栏宽度（像素），同样在 1.21.11 变 private。 */
    @Invoker("getWidth")
    int chatping$getChatWidth();

    //? if >=26.1 {
    /*// 聊天栏当前滚动位置（往回翻了多少行）。
    // 26.1 起原版不再把「这一行画在第几行」交给逐行回调，只给行对象本身，
    // 所以 y 坐标得自己按「底部对齐、往上堆」算，滚动量必须能读到。
    @Accessor("chatScrollbarPos")
    int chatping$getScroll();
    *///?}
}
