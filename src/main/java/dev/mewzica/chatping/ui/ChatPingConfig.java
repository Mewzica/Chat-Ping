package dev.mewzica.chatping.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 客户端配置，落在 {@code config/chatping.json}。
 *
 * <p>目前只有一项：聊天栏长什么样。</p>
 * <ul>
 *   <li>{@code DISCORD}（默认）：头像 + 名字 + 时间戳 + 连续消息合并 + 顶栏和圆角输入框；</li>
 *   <li>{@code VANILLA}：就是模组原来那套原版排版，悬停图标照旧。</li>
 * </ul>
 *
 * <p>游戏里点聊天框右上角那个 {@code ≡} 按钮就能来回切，切换会立刻写回文件，
 * 不用重启也不用改文件。</p>
 *
 * <p>为什么不引第三方配置库：整个模组就两个字段，MC 自己就带着 Gson，
 * 再拖一个 Cloth Config 进来只会让依赖变重、还得为每个加载器各写一遍注册代码。</p>
 */
public final class ChatPingConfig {

    public enum Style {
        DISCORD,
        VANILLA
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/chatping.json");

    private static Style style = Style.DISCORD;
    private static boolean loaded;

    private ChatPingConfig() {
    }

    public static Style style() {
        load();
        return style;
    }

    public static boolean discord() {
        return style() == Style.DISCORD;
    }

    /** 切到另一种样式，并立刻存盘。 */
    public static Style toggle() {
        load();
        style = style == Style.DISCORD ? Style.VANILLA : Style.DISCORD;
        save();
        return style;
    }

    /** 只读一次；文件不存在 / 写坏了都退回默认值，不让配置把游戏搞崩。 */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!FILE.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE.toPath(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("chatStyle")) {
                String raw = json.get("chatStyle").getAsString();
                for (Style candidate : Style.values()) {
                    if (candidate.name().equalsIgnoreCase(raw)) {
                        style = candidate;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // 配置坏了就当没配过，用默认值
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        json.addProperty("chatStyle", style.name().toLowerCase());
        File parent = FILE.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException ignored) {
            // 写不进去就算了，下次启动还是老样子
        }
    }
}
