package dev.mewzica.chatping.net;

import net.minecraft.resources.ResourceLocation;

/**
 * 生成 {@code chatping:xxx} 这种通道 id。
 *
 * <p>单独拆一个文件，是因为 {@link ResourceLocation} 的构造方式在 1.21 变过一次：
 * 老版本是 {@code new ResourceLocation(ns, path)}，1.21 起构造器变私有，
 * 要改用 {@code ResourceLocation.fromNamespaceAndPath(ns, path)}。</p>
 *
 * <p>为什么不直接写在 {@link Net} 里：{@code Net} 的 1.20.5+ 分支整段被包在
 * Stonecutter 的块注释里，而 Java 的块注释不能嵌套 —— 里面再放一个
 * {@code //? if} 会把外层注释提前闭合。所以分叉必须落在独立文件里。</p>
 */
final class PayloadIds {

    private PayloadIds() {
    }

    //? if >=1.21 {
    /*static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("chatping", path);
    }
    *///?} else {
    static ResourceLocation id(String path) {
        return new ResourceLocation("chatping", path);
    }
    //?}
}
