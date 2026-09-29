package com.poptrain.innerdemons.core.gltf;

import java.util.List;

public final class GltfMesh {

    private final String name;
    private final List<GltfPrimitive> primitives;
    private final List<String> targetNames;
    private final float[] defaultWeights;

    GltfMesh(String name, List<GltfPrimitive> primitives, List<String> targetNames, float[] defaultWeights) {
        this.name = name;
        this.primitives = List.copyOf(primitives);
        this.targetNames = List.copyOf(targetNames);
        this.defaultWeights = defaultWeights;
    }

    public String name() {
        return name;
    }

    public List<GltfPrimitive> primitives() {
        return primitives;
    }

    public int morphTargetCount() {
        return defaultWeights.length;
    }

    public List<String> morphTargetNames() {
        return targetNames;
    }

    public int morphTargetIndex(String targetName) {
        return targetNames.indexOf(targetName);
    }

    public float defaultWeight(int target) {
        return defaultWeights[target];
    }
}
