package com.poptrain.innerdemons.core.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DataSyncPayload(SyncTarget target, ResourceLocation registry, CompoundTag values, List<String> removed)
        implements CustomPacketPayload {

    public static final Type<DataSyncPayload> TYPE = new Type<>(Network.id("data_sync"));

    public static final StreamCodec<ByteBuf, DataSyncPayload> STREAM_CODEC = StreamCodec.composite(
            SyncTarget.STREAM_CODEC, DataSyncPayload::target,
            ResourceLocation.STREAM_CODEC, DataSyncPayload::registry,
            ByteBufCodecs.COMPOUND_TAG, DataSyncPayload::values,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DataSyncPayload::removed,
            DataSyncPayload::new);

    public DataSyncPayload {
        removed = List.copyOf(removed);
    }

    public boolean isEmpty() {
        return values.isEmpty() && removed.isEmpty();
    }

    @Override
    public Type<DataSyncPayload> type() {
        return TYPE;
    }
}
