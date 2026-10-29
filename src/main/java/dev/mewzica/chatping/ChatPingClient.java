package dev.mewzica.chatping;

import dev.mewzica.chatping.net.ServerPresence;
import dev.mewzica.chatping.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端侧入口。
 *
 * <p>加载器那边（Fabric 的 {@code ClientModInitializer}、Forge 的
 * {@code FMLClientSetupEvent}、NeoForge 的 {@code ClientTickEvent}）只负责
 * 在客户端环境里调一次 {@link #init(Platform)}，剩下的事都在这里。</p>
 */
public final class ChatPingClient {

    public static final String MOD_ID = "chatping";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ChatPingClient() {
    }

    public static void init(Platform platform) {
        platform.registerClientReceivers();

        // 断开连接就忘掉「服务端装了 mod」这件事，下次可能是别的服
        platform.registerClientEvents(ServerPresence::reset);

        LOGGER.info("ChatPing loaded: @name mentions, hover actions, mention autocomplete, edit/delete");
    }
}
