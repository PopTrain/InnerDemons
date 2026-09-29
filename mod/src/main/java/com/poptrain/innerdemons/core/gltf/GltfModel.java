package com.poptrain.innerdemons.core.gltf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GltfModel {

    private final String name;
    private final List<GltfNode> nodes;
    private final List<GltfMesh> meshes;
    private final List<GltfSkin> skins;
    private final Map<String, GltfAnimation> animations;
    private final List<String> materialNames;
    private final int[] traversalOrder;
    private final int[] renderNodes;
    private final Map<String, Integer> nodesByName;
    private final int[] morphOffsets;
    private final float[] restMorphWeights;
    private final Map<String, int[]> morphIndicesByName;
    private final List<String> morphTargetNames;

    GltfModel(String name, List<GltfNode> nodes, List<GltfMesh> meshes, List<GltfSkin> skins,
              List<GltfAnimation> animations, List<String> materialNames, int[] traversalOrder, int[] renderNodes,
              int[] morphOffsets, int morphWeightCount) {
        this.name = name;
        this.nodes = List.copyOf(nodes);
        this.meshes = List.copyOf(meshes);
        this.skins = List.copyOf(skins);
        Map<String, GltfAnimation> byName = new LinkedHashMap<>();
        for (GltfAnimation animation : animations) {
            byName.putIfAbsent(animation.name(), animation);
        }
        this.animations = Collections.unmodifiableMap(byName);
        this.materialNames = List.copyOf(materialNames);
        this.traversalOrder = traversalOrder;
        this.renderNodes = renderNodes;
        Map<String, Integer> names = new HashMap<>();
        for (GltfNode node : this.nodes) {
            names.putIfAbsent(node.name(), node.index());
        }
        this.nodesByName = Collections.unmodifiableMap(names);
        this.morphOffsets = morphOffsets;
        this.restMorphWeights = new float[morphWeightCount];
        Map<String, List<Integer>> byTarget = new LinkedHashMap<>();
        for (GltfNode node : this.nodes) {
            int offset = morphOffsets[node.index()];
            if (offset < 0) {
                continue;
            }
            GltfMesh mesh = this.meshes.get(node.mesh());
            float[] nodeWeights = node.restMorphWeightsUnsafe();
            for (int t = 0; t < mesh.morphTargetCount(); t++) {
                restMorphWeights[offset + t] = nodeWeights != null && t < nodeWeights.length ? nodeWeights[t] : mesh.defaultWeight(t);
                if (t < mesh.morphTargetNames().size()) {
                    byTarget.computeIfAbsent(mesh.morphTargetNames().get(t), k -> new ArrayList<>()).add(offset + t);
                }
            }
        }
        Map<String, int[]> indices = new LinkedHashMap<>();
        byTarget.forEach((k, v) -> indices.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
        this.morphIndicesByName = Collections.unmodifiableMap(indices);
        this.morphTargetNames = List.copyOf(indices.keySet());
    }

    public String name() {
        return name;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public GltfNode node(int index) {
        return nodes.get(index);
    }

    public List<GltfNode> nodes() {
        return nodes;
    }

    public int nodeIndex(String nodeName) {
        Integer index = nodesByName.get(nodeName);
        return index == null ? -1 : index;
    }

    public GltfMesh mesh(int index) {
        return meshes.get(index);
    }

    public List<GltfMesh> meshes() {
        return meshes;
    }

    public GltfSkin skin(int index) {
        return skins.get(index);
    }

    public List<GltfSkin> skins() {
        return skins;
    }

    public Optional<GltfAnimation> animation(String animationName) {
        return Optional.ofNullable(animations.get(animationName));
    }

    public boolean hasAnimation(String animationName) {
        return animations.containsKey(animationName);
    }

    public Collection<GltfAnimation> animations() {
        return animations.values();
    }

    public List<String> materialNames() {
        return materialNames;
    }

    int[] traversalOrder() {
        return traversalOrder;
    }

    public int[] renderNodes() {
        return renderNodes.clone();
    }

    public int renderNodeCount() {
        return renderNodes.length;
    }

    public int renderNode(int i) {
        return renderNodes[i];
    }

    public int maxJointCount() {
        int max = 0;
        for (GltfSkin skin : skins) {
            max = Math.max(max, skin.jointCount());
        }
        return max;
    }

    public int triangleCount() {
        int total = 0;
        for (int node : renderNodes) {
            for (GltfPrimitive primitive : meshes.get(nodes.get(node).mesh()).primitives()) {
                total += primitive.triangleCount();
            }
        }
        return total;
    }

    public int morphWeightCount() {
        return restMorphWeights.length;
    }

    public int morphOffset(int node) {
        return morphOffsets[node];
    }

    float restMorphWeight(int index) {
        return restMorphWeights[index];
    }

    public List<String> morphTargetNames() {
        return morphTargetNames;
    }

    public boolean hasMorphTarget(String targetName) {
        return morphIndicesByName.containsKey(targetName);
    }

    public int[] morphWeightIndices(String targetName) {
        int[] indices = morphIndicesByName.get(targetName);
        return indices == null ? new int[0] : indices.clone();
    }

    @Override
    public String toString() {
        return "GltfModel[" + name + ", nodes=" + nodes.size() + ", meshes=" + meshes.size() + ", skins=" + skins.size()
                + ", animations=" + animations.keySet() + ", shapeKeys=" + morphTargetNames + ", triangles=" + triangleCount() + "]";
    }
}
