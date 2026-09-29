package com.poptrain.innerdemons.species;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class DemonCodecs {

    public static final String NAMESPACE = "innerdemons";

    public static final Codec<ResourceLocation> ID = Codec.STRING.comapFlatMap(DemonCodecs::parseId, DemonCodecs::writeId);

    private DemonCodecs() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static <T> Codec<ResourceKey<T>> key(ResourceKey<? extends Registry<T>> registry) {
        return ID.xmap(id -> ResourceKey.create(registry, id), ResourceKey::location);
    }

    public static <T> Codec<T> registryEntry(Registry<T> registry) {
        return ID.flatXmap(
                id -> registry.getOptional(id)
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Unknown " + registry.key().location() + " entry '" + id + "'")),
                value -> Optional.ofNullable(registry.getKey(value))
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Unregistered " + registry.key().location() + " entry " + value)));
    }

    private static DataResult<ResourceLocation> parseId(String value) {
        ResourceLocation id = value.indexOf(':') < 0
                ? ResourceLocation.tryBuild(NAMESPACE, value)
                : ResourceLocation.tryParse(value);
        return id != null
                ? DataResult.success(id)
                : DataResult.error(() -> "Invalid id '" + value + "'");
    }

    private static String writeId(ResourceLocation id) {
        return NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }
}
