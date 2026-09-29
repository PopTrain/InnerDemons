package com.poptrain.innerdemons.client.gltf;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

public record GltfMaterialBinding(
        ResourceLocation texture,
        GltfRenderLayer layer,
        float uOffset,
        float vOffset,
        float uScale,
        float vScale,
        int color,
        boolean visible) {

    public static final int WHITE = 0xFFFFFFFF;

    public GltfMaterialBinding {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(layer, "layer");
    }

    public static GltfMaterialBinding of(ResourceLocation texture) {
        return new GltfMaterialBinding(texture, GltfRenderLayer.CUTOUT, 0f, 0f, 1f, 1f, WHITE, true);
    }

    public static GltfMaterialBinding of(ResourceLocation texture, GltfRenderLayer layer) {
        return new GltfMaterialBinding(texture, layer, 0f, 0f, 1f, 1f, WHITE, true);
    }

    public GltfMaterialBinding withTexture(ResourceLocation newTexture) {
        return new GltfMaterialBinding(newTexture, layer, uOffset, vOffset, uScale, vScale, color, visible);
    }

    public GltfMaterialBinding withLayer(GltfRenderLayer newLayer) {
        return new GltfMaterialBinding(texture, newLayer, uOffset, vOffset, uScale, vScale, color, visible);
    }

    public GltfMaterialBinding withColor(int argb) {
        return new GltfMaterialBinding(texture, layer, uOffset, vOffset, uScale, vScale, argb, visible);
    }

    public GltfMaterialBinding withVisible(boolean newVisible) {
        return new GltfMaterialBinding(texture, layer, uOffset, vOffset, uScale, vScale, color, newVisible);
    }

    public GltfMaterialBinding withUv(float newUOffset, float newVOffset, float newUScale, float newVScale) {
        return new GltfMaterialBinding(texture, layer, newUOffset, newVOffset, newUScale, newVScale, color, visible);
    }

    public GltfMaterialBinding frame(int columns, int rows, int index) {
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("Sprite sheet needs at least one column and row");
        }
        int total = columns * rows;
        int i = Math.floorMod(index, total);
        float cw = 1f / columns;
        float rh = 1f / rows;
        return withUv((i % columns) * cw, (i / columns) * rh, cw, rh);
    }

    public float u(float u) {
        return uOffset + u * uScale;
    }

    public float v(float v) {
        return vOffset + v * vScale;
    }
}
