package com.poptrain.innerdemons.core.gltf;

import org.joml.Matrix4f;

public final class GltfSkin {

    private final String name;
    private final int[] joints;
    private final Matrix4f[] inverseBindMatrices;

    GltfSkin(String name, int[] joints, Matrix4f[] inverseBindMatrices) {
        this.name = name;
        this.joints = joints;
        this.inverseBindMatrices = inverseBindMatrices;
    }

    public String name() {
        return name;
    }

    public int jointCount() {
        return joints.length;
    }

    public int jointNode(int joint) {
        return joints[joint];
    }

    public Matrix4f inverseBindMatrix(int joint) {
        return inverseBindMatrices[joint];
    }
}
