package com.poptrain.innerdemons.core.gltf;

import java.util.List;

public final class GltfPrimitive {

    public static final int MAX_INFLUENCES = 4;

    private final int materialIndex;
    private final String materialName;
    private final int vertexCount;
    private final float[] positions;
    private final float[] normals;
    private final float[] uvs;
    private final int[] joints;
    private final float[] weights;
    private final int[] indices;
    private final List<GltfMorphTarget> morphTargets;

    GltfPrimitive(int materialIndex, String materialName, float[] positions, float[] normals, float[] uvs,
                  int[] joints, float[] weights, int[] indices, List<GltfMorphTarget> morphTargets) {
        this.materialIndex = materialIndex;
        this.materialName = materialName;
        this.vertexCount = positions.length / 3;
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.joints = joints;
        this.weights = weights;
        this.indices = indices;
        this.morphTargets = List.copyOf(morphTargets);
    }

    public int materialIndex() {
        return materialIndex;
    }

    public String materialName() {
        return materialName;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    public boolean isSkinned() {
        return joints != null && weights != null;
    }

    public float[] positions() {
        return positions;
    }

    public float[] normals() {
        return normals;
    }

    public float[] uvs() {
        return uvs;
    }

    public int[] joints() {
        return joints;
    }

    public float[] weights() {
        return weights;
    }

    public int[] indices() {
        return indices;
    }

    public List<GltfMorphTarget> morphTargets() {
        return morphTargets;
    }

    public boolean hasMorphTargets() {
        return !morphTargets.isEmpty();
    }
}
