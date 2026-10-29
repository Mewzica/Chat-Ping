package dev.mewzica.chatping;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mewzica.chatping.net.ServerPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 输入 {@code @} 时的在线玩家补全候选。
 *
 * <p>原版 {@code ChatInputSuggestor.refresh()} 里其实已经留了「非命令文本也补全」的分支，
 * 只是它把候选交给了 {@code CommandSource.getChatSuggestions()}（原版永远是空的），
 * 所以平时什么都弹不出来。这里把这个分支的候选换成在线玩家名，
 * 剩下的弹窗、滚动、Tab 补全全部交回原版 {@code SuggestionWindow} 处理，
 * 效果和打 {@code /} 时一模一样。</p>
 */
public final class ChatSuggestions {

    /** refresh 里是否真的生成了提及候选，用来决定要不要把窗口弹出来。 */
    private static boolean matched;

    private ChatSuggestions() {
    }

    public static boolean consumeMatched() {
        boolean value = matched;
        matched = false;
        return value;
    }

    /**
     * 当前正在输入的词是 {@code @名字} 时，返回玩家名候选；否则返回 null 表示「不关我事」。
     */
    public static Suggestions build(SuggestionsBuilder builder) {
        matched = false;
        String remaining = builder.getRemaining();
        if (remaining.length() < 1 || remaining.charAt(0) != '@') {
            return null;
        }
        String partial = remaining.substring(1);
        for (int i = 0; i < partial.length(); i++) {
            if (!ChatFormat.isNameChar(partial.charAt(i))) {
                return null; // @ 后面已经不是名字了（比如邮箱、@ 加中文）
            }
        }

        int start = builder.getStart();
        StringRange range = StringRange.between(start, start + remaining.length());
        String lower = partial.toLowerCase(Locale.ROOT);

        List<Suggestion> list = new ArrayList<>();
        for (String name : onlinePlayers()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                String text = "@" + name;
                list.add(new Suggestion(range, text).expand(text, range));
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        matched = true;
        return new Suggestions(range, list);
    }

    private static List<String> onlinePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
            String name = Compat.profileName(entry.getProfile());
            if (name != null && !name.isEmpty()) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        // @admin 群体提及。发送侧不设门槛，所以只要服务端装了 mod 就给这条候选 ——
        // 谁会被叫醒是服务端按 chatping.admin.mention 决定的，客户端不用管。
        // 真有人叫 admin 时让位给那个玩家，不然列表里会出现两个一模一样的 @admin。
        if (ServerPresence.isActive() && !containsIgnoreCase(names, ChatText.ADMIN_NAME)) {
            names.add(0, ChatText.ADMIN_NAME);
        }
        return names;
    }

    private static boolean containsIgnoreCase(List<String> names, String target) {
        for (String name : names) {
            if (name.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
