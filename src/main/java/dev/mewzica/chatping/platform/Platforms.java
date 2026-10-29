package dev.mewzica.chatping.platform;

/**
 * 当前加载器实现的存放处。
 *
 * <p>入口点第一件事就是 {@code Platforms.set(new XxxPlatform())}，
 * 之后共享代码里所有 {@code Platforms.get()} 都拿得到它。</p>
 *
 * <p>为什么不用 ServiceLoader 或者反射找实现：加载器的入口点本来就要写，
 * 顺手 set 一下是最短的路，而且启动期不会有「找不到实现」的歧义。</p>
 */
public final class Platforms {

    private static Platform instance;

    private Platforms() {
    }

    public static void set(Platform platform) {
        instance = platform;
    }

    public static Platform get() {
        Platform platform = instance;
        if (platform == null) {
            throw new IllegalStateException(
                    "ChatPing: 加载器实现还没注册就有人来要了 —— 检查入口点是不是漏了 Platforms.set(...)");
        }
        return platform;
    }
}
