package com.poptrain.innerdemons.core.gltf;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class GltfPose {

    public static final int TRANSLATION = 0;
    public static final int ROTATION = 3;
    public static final int SCALE = 7;
    public static final int STRIDE = 10;

    private final float[] data;
    private final float[] morph;
    private final int nodeCount;

    public GltfPose(GltfModel model) {
        this.nodeCount = model.nodeCount();
        this.data = new float[nodeCount * STRIDE];
        this.morph = new float[model.morphWeightCount()];
        resetToRest(model);
    }

    public int nodeCount() {
        return nodeCount;
    }

    float[] data() {
        return data;
    }

    float[] morphData() {
        return morph;
    }

    public boolean fits(GltfModel model) {
        return model.nodeCount() == nodeCount && model.morphWeightCount() == morph.length;
    }

    public GltfPose resetToRest(GltfModel model) {
        checkFits(model);
        for (int i = 0; i < nodeCount; i++) {
            GltfNode node = model.node(i);
            int o = i * STRIDE;
            for (int c = 0; c < 3; c++) {
                data[o + TRANSLATION + c] = node.restTranslation(c);
                data[o + SCALE + c] = node.restScale(c);
            }
            for (int c = 0; c < 4; c++) {
                data[o + ROTATION + c] = node.restRotation(c);
            }
        }
        for (int i = 0; i < morph.length; i++) {
            morph[i] = model.restMorphWeight(i);
        }
        return this;
    }

    public GltfPose copyFrom(GltfPose other) {
        checkSameSize(other);
        System.arraycopy(other.data, 0, data, 0, data.length);
        System.arraycopy(other.morph, 0, morph, 0, morph.length);
        return this;
    }

    public GltfPose blendTowards(GltfPose target, float weight) {
        checkSameSize(target);
        if (weight <= 0f) {
            return this;
        }
        if (weight >= 1f) {
            return copyFrom(target);
        }
        float[] b = target.data;
        for (int i = 0; i < nodeCount; i++) {
            int o = i * STRIDE;
            for (int c = 0; c < 3; c++) {
                data[o + TRANSLATION + c] += (b[o + TRANSLATION + c] - data[o + TRANSLATION + c]) * weight;
                data[o + SCALE + c] += (b[o + SCALE + c] - data[o + SCALE + c]) * weight;
            }
            GltfChannel.slerp(data, o + ROTATION, b, o + ROTATION, weight, data, o + ROTATION);
        }
        for (int i = 0; i < morph.length; i++) {
            morph[i] += (target.morph[i] - morph[i]) * weight;
        }
        return this;
    }

    public void setTranslation(int node, float x, float y, float z) {
        int o = node * STRIDE + TRANSLATION;
        data[o] = x;
        data[o + 1] = y;
        data[o + 2] = z;
    }

    public void setRotation(int node, float x, float y, float z, float w) {
        int o = node * STRIDE + ROTATION;
        data[o] = x;
        data[o + 1] = y;
        data[o + 2] = z;
        data[o + 3] = w;
        GltfChannel.normalizeQuat(data, o);
    }

    public void setScale(int node, float x, float y, float z) {
        int o = node * STRIDE + SCALE;
        data[o] = x;
        data[o + 1] = y;
        data[o + 2] = z;
    }

    public void rotateLocal(int node, Quaternionf delta) {
        Quaternionf current = getRotation(node, new Quaternionf());
        current.mul(delta);
        setRotation(node, current.x, current.y, current.z, current.w);
    }

    public int morphWeightCount() {
        return morph.length;
    }

    public float morphWeight(int index) {
        return morph[index];
    }

    public void setMorphWeight(int index, float weight) {
        morph[index] = weight;
    }

    public void setMorphWeight(GltfModel model, String targetName, float weight) {
        for (int index : model.morphWeightIndices(targetName)) {
            morph[index] = weight;
        }
    }

    public void addMorphWeight(GltfModel model, String targetName, float delta) {
        for (int index : model.morphWeightIndices(targetName)) {
            morph[index] += delta;
        }
    }

    public float morphWeight(GltfModel model, String targetName) {
        int[] indices = model.morphWeightIndices(targetName);
        return indices.length == 0 ? 0f : morph[indices[0]];
    }

    public void setNodeMorphWeight(GltfModel model, int node, int target, float weight) {
        int offset = model.morphOffset(node);
        if (offset < 0 || target < 0 || target >= model.mesh(model.node(node).mesh()).morphTargetCount()) {
            throw new IllegalArgumentException("Node " + node + " has no morph target " + target);
        }
        morph[offset + target] = weight;
    }

    public Quaternionf getRotation(int node, Quaternionf dest) {
        int o = node * STRIDE + ROTATION;
        return dest.set(data[o], data[o + 1], data[o + 2], data[o + 3]);
    }

    public float get(int node, int offset) {
        return data[node * STRIDE + offset];
    }

    public Matrix4f localMatrix(int node, Matrix4f dest) {
        int o = node * STRIDE;
        return dest.translationRotateScale(
                data[o], data[o + 1], data[o + 2],
                data[o + 3], data[o + 4], data[o + 5], data[o + 6],
                data[o + 7], data[o + 8], data[o + 9]);
    }

    public void computeWorldMatrices(GltfModel model, Matrix4f[] out) {
        checkFits(model);
        if (out.length < nodeCount) {
            throw new IllegalArgumentException("Output array holds " + out.length + " matrices, model has " + nodeCount + " nodes");
        }
        int[] order = model.traversalOrder();
        for (int node : order) {
            Matrix4f local = localMatrix(node, out[node]);
            int parent = model.node(node).parent();
            if (parent >= 0) {
                out[parent].mul(local, local);
            }
        }
    }

    public static void computeJointMatrices(GltfSkin skin, Matrix4f[] world, float[] out) {
        Matrix4f scratch = new Matrix4f();
        for (int j = 0; j < skin.jointCount(); j++) {
            world[skin.jointNode(j)].mul(skin.inverseBindMatrix(j), scratch);
            scratch.get(out, j * 16);
        }
    }

    private void checkFits(GltfModel model) {
        if (!fits(model)) {
            throw new IllegalArgumentException("Pose does not match model '" + model.name() + "' (" + nodeCount + " nodes, "
                    + morph.length + " shape key weights vs " + model.nodeCount() + " nodes, " + model.morphWeightCount() + ")");
        }
    }

    private void checkSameSize(GltfPose other) {
        if (other.nodeCount != nodeCount || other.morph.length != morph.length) {
            throw new IllegalArgumentException("Pose sizes differ: " + nodeCount + " vs " + other.nodeCount);
        }
    }
}
