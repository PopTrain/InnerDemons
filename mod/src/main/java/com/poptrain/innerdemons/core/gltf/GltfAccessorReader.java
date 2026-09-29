package com.poptrain.innerdemons.core.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

final class GltfAccessorReader {

    static final int BYTE = 5120;
    static final int UNSIGNED_BYTE = 5121;
    static final int SHORT = 5122;
    static final int UNSIGNED_SHORT = 5123;
    static final int UNSIGNED_INT = 5125;
    static final int FLOAT = 5126;

    private final JsonArray accessors;
    private final JsonArray bufferViews;
    private final List<byte[]> buffers;

    GltfAccessorReader(JsonArray accessors, JsonArray bufferViews, List<byte[]> buffers) {
        this.accessors = accessors;
        this.bufferViews = bufferViews;
        this.buffers = buffers;
    }

    int count(int accessorIndex) {
        return GltfJson.getInt(accessor(accessorIndex), "count", 0);
    }

    int components(int accessorIndex) {
        return componentsOf(GltfJson.getString(accessor(accessorIndex), "type", "SCALAR"));
    }

    float[] readFloats(int accessorIndex) {
        JsonObject acc = accessor(accessorIndex);
        int count = GltfJson.getInt(acc, "count", 0);
        int comps = componentsOf(GltfJson.getString(acc, "type", "SCALAR"));
        int componentType = GltfJson.getInt(acc, "componentType", FLOAT);
        boolean normalized = GltfJson.getBoolean(acc, "normalized", false);
        float[] out = new float[count * comps];
        if (acc.has("bufferView")) {
            View view = view(GltfJson.getInt(acc, "bufferView", -1), GltfJson.getInt(acc, "byteOffset", 0));
            int elementSize = elementSize(componentType, comps);
            int stride = view.stride > 0 ? view.stride : elementSize;
            checkBounds(view, stride, elementSize, count, accessorIndex);
            int compSize = componentSize(componentType);
            for (int i = 0; i < count; i++) {
                int base = view.offset + i * stride;
                for (int c = 0; c < comps; c++) {
                    out[i * comps + c] = readFloat(view.data, base + c * compSize, componentType, normalized);
                }
            }
        }
        if (acc.has("sparse")) {
            applySparse(acc.getAsJsonObject("sparse"), comps, componentType, normalized, out, null);
        }
        return out;
    }

    int[] readInts(int accessorIndex) {
        JsonObject acc = accessor(accessorIndex);
        int count = GltfJson.getInt(acc, "count", 0);
        int comps = componentsOf(GltfJson.getString(acc, "type", "SCALAR"));
        int componentType = GltfJson.getInt(acc, "componentType", UNSIGNED_INT);
        int[] out = new int[count * comps];
        if (acc.has("bufferView")) {
            View view = view(GltfJson.getInt(acc, "bufferView", -1), GltfJson.getInt(acc, "byteOffset", 0));
            int elementSize = elementSize(componentType, comps);
            int stride = view.stride > 0 ? view.stride : elementSize;
            checkBounds(view, stride, elementSize, count, accessorIndex);
            int compSize = componentSize(componentType);
            for (int i = 0; i < count; i++) {
                int base = view.offset + i * stride;
                for (int c = 0; c < comps; c++) {
                    out[i * comps + c] = readInt(view.data, base + c * compSize, componentType);
                }
            }
        }
        if (acc.has("sparse")) {
            applySparse(acc.getAsJsonObject("sparse"), comps, componentType, false, null, out);
        }
        return out;
    }

    private void applySparse(JsonObject sparse, int comps, int componentType, boolean normalized, float[] floats, int[] ints) {
        int count = GltfJson.getInt(sparse, "count", 0);
        JsonObject indices = sparse.getAsJsonObject("indices");
        JsonObject values = sparse.getAsJsonObject("values");
        int indexType = GltfJson.getInt(indices, "componentType", UNSIGNED_INT);
        View indexView = view(GltfJson.getInt(indices, "bufferView", -1), GltfJson.getInt(indices, "byteOffset", 0));
        View valueView = view(GltfJson.getInt(values, "bufferView", -1), GltfJson.getInt(values, "byteOffset", 0));
        int indexSize = componentSize(indexType);
        int compSize = componentSize(componentType);
        int elementSize = elementSize(componentType, comps);
        checkBounds(indexView, indexSize, indexSize, count, -1);
        checkBounds(valueView, elementSize, elementSize, count, -1);
        int limit = floats != null ? floats.length / comps : ints.length / comps;
        for (int i = 0; i < count; i++) {
            int target = readInt(indexView.data, indexView.offset + i * indexSize, indexType);
            if (target < 0 || target >= limit) {
                throw new GltfException("Sparse accessor index " + target + " out of range");
            }
            int base = valueView.offset + i * elementSize;
            for (int c = 0; c < comps; c++) {
                if (floats != null) {
                    floats[target * comps + c] = readFloat(valueView.data, base + c * compSize, componentType, normalized);
                } else {
                    ints[target * comps + c] = readInt(valueView.data, base + c * compSize, componentType);
                }
            }
        }
    }

    private JsonObject accessor(int index) {
        if (index < 0 || index >= accessors.size()) {
            throw new GltfException("Accessor index " + index + " out of range");
        }
        return accessors.get(index).getAsJsonObject();
    }

    private View view(int bufferViewIndex, int accessorOffset) {
        if (bufferViewIndex < 0 || bufferViewIndex >= bufferViews.size()) {
            throw new GltfException("Buffer view index " + bufferViewIndex + " out of range");
        }
        JsonObject bv = bufferViews.get(bufferViewIndex).getAsJsonObject();
        int bufferIndex = GltfJson.getInt(bv, "buffer", -1);
        if (bufferIndex < 0 || bufferIndex >= buffers.size()) {
            throw new GltfException("Buffer index " + bufferIndex + " out of range");
        }
        ByteBuffer data = ByteBuffer.wrap(buffers.get(bufferIndex)).order(ByteOrder.LITTLE_ENDIAN);
        int viewOffset = GltfJson.getInt(bv, "byteOffset", 0);
        int viewLength = GltfJson.getInt(bv, "byteLength", 0);
        int stride = GltfJson.getInt(bv, "byteStride", 0);
        if (viewOffset + viewLength > data.capacity()) {
            throw new GltfException("Buffer view " + bufferViewIndex + " extends past the end of buffer " + bufferIndex);
        }
        return new View(data, viewOffset + accessorOffset, viewOffset + viewLength, stride);
    }

    private static void checkBounds(View view, int stride, int elementSize, int count, int accessorIndex) {
        if (count == 0) {
            return;
        }
        long end = (long) view.offset + (long) (count - 1) * stride + elementSize;
        if (end > view.end) {
            throw new GltfException("Accessor " + accessorIndex + " reads past the end of its buffer view");
        }
    }

    private static float readFloat(ByteBuffer data, int pos, int componentType, boolean normalized) {
        return switch (componentType) {
            case FLOAT -> data.getFloat(pos);
            case BYTE -> normalized ? Math.max(data.get(pos) / 127f, -1f) : data.get(pos);
            case UNSIGNED_BYTE -> normalized ? (data.get(pos) & 0xFF) / 255f : data.get(pos) & 0xFF;
            case SHORT -> normalized ? Math.max(data.getShort(pos) / 32767f, -1f) : data.getShort(pos);
            case UNSIGNED_SHORT -> normalized ? (data.getShort(pos) & 0xFFFF) / 65535f : data.getShort(pos) & 0xFFFF;
            case UNSIGNED_INT -> (float) (data.getInt(pos) & 0xFFFFFFFFL);
            default -> throw new GltfException("Unsupported component type " + componentType);
        };
    }

    private static int readInt(ByteBuffer data, int pos, int componentType) {
        return switch (componentType) {
            case UNSIGNED_BYTE -> data.get(pos) & 0xFF;
            case BYTE -> data.get(pos);
            case UNSIGNED_SHORT -> data.getShort(pos) & 0xFFFF;
            case SHORT -> data.getShort(pos);
            case UNSIGNED_INT -> data.getInt(pos);
            case FLOAT -> (int) data.getFloat(pos);
            default -> throw new GltfException("Unsupported component type " + componentType);
        };
    }

    static int componentsOf(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new GltfException("Unknown accessor type " + type);
        };
    }

    static int componentSize(int componentType) {
        return switch (componentType) {
            case BYTE, UNSIGNED_BYTE -> 1;
            case SHORT, UNSIGNED_SHORT -> 2;
            case UNSIGNED_INT, FLOAT -> 4;
            default -> throw new GltfException("Unsupported component type " + componentType);
        };
    }

    private static int elementSize(int componentType, int comps) {
        return componentSize(componentType) * comps;
    }

    private record View(ByteBuffer data, int offset, int end, int stride) {
    }
}
