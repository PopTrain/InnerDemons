package com.poptrain.innerdemons.core.gltf;

public final class GltfNode {

    private final int index;
    private final String name;
    private final int parent;
    private final int[] children;
    private final float[] restTranslation;
    private final float[] restRotation;
    private final float[] restScale;
    private final int mesh;
    private final int skin;
    private final float[] restMorphWeights;

    GltfNode(int index, String name, int parent, int[] children, float[] restTranslation, float[] restRotation,
             float[] restScale, int mesh, int skin, float[] restMorphWeights) {
        this.index = index;
        this.name = name;
        this.parent = parent;
        this.children = children;
        this.restTranslation = restTranslation;
        this.restRotation = restRotation;
        this.restScale = restScale;
        this.mesh = mesh;
        this.skin = skin;
        this.restMorphWeights = restMorphWeights;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public int parent() {
        return parent;
    }

    public int[] children() {
        return children.clone();
    }

    public float restTranslation(int axis) {
        return restTranslation[axis];
    }

    public float restRotation(int component) {
        return restRotation[component];
    }

    public float restScale(int axis) {
        return restScale[axis];
    }

    public int mesh() {
        return mesh;
    }

    public int skin() {
        return skin;
    }

    public boolean hasMesh() {
        return mesh >= 0;
    }

    public boolean hasSkin() {
        return skin >= 0;
    }

    public float[] restMorphWeights() {
        return restMorphWeights == null ? null : restMorphWeights.clone();
    }

    float[] restMorphWeightsUnsafe() {
        return restMorphWeights;
    }
}
