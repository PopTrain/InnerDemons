package com.poptrain.innerdemons.core.network;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.poptrain.innerdemons.client.network.ClientDataSync;
import com.poptrain.innerdemons.core.condition.ConditionResult;
import com.poptrain.innerdemons.core.event.EventBus;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Network.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class Network {

    public static final String MODID = "innerdemons";
    public static final String VERSION = "1";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, MessageType<?>> MESSAGES = new ConcurrentHashMap<>();
    private static volatile boolean registered;

    private Network() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static void register(MessageType<?>... types) {
        register(List.of(types));
    }

    public static void register(Collection<? extends MessageType<?>> types) {
        if (registered) {
            throw new IllegalStateException("Message types must be registered before RegisterPayloadHandlersEvent, "
                    + "for example from the mod constructor");
        }
        for (MessageType<?> type : types) {
            if (type.id().equals(DataSyncPayload.TYPE.id())) {
                throw new IllegalArgumentException("Message id " + type.id() + " is reserved");
            }
            MessageType<?> previous = MESSAGES.putIfAbsent(type.id(), type);
            if (previous != null && previous != type) {
                throw new IllegalArgumentException("Duplicate message id " + type.id());
            }
        }
    }

    public static boolean isRegistered(MessageType<?> type) {
        return registered && MESSAGES.get(type.id()) == type;
    }

    public static <M> void sendToPlayer(ServerPlayer player, MessageType<M> type, M message) {
        PacketDistributor.sendToPlayer(player, clientbound(type, message));
    }

    public static <M> void sendToPlayers(Collection<ServerPlayer> players, MessageType<M> type, M message) {
        MessagePayload<M> payload = clientbound(type, message);
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static <M> void sendToTracking(Entity entity, MessageType<M> type, M message) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, clientbound(type, message));
    }

    public static <M> void sendToTrackingAndSelf(Entity entity, MessageType<M> type, M message) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, clientbound(type, message));
    }

    public static <M> void sendToLevel(ServerLevel level, MessageType<M> type, M message) {
        PacketDistributor.sendToPlayersInDimension(level, clientbound(type, message));
    }

    public static <M> void sendToAll(MessageType<M> type, M message) {
        PacketDistributor.sendToAllPlayers(clientbound(type, message));
    }

    public static <M> void sendToServer(MessageType<M> type, M message) {
        if (!type.direction().toServer()) {
            throw new IllegalArgumentException(type + " can't be sent to the server");
        }
        requireRegistered(type);
        PacketDistributor.sendToServer(type.wrap(message));
    }

    private static <M> MessagePayload<M> clientbound(MessageType<M> type, M message) {
        if (!type.direction().toClient()) {
            throw new IllegalArgumentException(type + " can't be sent to clients");
        }
        requireRegistered(type);
        return type.wrap(message);
    }

    private static void requireRegistered(MessageType<?> type) {
        if (!isRegistered(type)) {
            throw new IllegalStateException(type + " was not registered with Network.register(...)");
        }
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        registered = true;
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(DataSyncPayload.TYPE, DataSyncPayload.STREAM_CODEC, Network::handleDataSync);
        for (MessageType<?> type : MESSAGES.values()) {
            registerMessage(registrar, type);
        }
    }

    private static <M> void registerMessage(PayloadRegistrar registrar, MessageType<M> type) {
        switch (type.direction()) {
            case TO_CLIENT -> registrar.playToClient(type.payloadType(), type.payloadCodec(), Network::handleMessage);
            case TO_SERVER -> registrar.playToServer(type.payloadType(), type.payloadCodec(), Network::handleMessage);
            case BOTH -> registrar.playBidirectional(type.payloadType(), type.payloadCodec(), Network::handleMessage);
        }
    }

    private static void handleDataSync(DataSyncPayload payload, IPayloadContext context) {
        ClientDataSync.handle(payload);
    }

    private static <M> void handleMessage(MessagePayload<M> payload, IPayloadContext context) {
        boolean clientSide = context.flow().isClientbound();
        MessageType<M> type = payload.messageType();
        M message = payload.message();
        Player player = context.player();
        if (!clientSide) {
            ConditionResult result;
            try {
                result = type.validate(message);
            } catch (RuntimeException e) {
                LOGGER.warn("Validator for {} threw on a message from {}", type.id(), player.getGameProfile().getName(), e);
                return;
            }
            if (result.failed()) {
                LOGGER.warn("Dropped {} from {}: {}", type.id(), player.getGameProfile().getName(), result.reason());
                return;
            }
        }
        EventBus bus = clientSide ? NetworkBuses.client() : NetworkBuses.serverIfRunning().orElse(null);
        if (bus == null) {
            return;
        }
        bus.post(new MessageReceivedEvent<>(type, message, player, clientSide));
    }
}
