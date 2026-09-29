package com.poptrain.innerdemons.client.gltf;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

public final class GltfTextureSet {

    private final GltfMaterialBinding fallback;
    private final Map<String, GltfMaterialBinding> materials;

    private GltfTextureSet(GltfMaterialBinding fallback, Map<String, GltfMaterialBinding> materials) {
        this.fallback = fallback;
        this.materials = Map.copyOf(materials);
    }

    public static GltfTextureSet of(ResourceLocation texture) {
        return new GltfTextureSet(GltfMaterialBinding.of(texture), Map.of());
    }

    public static Builder builder(ResourceLocation fallbackTexture) {
        return new Builder(GltfMaterialBinding.of(fallbackTexture));
    }

    public static Builder builder(GltfMaterialBinding fallback) {
        return new Builder(fallback);
    }

    public GltfMaterialBinding resolve(String materialName) {
        GltfMaterialBinding binding = materials.get(materialName);
        return binding != null ? binding : fallback;
    }

    public GltfMaterialBinding fallback() {
        return fallback;
    }

    public GltfMaterialBinding material(String materialName) {
        return materials.get(materialName);
    }

    public GltfTextureSet with(String materialName, GltfMaterialBinding binding) {
        Map<String, GltfMaterialBinding> copy = new HashMap<>(materials);
        copy.put(materialName, Objects.requireNonNull(binding, "binding"));
        return new GltfTextureSet(fallback, copy);
    }

    public GltfTextureSet withFallback(GltfMaterialBinding binding) {
        return new GltfTextureSet(Objects.requireNonNull(binding, "binding"), materials);
    }

    public GltfTextureSet withFrame(String materialName, int columns, int rows, int index) {
        return with(materialName, resolve(materialName).frame(columns, rows, index));
    }

    public GltfTextureSet retextured(Map<ResourceLocation, ResourceLocation> replacements) {
        GltfMaterialBinding newFallback = swap(fallback, replacements);
        Map<String, GltfMaterialBinding> copy = new HashMap<>();
        materials.forEach((name, binding) -> copy.put(name, swap(binding, replacements)));
        return new GltfTextureSet(newFallback, copy);
    }

    private static GltfMaterialBinding swap(GltfMaterialBinding binding, Map<ResourceLocation, ResourceLocation> replacements) {
        ResourceLocation replacement = replacements.get(binding.texture());
        return replacement == null ? binding : binding.withTexture(replacement);
    }

    public static final class Builder {

        private GltfMaterialBinding fallback;
        private final Map<String, GltfMaterialBinding> materials = new HashMap<>();

        private Builder(GltfMaterialBinding fallback) {
            this.fallback = Objects.requireNonNull(fallback, "fallback");
        }

        public Builder fallback(GltfMaterialBinding binding) {
            this.fallback = Objects.requireNonNull(binding, "binding");
            return this;
        }

        public Builder material(String materialName, ResourceLocation texture) {
            return material(materialName, GltfMaterialBinding.of(texture));
        }

        public Builder material(String materialName, GltfMaterialBinding binding) {
            materials.put(Objects.requireNonNull(materialName, "materialName"), Objects.requireNonNull(binding, "binding"));
            return this;
        }

        public Builder hide(String materialName) {
            return material(materialName, fallback.withVisible(false));
        }

        public GltfTextureSet build() {
            return new GltfTextureSet(fallback, materials);
        }
    }
}
