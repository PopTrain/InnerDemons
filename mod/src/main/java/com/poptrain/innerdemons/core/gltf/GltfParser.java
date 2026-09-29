package com.poptrain.innerdemons.core.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class GltfParser {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private static final int MODE_TRIANGLES = 4;
    private static final int MODE_TRIANGLE_STRIP = 5;
    private static final int MODE_TRIANGLE_FAN = 6;

    private static final Set<String> SUPPORTED_REQUIRED_EXTENSIONS = Set.of(
            "KHR_mesh_quantization",
            "KHR_texture_transform",
            "KHR_materials_unlit",
            "KHR_materials_emissive_strength");

    private GltfParser() {
    }

    public static GltfModel parse(String name, InputStream in, GltfBufferResolver resolver) throws IOException {
        return parse(name, in.readAllBytes(), resolver);
    }

    public static GltfModel parse(String name, byte[] bytes, GltfBufferResolver resolver) {
        try {
            if (isGlb(bytes)) {
                return parseGlb(name, bytes, resolver);
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            return parseJson(name, JsonParser.parseString(stripBom(json)).getAsJsonObject(), null, resolver);
        } catch (GltfException e) {
            throw new GltfException(name + ": " + e.getMessage(), e);
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException | ClassCastException e) {
            throw new GltfException(name + ": malformed glTF JSON (" + e.getMessage() + ")", e);
        } catch (IndexOutOfBoundsException e) {
            throw new GltfException(name + ": truncated or corrupt data (" + e.getMessage() + ")", e);
        }
    }

    public static boolean isGlb(byte[] bytes) {
        return bytes.length >= 12 && ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC;
    }

    private static GltfModel parseGlb(String name, byte[] bytes, GltfBufferResolver resolver) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int version = buf.getInt(4);
        if (version != 2) {
            throw new GltfException("Unsupported GLB version " + version + " (only glTF 2.0 is supported)");
        }
        int totalLength = Math.min(buf.getInt(8), bytes.length);
        int pos = 12;
        JsonObject json = null;
        byte[] bin = null;
        while (pos + 8 <= totalLength) {
            int chunkLength = buf.getInt(pos);
            int chunkType = buf.getInt(pos + 4);
            int dataStart = pos + 8;
            if (chunkLength < 0 || dataStart + chunkLength > totalLength) {
                throw new GltfException("GLB chunk runs past the end of the file");
            }
            if (chunkType == CHUNK_JSON && json == null) {
                String text = new String(bytes, dataStart, chunkLength, StandardCharsets.UTF_8);
                json = JsonParser.parseString(stripBom(text)).getAsJsonObject();
            } else if (chunkType == CHUNK_BIN && bin == null) {
                bin = Arrays.copyOfRange(bytes, dataStart, dataStart + chunkLength);
            }
            pos = dataStart + ((chunkLength + 3) & ~3);
        }
        if (json == null) {
            throw new GltfException("GLB file has no JSON chunk");
        }
        return parseJson(name, json, bin, resolver);
    }

    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '﻿' ? text.substring(1) : text;
    }

    private static GltfModel parseJson(String name, JsonObject root, byte[] glbBin, GltfBufferResolver resolver) {
        checkAsset(root);
        checkExtensions(root);

        List<byte[]> buffers = loadBuffers(root, glbBin, resolver);
        GltfAccessorReader reader = new GltfAccessorReader(
                GltfJson.getArray(root, "accessors"), GltfJson.getArray(root, "bufferViews"), buffers);

        List<String> materialNames = readMaterialNames(root);
        List<GltfMesh> meshes = readMeshes(root, reader, materialNames);
        List<GltfNode> nodes = readNodes(root, meshes.size());
        List<GltfSkin> skins = readSkins(root, reader, nodes.size());
        validateNodeSkins(nodes, skins);
        int[] morphOffsets = new int[nodes.size()];
        int morphWeightCount = 0;
        for (GltfNode node : nodes) {
            int targets = node.hasMesh() ? meshes.get(node.mesh()).morphTargetCount() : 0;
            morphOffsets[node.index()] = targets > 0 ? morphWeightCount : -1;
            morphWeightCount += targets;
        }
        List<GltfAnimation> animations = readAnimations(root, reader, nodes, meshes, morphOffsets);

        int[] traversal = traversalOrder(nodes);
        int[] renderNodes = renderNodes(root, nodes);
        return new GltfModel(name, nodes, meshes, skins, animations, materialNames, traversal, renderNodes,
                morphOffsets, morphWeightCount);
    }

    private static void checkAsset(JsonObject root) {
        if (!root.has("asset")) {
            throw new GltfException("Missing 'asset' block; this does not look like a glTF file");
        }
        String version = GltfJson.getString(root.getAsJsonObject("asset"), "version", "");
        if (!version.startsWith("2.")) {
            throw new GltfException("Unsupported glTF version '" + version + "' (only 2.x is supported)");
        }
    }

    private static void checkExtensions(JsonObject root) {
        for (JsonElement e : GltfJson.getArray(root, "extensionsRequired")) {
            String ext = e.getAsString();
            if (!SUPPORTED_REQUIRED_EXTENSIONS.contains(ext) && !ext.startsWith("KHR_materials_")) {
                throw new GltfException("Required extension '" + ext + "' is not supported. Re-export without compression (e.g. disable Draco in Blender).");
            }
        }
    }

    private static List<byte[]> loadBuffers(JsonObject root, byte[] glbBin, GltfBufferResolver resolver) {
        JsonArray arr = GltfJson.getArray(root, "buffers");
        List<byte[]> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonObject buffer = arr.get(i).getAsJsonObject();
            String uri = GltfJson.getString(buffer, "uri", null);
            int byteLength = GltfJson.getInt(buffer, "byteLength", 0);
            byte[] data;
            if (uri == null) {
                if (i != 0 || glbBin == null) {
                    throw new GltfException("Buffer " + i + " has no uri and there is no GLB binary chunk");
                }
                data = glbBin;
            } else if (uri.startsWith("data:")) {
                int comma = uri.indexOf(',');
                if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) {
                    throw new GltfException("Buffer " + i + " uses an unsupported data URI encoding");
                }
                data = Base64.getDecoder().decode(uri.substring(comma + 1));
            } else {
                String decoded = URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
                if (decoded.contains("..") || decoded.startsWith("/") || decoded.contains(":")) {
                    throw new GltfException("Buffer uri '" + uri + "' must be a relative path next to the model");
                }
                try {
                    data = resolver.resolve(decoded);
                } catch (IOException e) {
                    throw new GltfException("Could not load buffer '" + decoded + "': " + e.getMessage(), e);
                }
            }
            if (data.length < byteLength) {
                throw new GltfException("Buffer " + i + " is " + data.length + " bytes but declares " + byteLength);
            }
            out.add(data);
        }
        return out;
    }

    private static List<String> readMaterialNames(JsonObject root) {
        JsonArray arr = GltfJson.getArray(root, "materials");
        List<String> names = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            names.add(GltfJson.getString(arr.get(i).getAsJsonObject(), "name", "material_" + i));
        }
        return names;
    }

    private static List<GltfMesh> readMeshes(JsonObject root, GltfAccessorReader reader, List<String> materialNames) {
        JsonArray arr = GltfJson.getArray(root, "meshes");
        List<GltfMesh> meshes = new ArrayList<>(arr.size());
        for (int m = 0; m < arr.size(); m++) {
            JsonObject mesh = arr.get(m).getAsJsonObject();
            String meshName = GltfJson.getString(mesh, "name", "mesh_" + m);
            List<GltfPrimitive> primitives = new ArrayList<>();
            JsonArray prims = GltfJson.getArray(mesh, "primitives");
            int targetCount = -1;
            for (int p = 0; p < prims.size(); p++) {
                JsonObject primJson = prims.get(p).getAsJsonObject();
                int primTargets = GltfJson.getArray(primJson, "targets").size();
                if (targetCount < 0) {
                    targetCount = primTargets;
                } else if (targetCount != primTargets) {
                    throw new GltfException("Mesh '" + meshName + "' has primitives with different shape key counts (" + targetCount + " vs " + primTargets + ")");
                }
                GltfPrimitive primitive = readPrimitive(primJson, reader, materialNames, meshName + "#" + p);
                if (primitive != null) {
                    primitives.add(primitive);
                }
            }
            targetCount = Math.max(targetCount, 0);
            float[] defaultWeights = new float[targetCount];
            JsonArray weightsJson = GltfJson.getArray(mesh, "weights");
            for (int t = 0; t < Math.min(targetCount, weightsJson.size()); t++) {
                defaultWeights[t] = weightsJson.get(t).getAsFloat();
            }
            meshes.add(new GltfMesh(meshName, primitives, readTargetNames(mesh, targetCount), defaultWeights));
        }
        return meshes;
    }

    private static List<String> readTargetNames(JsonObject mesh, int targetCount) {
        List<String> names = new ArrayList<>(targetCount);
        JsonObject extras = mesh.has("extras") && mesh.get("extras").isJsonObject() ? mesh.getAsJsonObject("extras") : null;
        JsonArray given = extras == null ? new JsonArray() : GltfJson.getArray(extras, "targetNames");
        for (int t = 0; t < targetCount; t++) {
            JsonElement e = t < given.size() ? given.get(t) : null;
            names.add(e != null && e.isJsonPrimitive() ? e.getAsString() : "target_" + t);
        }
        return names;
    }

    private static GltfPrimitive readPrimitive(JsonObject prim, GltfAccessorReader reader, List<String> materialNames, String label) {
        int mode = GltfJson.getInt(prim, "mode", MODE_TRIANGLES);
        if (mode != MODE_TRIANGLES && mode != MODE_TRIANGLE_STRIP && mode != MODE_TRIANGLE_FAN) {
            return null;
        }
        JsonObject attributes = prim.getAsJsonObject("attributes");
        if (attributes == null || !attributes.has("POSITION")) {
            throw new GltfException("Primitive " + label + " has no POSITION attribute");
        }
        float[] positions = reader.readFloats(attributes.get("POSITION").getAsInt());
        int vertexCount = positions.length / 3;

        float[] normals = attributes.has("NORMAL") ? reader.readFloats(attributes.get("NORMAL").getAsInt()) : null;
        float[] uvs = attributes.has("TEXCOORD_0") ? reader.readFloats(attributes.get("TEXCOORD_0").getAsInt()) : new float[vertexCount * 2];
        requireLength(normals, vertexCount * 3, "NORMAL", label);
        requireLength(uvs, vertexCount * 2, "TEXCOORD_0", label);

        int[] joints = null;
        float[] weights = null;
        if (attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0")) {
            joints = reader.readInts(attributes.get("JOINTS_0").getAsInt());
            weights = reader.readFloats(attributes.get("WEIGHTS_0").getAsInt());
            requireLength(joints, vertexCount * 4, "JOINTS_0", label);
            requireLength(weights, vertexCount * 4, "WEIGHTS_0", label);
            normalizeWeights(weights);
        }

        int[] indices = prim.has("indices")
                ? reader.readInts(prim.get("indices").getAsInt())
                : sequence(vertexCount);
        for (int index : indices) {
            if (index < 0 || index >= vertexCount) {
                throw new GltfException("Primitive " + label + " has vertex index " + index + " but only " + vertexCount + " vertices");
            }
        }
        indices = toTriangleList(indices, mode);

        if (normals == null) {
            normals = computeNormals(positions, indices);
        }

        List<GltfMorphTarget> morphTargets = new ArrayList<>();
        JsonArray targets = GltfJson.getArray(prim, "targets");
        for (int t = 0; t < targets.size(); t++) {
            JsonObject target = targets.get(t).getAsJsonObject();
            float[] dp = target.has("POSITION") ? reader.readFloats(target.get("POSITION").getAsInt()) : null;
            float[] dn = target.has("NORMAL") ? reader.readFloats(target.get("NORMAL").getAsInt()) : null;
            requireLength(dp, vertexCount * 3, "shape key " + t + " POSITION", label);
            requireLength(dn, vertexCount * 3, "shape key " + t + " NORMAL", label);
            morphTargets.add(GltfMorphTarget.fromDense(dp, dn, vertexCount));
        }

        int materialIndex = GltfJson.getInt(prim, "material", -1);
        String materialName = materialIndex >= 0 && materialIndex < materialNames.size() ? materialNames.get(materialIndex) : "";
        return new GltfPrimitive(materialIndex, materialName, positions, normals, uvs, joints, weights, indices, morphTargets);
    }

    private static void requireLength(float[] data, int expected, String attribute, String label) {
        if (data != null && data.length < expected) {
            throw new GltfException("Primitive " + label + " attribute " + attribute + " has too few elements");
        }
    }

    private static void requireLength(int[] data, int expected, String attribute, String label) {
        if (data != null && data.length < expected) {
            throw new GltfException("Primitive " + label + " attribute " + attribute + " has too few elements");
        }
    }

    private static void normalizeWeights(float[] weights) {
        for (int v = 0; v < weights.length / 4; v++) {
            int o = v * 4;
            float sum = weights[o] + weights[o + 1] + weights[o + 2] + weights[o + 3];
            if (sum > 1.0e-6f && Math.abs(sum - 1f) > 1.0e-4f) {
                float inv = 1f / sum;
                for (int c = 0; c < 4; c++) {
                    weights[o + c] *= inv;
                }
            }
        }
    }

    private static int[] sequence(int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = i;
        }
        return out;
    }

    private static int[] toTriangleList(int[] indices, int mode) {
        if (mode == MODE_TRIANGLES) {
            return indices.length % 3 == 0 ? indices : Arrays.copyOf(indices, indices.length - indices.length % 3);
        }
        int triangles = Math.max(0, indices.length - 2);
        int[] out = new int[triangles * 3];
        for (int i = 0; i < triangles; i++) {
            int o = i * 3;
            if (mode == MODE_TRIANGLE_STRIP) {
                if ((i & 1) == 0) {
                    out[o] = indices[i];
                    out[o + 1] = indices[i + 1];
                } else {
                    out[o] = indices[i + 1];
                    out[o + 1] = indices[i];
                }
                out[o + 2] = indices[i + 2];
            } else {
                out[o] = indices[i + 1];
                out[o + 1] = indices[i + 2];
                out[o + 2] = indices[0];
            }
        }
        return out;
    }

    private static float[] computeNormals(float[] positions, int[] indices) {
        float[] normals = new float[positions.length];
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f n = new Vector3f();
        for (int t = 0; t < indices.length; t += 3) {
            int i0 = indices[t] * 3;
            int i1 = indices[t + 1] * 3;
            int i2 = indices[t + 2] * 3;
            a.set(positions[i1] - positions[i0], positions[i1 + 1] - positions[i0 + 1], positions[i1 + 2] - positions[i0 + 2]);
            b.set(positions[i2] - positions[i0], positions[i2 + 1] - positions[i0 + 1], positions[i2 + 2] - positions[i0 + 2]);
            a.cross(b, n);
            for (int idx : new int[]{i0, i1, i2}) {
                normals[idx] += n.x;
                normals[idx + 1] += n.y;
                normals[idx + 2] += n.z;
            }
        }
        for (int i = 0; i < normals.length; i += 3) {
            float len = (float) Math.sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1] + normals[i + 2] * normals[i + 2]);
            if (len > 1.0e-8f) {
                normals[i] /= len;
                normals[i + 1] /= len;
                normals[i + 2] /= len;
            } else {
                normals[i + 1] = 1f;
            }
        }
        return normals;
    }

    private static List<GltfNode> readNodes(JsonObject root, int meshCount) {
        JsonArray arr = GltfJson.getArray(root, "nodes");
        int count = arr.size();
        int[] parents = new int[count];
        Arrays.fill(parents, -1);
        int[][] children = new int[count][];
        for (int i = 0; i < count; i++) {
            children[i] = GltfJson.getInts(arr.get(i).getAsJsonObject(), "children");
            for (int child : children[i]) {
                if (child < 0 || child >= count) {
                    throw new GltfException("Node " + i + " has child index " + child + " out of range");
                }
                if (parents[child] != -1) {
                    throw new GltfException("Node " + child + " has more than one parent");
                }
                parents[child] = i;
            }
        }
        List<GltfNode> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            JsonObject node = arr.get(i).getAsJsonObject();
            float[] t;
            float[] r;
            float[] s;
            if (node.has("matrix")) {
                float[] m = GltfJson.getFloats(node, "matrix", new float[16]);
                Matrix4f matrix = new Matrix4f().set(m);
                Vector3f translation = matrix.getTranslation(new Vector3f());
                Vector3f scale = matrix.getScale(new Vector3f());
                Quaternionf rotation = matrix.getNormalizedRotation(new Quaternionf());
                t = new float[]{translation.x, translation.y, translation.z};
                r = new float[]{rotation.x, rotation.y, rotation.z, rotation.w};
                s = new float[]{scale.x, scale.y, scale.z};
            } else {
                t = GltfJson.getFloats(node, "translation", new float[]{0f, 0f, 0f});
                r = GltfJson.getFloats(node, "rotation", new float[]{0f, 0f, 0f, 1f});
                s = GltfJson.getFloats(node, "scale", new float[]{1f, 1f, 1f});
                GltfChannel.normalizeQuat(r, 0);
            }
            int mesh = GltfJson.getInt(node, "mesh", -1);
            if (mesh >= meshCount) {
                throw new GltfException("Node " + i + " references mesh " + mesh + " out of range");
            }
            int skin = GltfJson.getInt(node, "skin", -1);
            String nodeName = GltfJson.getString(node, "name", "node_" + i);
            float[] morphWeights = null;
            if (node.has("weights")) {
                JsonArray w = GltfJson.getArray(node, "weights");
                morphWeights = new float[w.size()];
                for (int k = 0; k < morphWeights.length; k++) {
                    morphWeights[k] = w.get(k).getAsFloat();
                }
            }
            nodes.add(new GltfNode(i, nodeName, parents[i], children[i], t, r, s, mesh, skin, morphWeights));
        }
        return nodes;
    }

    private static List<GltfSkin> readSkins(JsonObject root, GltfAccessorReader reader, int nodeCount) {
        JsonArray arr = GltfJson.getArray(root, "skins");
        List<GltfSkin> skins = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonObject skin = arr.get(i).getAsJsonObject();
            int[] joints = GltfJson.getInts(skin, "joints");
            for (int joint : joints) {
                if (joint < 0 || joint >= nodeCount) {
                    throw new GltfException("Skin " + i + " references joint node " + joint + " out of range");
                }
            }
            Matrix4f[] ibms = new Matrix4f[joints.length];
            if (skin.has("inverseBindMatrices")) {
                float[] data = reader.readFloats(skin.get("inverseBindMatrices").getAsInt());
                if (data.length < joints.length * 16) {
                    throw new GltfException("Skin " + i + " has fewer inverse bind matrices than joints");
                }
                for (int j = 0; j < joints.length; j++) {
                    ibms[j] = new Matrix4f().set(data, j * 16);
                }
            } else {
                for (int j = 0; j < joints.length; j++) {
                    ibms[j] = new Matrix4f();
                }
            }
            skins.add(new GltfSkin(GltfJson.getString(skin, "name", "skin_" + i), joints, ibms));
        }
        return skins;
    }

    private static void validateNodeSkins(List<GltfNode> nodes, List<GltfSkin> skins) {
        for (GltfNode node : nodes) {
            if (node.skin() >= skins.size()) {
                throw new GltfException("Node '" + node.name() + "' references skin " + node.skin() + " out of range");
            }
        }
    }

    private static List<GltfAnimation> readAnimations(JsonObject root, GltfAccessorReader reader, List<GltfNode> nodes,
                                                      List<GltfMesh> meshes, int[] morphOffsets) {
        int nodeCount = nodes.size();
        JsonArray arr = GltfJson.getArray(root, "animations");
        List<GltfAnimation> animations = new ArrayList<>(arr.size());
        for (int a = 0; a < arr.size(); a++) {
            JsonObject anim = arr.get(a).getAsJsonObject();
            String animName = GltfJson.getString(anim, "name", "animation_" + a);
            JsonArray samplers = GltfJson.getArray(anim, "samplers");
            List<GltfChannel> channels = new ArrayList<>();
            for (JsonElement ce : GltfJson.getArray(anim, "channels")) {
                JsonObject channel = ce.getAsJsonObject();
                JsonObject target = channel.getAsJsonObject("target");
                if (target == null || !target.has("node")) {
                    continue;
                }
                int node = target.get("node").getAsInt();
                if (node < 0 || node >= nodeCount) {
                    throw new GltfException("Animation '" + animName + "' targets node " + node + " out of range");
                }
                GltfChannel.Path path = switch (GltfJson.getString(target, "path", "")) {
                    case "translation" -> GltfChannel.Path.TRANSLATION;
                    case "rotation" -> GltfChannel.Path.ROTATION;
                    case "scale" -> GltfChannel.Path.SCALE;
                    case "weights" -> GltfChannel.Path.WEIGHTS;
                    default -> null;
                };
                if (path == null) {
                    continue;
                }
                int samplerIndex = GltfJson.getInt(channel, "sampler", -1);
                if (samplerIndex < 0 || samplerIndex >= samplers.size()) {
                    throw new GltfException("Animation '" + animName + "' references sampler " + samplerIndex + " out of range");
                }
                JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
                float[] times = reader.readFloats(GltfJson.getInt(sampler, "input", -1));
                float[] values = reader.readFloats(GltfJson.getInt(sampler, "output", -1));
                GltfInterpolation interpolation = GltfInterpolation.fromJson(GltfJson.getString(sampler, "interpolation", null));
                if (path == GltfChannel.Path.WEIGHTS) {
                    GltfNode targetNode = nodes.get(node);
                    int targetCount = targetNode.hasMesh() ? meshes.get(targetNode.mesh()).morphTargetCount() : 0;
                    if (targetCount == 0) {
                        continue;
                    }
                    channels.add(new GltfChannel(node, path, interpolation, times, values, targetCount, morphOffsets[node]));
                } else {
                    channels.add(new GltfChannel(node, path, interpolation, times, values));
                }
            }
            animations.add(new GltfAnimation(animName, channels));
        }
        return animations;
    }

    private static int[] traversalOrder(List<GltfNode> nodes) {
        int[] order = new int[nodes.size()];
        int written = 0;
        Deque<Integer> stack = new ArrayDeque<>();
        for (GltfNode node : nodes) {
            if (node.parent() < 0) {
                stack.push(node.index());
                while (!stack.isEmpty()) {
                    int current = stack.pop();
                    order[written++] = current;
                    int[] children = nodes.get(current).children();
                    for (int c = children.length - 1; c >= 0; c--) {
                        stack.push(children[c]);
                    }
                }
            }
        }
        if (written != nodes.size()) {
            throw new GltfException("Node hierarchy contains a cycle");
        }
        return order;
    }

    private static int[] renderNodes(JsonObject root, List<GltfNode> nodes) {
        JsonArray scenes = GltfJson.getArray(root, "scenes");
        int[] roots;
        if (scenes.isEmpty()) {
            roots = nodes.stream().filter(n -> n.parent() < 0).mapToInt(GltfNode::index).toArray();
        } else {
            int scene = GltfJson.getInt(root, "scene", 0);
            if (scene < 0 || scene >= scenes.size()) {
                throw new GltfException("Default scene " + scene + " out of range");
            }
            roots = GltfJson.getInts(scenes.get(scene).getAsJsonObject(), "nodes");
        }
        List<Integer> out = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        for (int r : roots) {
            if (r < 0 || r >= nodes.size()) {
                throw new GltfException("Scene root node " + r + " out of range");
            }
            stack.push(r);
        }
        while (!stack.isEmpty()) {
            GltfNode node = nodes.get(stack.pop());
            if (node.hasMesh()) {
                out.add(node.index());
            }
            for (int child : node.children()) {
                stack.push(child);
            }
        }
        out.sort(Integer::compare);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
