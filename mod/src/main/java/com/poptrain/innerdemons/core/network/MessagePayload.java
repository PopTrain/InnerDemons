package com.poptrain.innerdemons.core.network;

import java.util.Objects;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MessagePayload<M>(MessageType<M> messageType, M message) implements CustomPacketPayload {

    public MessagePayload {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(message, "message");
    }

    @Override
    public Type<MessagePayload<M>> type() {
        return messageType.payloadType();
    }
}
