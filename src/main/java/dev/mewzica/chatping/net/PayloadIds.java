package dev.mewzica.chatping.net;

//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}

/**
 * 生成 {@code chatping:xxx} 这种通道 id。
 *
 * <p>单独拆一个文件，是因为这个 id 的类型和构造方式一共变过两次：</p>
 * <ul>
 *   <li><b>1.20.1 ~ 1.20.4</b>：{@code new ResourceLocation(ns, path)}；</li>
 *   <li><b>1.21 ~ 1.21.10</b>：构造器变私有，改用
 *       {@code ResourceLocation.fromNamespaceAndPath(ns, path)}；</li>
 *   <li><b>1.21.11 起</b>：类本身改名成 {@code Identifier}（方法名没变）。</li>
 * </ul>
 *
 * <p>为什么不直接写在 {@link Net} 里：{@code Net} 的 1.20.5+ 分支整段被包在
 * Stonecutter 的块注释里，而 Java 的块注释不能嵌套 —— 里面再放一个
 * {@code //? if} 会把外层注释提前闭合。所以分叉必须落在独立文件里。</p>
 */
final class PayloadIds {

    private PayloadIds() {
    }

    //? if >=1.21.11 {
    /*static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("chatping", path);
    }
    *///?} else {
        //? if >=1.21 {
        /*static ResourceLocation id(String path) {
            return ResourceLocation.fromNamespaceAndPath("chatping", path);
        }
        *///?} else {
        static ResourceLocation id(String path) {
            return new ResourceLocation("chatping", path);
        }
        //?}
    //?}
}
