package dev.mewzica.chatping.net;

import dev.mewzica.chatping.ChatEditor;
import dev.mewzica.chatping.server.ServerNetwork;
import net.minecraft.server.level.ServerPlayer;

//? if >=1.20.5 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
*///?} else {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//?}

/**
 * 客户端 ↔ 服务端自定义包的门面。
 *
 * <p>为什么要这么一层：<b>Fabric 的网络 API 在 MC 1.20.5 断过一次</b>。</p>
 * <ul>
 *   <li><b>1.20.1 ~ 1.20.4</b>：通道是 {@code ResourceLocation}，包体自己往
 *       {@code FriendlyByteBuf} 里写，收包回调拿的是裸 buf；</li>
 *   <li><b>1.20.5 起</b>：MC 自己加了 {@code CustomPacketPayload} 体系，
 *       Fabric 跟着改成「先定义 payload 类型 + StreamCodec，再收发对象」，
 *       裸 buf 那套 API 直接删了。</li>
 * </ul>
 *
 * <p>两套的调用点长得完全不一样（一个写 buf，一个 new 对象），所以把差异全部关在
 * 这个类里，上层（{@link ChatEditor} / {@link ServerNetwork} / ChatPingServer）
 * 只认 {@code Net.xxx(...)} 这一组方法，一行 {@code //? if} 都不用写。</p>
 *
 * <p>通道语义：</p>
 * <ul>
 *   <li>{@code handshake} 服务端 → 客户端，告诉客户端「服务端也装了 mod」，
 *       顺便带上传「能不能删别人的消息」；</li>
 *   <li>{@code edit} 客户端 → 服务端，请求改某条自己发过的消息；</li>
 *   <li>{@code purge} 客户端 → 服务端，请求删某一条消息（管理员可删别人的 / 系统消息）；</li>
 *   <li>{@code update} 服务端 → 客户端，广播「某条消息被改了」；</li>
 *   <li>{@code remove} 服务端 → 客户端，广播「把这一行删掉」。</li>
 * </ul>
 */
public final class Net {

    private Net() {
    }

    //? if >=1.20.5 {

    /*// ==============================================================
    // 1.20.5+：CustomPacketPayload + StreamCodec
    // ==============================================================

    // id 的类型在 1.21.11 从 ResourceLocation 改名成了 Identifier，
    // 分叉放在 PayloadIds 里（这里整段在块注释中，没法再嵌 //? if）。

    public record Handshake(boolean canDeleteOthers) implements CustomPacketPayload {
        public static final Type<Handshake> TYPE = new Type<>(PayloadIds.id("handshake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Handshake> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeBoolean(v.canDeleteOthers()),
                buf -> new Handshake(buf.readBoolean()));

        @Override
        public Type<Handshake> type() {
            return TYPE;
        }
    }

    public record Edit(String oldBody, String newText) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(PayloadIds.id("edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUtf(v.oldBody());
                    buf.writeUtf(v.newText());
                },
                buf -> new Edit(buf.readUtf(), buf.readUtf()));

        @Override
        public Type<Edit> type() {
            return TYPE;
        }
    }

    public record Purge(String senderName, String body, String display) implements CustomPacketPayload {
        public static final Type<Purge> TYPE = new Type<>(PayloadIds.id("purge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Purge> CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUtf(v.senderName());
                    buf.writeUtf(v.body());
                    buf.writeUtf(v.display());
                },
                buf -> new Purge(buf.readUtf(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<Purge> type() {
            return TYPE;
        }
    }

    public record Update(String senderName, String oldBody, String newRaw) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(PayloadIds.id("update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUtf(v.senderName());
                    buf.writeUtf(v.oldBody());
                    buf.writeUtf(v.newRaw());
                },
                buf -> new Update(buf.readUtf(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<Update> type() {
            return TYPE;
        }
    }

    public record Remove(String display) implements CustomPacketPayload {
        public static final Type<Remove> TYPE = new Type<>(PayloadIds.id("remove"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Remove> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeUtf(v.display()),
                buf -> new Remove(buf.readUtf()));

        @Override
        public Type<Remove> type() {
            return TYPE;
        }
    }

    // 注册 payload 类型。客户端和服务端都要跑（在共用初始化里调一次就够）。
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(Handshake.TYPE, Handshake.CODEC);
        PayloadTypeRegistry.playC2S().register(Edit.TYPE, Edit.CODEC);
        PayloadTypeRegistry.playC2S().register(Purge.TYPE, Purge.CODEC);
        PayloadTypeRegistry.playS2C().register(Update.TYPE, Update.CODEC);
        PayloadTypeRegistry.playS2C().register(Remove.TYPE, Remove.CODEC);
    }

    public static boolean canReceiveHandshake(ServerPlayer player) {
        return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, Handshake.TYPE);
    }

    public static void sendHandshake(ServerPlayer player, boolean canDeleteOthers) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new Handshake(canDeleteOthers));
    }

    public static void sendEdit(String oldBody, String newText) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new Edit(oldBody, newText));
    }

    public static void sendPurge(String senderName, String body, String display) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new Purge(senderName, body, display));
    }

    public static boolean canReceiveUpdate(ServerPlayer player) {
        return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, Update.TYPE);
    }

    public static void sendUpdate(ServerPlayer player, String senderName, String oldBody, String newRaw) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player, new Update(senderName, oldBody, newRaw));
    }

    public static void sendRemove(ServerPlayer player, String display) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new Remove(display));
    }

    public static boolean canReceiveRemove(ServerPlayer player) {
        return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, Remove.TYPE);
    }

    public static void registerClientReceivers() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Handshake.TYPE,
                (payload, context) -> context.client().execute(
                        () -> ServerPresence.setActive(payload.canDeleteOthers())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Update.TYPE,
                (payload, context) -> context.client().execute(
                        () -> ChatEditor.applyRemote(payload.senderName(), payload.oldBody(), payload.newRaw())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Remove.TYPE,
                (payload, context) -> context.client().execute(
                        () -> ChatEditor.applyPurge(payload.display())));
    }

    public static void registerServerReceivers() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(Edit.TYPE,
                (payload, context) -> context.player().level().getServer().execute(() -> ServerNetwork.onEditRequest(
                        context.player().level().getServer(), context.player(), payload.oldBody(), payload.newText())));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(Purge.TYPE,
                (payload, context) -> context.player().level().getServer().execute(() -> ServerNetwork.onPurgeRequest(
                        context.player().level().getServer(), context.player(), payload.senderName(), payload.body(), payload.display())));
    }
    *///?} else {

    // ==============================================================
    // 1.20.1 ~ 1.20.4：ResourceLocation 通道 + 手写 FriendlyByteBuf
    // ==============================================================

    public static final ResourceLocation HANDSHAKE = new ResourceLocation("chatping", "handshake");
    public static final ResourceLocation EDIT = new ResourceLocation("chatping", "edit");
    public static final ResourceLocation PURGE = new ResourceLocation("chatping", "purge");
    public static final ResourceLocation UPDATE = new ResourceLocation("chatping", "update");
    public static final ResourceLocation REMOVE = new ResourceLocation("chatping", "remove");

    /** 老版本没有 payload 类型要注册，空实现。 */
    public static void registerPayloads() {
    }

    public static boolean canReceiveHandshake(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, HANDSHAKE);
    }

    public static void sendHandshake(ServerPlayer player, boolean canDeleteOthers) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(canDeleteOthers);
        ServerPlayNetworking.send(player, HANDSHAKE, buf);
    }

    public static void sendEdit(String oldBody, String newText) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(oldBody);
        buf.writeUtf(newText);
        ClientPlayNetworking.send(EDIT, buf);
    }

    public static void sendPurge(String senderName, String body, String display) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(senderName);
        buf.writeUtf(body);
        buf.writeUtf(display);
        ClientPlayNetworking.send(PURGE, buf);
    }

    public static boolean canReceiveUpdate(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, UPDATE);
    }

    public static void sendUpdate(ServerPlayer player, String senderName, String oldBody, String newRaw) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(senderName);
        buf.writeUtf(oldBody);
        buf.writeUtf(newRaw);
        ServerPlayNetworking.send(player, UPDATE, buf);
    }

    public static void sendRemove(ServerPlayer player, String display) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(display);
        ServerPlayNetworking.send(player, REMOVE, buf);
    }

    public static boolean canReceiveRemove(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, REMOVE);
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(HANDSHAKE, (client, handler, buf, sender) -> {
            boolean deleteOthers = buf.readBoolean();
            client.execute(() -> ServerPresence.setActive(deleteOthers));
        });
        ClientPlayNetworking.registerGlobalReceiver(UPDATE, (client, handler, buf, sender) -> {
            String senderName = buf.readUtf();
            String oldBody = buf.readUtf();
            String newRaw = buf.readUtf();
            client.execute(() -> ChatEditor.applyRemote(senderName, oldBody, newRaw));
        });
        ClientPlayNetworking.registerGlobalReceiver(REMOVE, (client, handler, buf, sender) -> {
            String display = buf.readUtf();
            client.execute(() -> ChatEditor.applyPurge(display));
        });
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(EDIT, (server, player, handler, buf, sender) -> {
            String oldBody = buf.readUtf();
            String newText = buf.readUtf();
            server.execute(() -> ServerNetwork.onEditRequest(server, player, oldBody, newText));
        });
        ServerPlayNetworking.registerGlobalReceiver(PURGE, (server, player, handler, buf, sender) -> {
            String senderName = buf.readUtf();
            String body = buf.readUtf();
            String display = buf.readUtf();
            server.execute(() -> ServerNetwork.onPurgeRequest(server, player, senderName, body, display));
        });
    }

    //?}
}
