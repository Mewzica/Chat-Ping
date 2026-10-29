package dev.mewzica.chatping;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;

//? if >=1.21.11 {
/*import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
*///?}

/**
 * 「只改了名字、语义没变」的几处 API，集中放在这里按版本分叉。
 *
 * <p>单独开一个类是为了让业务代码看起来是干净的：调用点一律写
 * {@code Compat.xxx(...)}，不需要在十几个地方各贴一遍 {@code //? if}。</p>
 */
public final class Compat {

    private Compat() {
    }

    /**
     * 取玩家的档案名。
     *
     * <p>authlib 7（MC 1.21.9 起）把 {@code GameProfile} 从普通类改成了 record，
     * {@code getName()} 也跟着变成 {@code name()}。</p>
     */
    public static String profileName(GameProfile profile) {
        //? if >=1.21.9 {
        /*return profile.name();
        *///?} else {
        return profile.getName();
        //?}
    }

    /**
     * 玩家是否达到某个 op 等级（没装 LuckPerms 时的兜底判断）。
     *
     * <p>1.21.11 把 {@code hasPermissions(int)} 整个换成了新的
     * {@code net.minecraft.server.permissions} 体系：
     * {@code player.permissions().hasPermission(new Permission.HasCommandLevel(...))}。</p>
     */
    public static boolean hasOpLevel(Player player, int level) {
        //? if >=1.21.11 {
        /*return player.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.byId(level)));
        *///?} else {
        return player.hasPermissions(level);
        //?}
    }
}
