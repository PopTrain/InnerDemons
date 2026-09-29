package com.poptrain.innerdemons.core.network;

import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record MessageReceivedEvent<M>(MessageType<M> messageType, M message, Player player, boolean clientSide) {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final Class<MessageReceivedEvent<?>> TYPE = (Class) MessageReceivedEvent.class;

    public boolean is(MessageType<?> type) {
        return messageType == type;
    }

    @SuppressWarnings("unchecked")
    public <U> Optional<MessageReceivedEvent<U>> as(MessageType<U> type) {
        return messageType == type ? Optional.of((MessageReceivedEvent<U>) this) : Optional.empty();
    }

    public Optional<ServerPlayer> sender() {
        return !clientSide && player instanceof ServerPlayer serverPlayer ? Optional.of(serverPlayer) : Optional.empty();
    }
}
