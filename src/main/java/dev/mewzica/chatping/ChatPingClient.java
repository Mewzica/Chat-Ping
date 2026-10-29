package dev.mewzica.chatping;

import dev.mewzica.chatping.net.ClientNetwork;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatPingClient implements ClientModInitializer {

    public static final String MOD_ID = "chatping";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ClientNetwork.register();
        LOGGER.info("ChatPing loaded: @name mentions, hover actions, mention autocomplete, edit/delete");
    }
}
