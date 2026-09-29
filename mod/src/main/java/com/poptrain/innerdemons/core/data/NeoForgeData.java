package com.poptrain.innerdemons.core.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

import java.util.Objects;
import java.util.function.Function;

public final class NeoForgeData {

    private NeoForgeData() {
    }

    public static <C extends DataContainer> IAttachmentSerializer<CompoundTag, C> serializer(Function<IAttachmentHolder, C> factory) {
        Objects.requireNonNull(factory, "factory");
        return new IAttachmentSerializer<>() {
            @Override
            public C read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                C container = factory.apply(holder);
                DataNbt.load(container, tag);
                return container;
            }

            @Override
            public CompoundTag write(C container, HolderLookup.Provider provider) {
                CompoundTag tag = DataNbt.save(container);
                return tag.isEmpty() ? null : tag;
            }
        };
    }

    public static <C extends DataContainer> AttachmentType<C> type(Function<IAttachmentHolder, C> factory) {
        return AttachmentType.builder(factory).serialize(serializer(factory)).build();
    }

    public static <C extends DataContainer> AttachmentType<C> typeCopiedOnDeath(Function<IAttachmentHolder, C> factory) {
        return AttachmentType.builder(factory).serialize(serializer(factory)).copyOnDeath().build();
    }
}
