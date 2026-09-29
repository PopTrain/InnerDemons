package com.poptrain.innerdemons.client.gltf;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public enum GltfRenderLayer {
    CUTOUT,
    CUTOUT_CULLED,
    TRANSLUCENT,
    EMISSIVE;

    public RenderType renderType(ResourceLocation texture) {
        return switch (this) {
            case CUTOUT -> RenderType.entityCutoutNoCull(texture);
            case CUTOUT_CULLED -> RenderType.entityCutout(texture);
            case TRANSLUCENT -> RenderType.entityTranslucent(texture);
            case EMISSIVE -> RenderType.eyes(texture);
        };
    }

    public boolean fullBright() {
        return this == EMISSIVE;
    }
}
