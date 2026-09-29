package com.poptrain.innerdemons.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public sealed interface SyncTarget permits SyncTarget.OfEntity, SyncTarget.OfEntityAttachment, SyncTarget.Custom {

    StreamCodec<ByteBuf, SyncTarget> STREAM_CODEC = StreamCodec.of(SyncTarget::write, SyncTarget::read);

    static SyncTarget entity(int entityId) {
        return new OfEntity(entityId);
    }

    static SyncTarget entityAttachment(int entityId, ResourceLocation attachment) {
        return new OfEntityAttachment(entityId, attachment);
    }

    static SyncTarget custom(ResourceLocation kind, String id) {
        return new Custom(kind, id);
    }

    record OfEntity(int entityId) implements SyncTarget {
    }

    record OfEntityAttachment(int entityId, ResourceLocation attachment) implements SyncTarget {
    }

    record Custom(ResourceLocation kind, String id) implements SyncTarget {
    }

    private static void write(ByteBuf buf, SyncTarget target) {
        switch (target) {
            case OfEntity e -> {
                buf.writeByte(0);
                ByteBufCodecs.VAR_INT.encode(buf, e.entityId());
            }
            case OfEntityAttachment a -> {
                buf.writeByte(1);
                ByteBufCodecs.VAR_INT.encode(buf, a.entityId());
                ResourceLocation.STREAM_CODEC.encode(buf, a.attachment());
            }
            case Custom c -> {
                buf.writeByte(2);
                ResourceLocation.STREAM_CODEC.encode(buf, c.kind());
                ByteBufCodecs.STRING_UTF8.encode(buf, c.id());
            }
        }
    }

    private static SyncTarget read(ByteBuf buf) {
        byte kind = buf.readByte();
        return switch (kind) {
            case 0 -> new OfEntity(ByteBufCodecs.VAR_INT.decode(buf));
            case 1 -> new OfEntityAttachment(ByteBufCodecs.VAR_INT.decode(buf), ResourceLocation.STREAM_CODEC.decode(buf));
            case 2 -> new Custom(ResourceLocation.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf));
            default -> throw new IllegalArgumentException("Unknown sync target kind " + kind);
        };
    }
}
