package com.poptrain.innerdemons.core.gltf;

public final class GltfMorphTarget {

    private static final float EPSILON = 1.0e-7f;

    private final int[] vertices;
    private final float[] positionDeltas;
    private final float[] normalDeltas;

    private GltfMorphTarget(int[] vertices, float[] positionDeltas, float[] normalDeltas) {
        this.vertices = vertices;
        this.positionDeltas = positionDeltas;
        this.normalDeltas = normalDeltas;
    }

    static GltfMorphTarget fromDense(float[] positions, float[] normals, int vertexCount) {
        int count = 0;
        boolean[] touched = new boolean[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            if (nonZero(positions, v) || nonZero(normals, v)) {
                touched[v] = true;
                count++;
            }
        }
        int[] vertices = new int[count];
        float[] pos = new float[count * 3];
        float[] nrm = normals == null ? null : new float[count * 3];
        int i = 0;
        for (int v = 0; v < vertexCount; v++) {
            if (!touched[v]) {
                continue;
            }
            vertices[i] = v;
            for (int c = 0; c < 3; c++) {
                pos[i * 3 + c] = positions == null ? 0f : positions[v * 3 + c];
                if (nrm != null) {
                    nrm[i * 3 + c] = normals[v * 3 + c];
                }
            }
            i++;
        }
        return new GltfMorphTarget(vertices, pos, nrm);
    }

    private static boolean nonZero(float[] data, int v) {
        if (data == null) {
            return false;
        }
        int o = v * 3;
        return Math.abs(data[o]) > EPSILON || Math.abs(data[o + 1]) > EPSILON || Math.abs(data[o + 2]) > EPSILON;
    }

    public int affectedVertexCount() {
        return vertices.length;
    }

    public int vertex(int i) {
        return vertices[i];
    }

    public boolean hasNormals() {
        return normalDeltas != null;
    }

    public void apply(float weight, float[] positions, float[] normals) {
        if (weight == 0f) {
            return;
        }
        for (int i = 0; i < vertices.length; i++) {
            int o = vertices[i] * 3;
            int d = i * 3;
            positions[o] += positionDeltas[d] * weight;
            positions[o + 1] += positionDeltas[d + 1] * weight;
            positions[o + 2] += positionDeltas[d + 2] * weight;
            if (normalDeltas != null) {
                normals[o] += normalDeltas[d] * weight;
                normals[o + 1] += normalDeltas[d + 1] * weight;
                normals[o + 2] += normalDeltas[d + 2] * weight;
            }
        }
    }
}
