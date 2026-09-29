package com.poptrain.innerdemons.core.network;

import java.util.Objects;
import java.util.Optional;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.condition.ConditionResult;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class MessageType<M> {

    public enum Direction {
        TO_CLIENT,
        TO_SERVER,
        BOTH;

        public boolean toClient() {
            return this != TO_SERVER;
        }

        public boolean toServer() {
            return this != TO_CLIENT;
        }
    }

    private final ResourceLocation id;
    private final Class<M> messageClass;
    private final Direction direction;
    private final Condition<? super M> validator;
    private final CustomPacketPayload.Type<MessagePayload<M>> payloadType;
    private final StreamCodec<? super RegistryFriendlyByteBuf, MessagePayload<M>> payloadCodec;

    private MessageType(Builder<M> builder) {
        this.id = builder.id;
        this.messageClass = builder.messageClass;
        this.direction = builder.direction;
        this.validator = builder.validator;
        this.payloadType = new CustomPacketPayload.Type<>(id);
        this.payloadCodec = builder.codec.map(m -> new MessagePayload<>(this, m), MessagePayload::message);
    }

    public static <M> Builder<M> builder(ResourceLocation id, Class<M> messageClass,
            StreamCodec<? super RegistryFriendlyByteBuf, M> codec) {
        return new Builder<>(id, messageClass, codec);
    }

    public static <M> Builder<M> builder(String path, Class<M> messageClass,
            StreamCodec<? super RegistryFriendlyByteBuf, M> codec) {
        return new Builder<>(Network.id(path), messageClass, codec);
    }

    public static <M> MessageType<M> toClient(String path, Class<M> messageClass,
            StreamCodec<? super RegistryFriendlyByteBuf, M> codec) {
        return builder(path, messageClass, codec).direction(Direction.TO_CLIENT).build();
    }

    public static <M> MessageType<M> toServer(String path, Class<M> messageClass,
            StreamCodec<? super RegistryFriendlyByteBuf, M> codec) {
        return builder(path, messageClass, codec).direction(Direction.TO_SERVER).build();
    }

    public ResourceLocation id() {
        return id;
    }

    public Class<M> messageClass() {
        return messageClass;
    }

    public Direction direction() {
        return direction;
    }

    public Optional<Condition<? super M>> validator() {
        return Optional.ofNullable(validator);
    }

    public ConditionResult validate(M message) {
        return validator == null ? ConditionResult.pass() : validator.evaluate(message);
    }

    public MessagePayload<M> wrap(M message) {
        if (!messageClass.isInstance(message)) {
            throw new IllegalArgumentException("Message " + message + " is not a " + messageClass.getName() + " for " + id);
        }
        return new MessagePayload<>(this, message);
    }

    CustomPacketPayload.Type<MessagePayload<M>> payloadType() {
        return payloadType;
    }

    StreamCodec<? super RegistryFriendlyByteBuf, MessagePayload<M>> payloadCodec() {
        return payloadCodec;
    }

    @Override
    public String toString() {
        return "MessageType[" + id + ", " + direction + "]";
    }

    public static final class Builder<M> {

        private final ResourceLocation id;
        private final Class<M> messageClass;
        private final StreamCodec<? super RegistryFriendlyByteBuf, M> codec;
        private Direction direction = Direction.TO_CLIENT;
        private Condition<? super M> validator;

        private Builder(ResourceLocation id, Class<M> messageClass, StreamCodec<? super RegistryFriendlyByteBuf, M> codec) {
            this.id = Objects.requireNonNull(id, "id");
            this.messageClass = Objects.requireNonNull(messageClass, "messageClass");
            this.codec = Objects.requireNonNull(codec, "codec");
        }

        public Builder<M> direction(Direction direction) {
            this.direction = Objects.requireNonNull(direction, "direction");
            return this;
        }

        public Builder<M> toClient() {
            return direction(Direction.TO_CLIENT);
        }

        public Builder<M> toServer() {
            return direction(Direction.TO_SERVER);
        }

        public Builder<M> bothWays() {
            return direction(Direction.BOTH);
        }

        public Builder<M> validate(Condition<? super M> condition) {
            Objects.requireNonNull(condition, "condition");
            if (validator == null) {
                validator = condition;
            } else {
                Condition<? super M> previous = validator;
                validator = Condition.<M>allOf(previous, condition);
            }
            return this;
        }

        public MessageType<M> build() {
            return new MessageType<>(this);
        }
    }
}
