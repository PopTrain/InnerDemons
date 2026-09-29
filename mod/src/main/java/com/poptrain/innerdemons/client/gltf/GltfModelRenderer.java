package com.poptrain.innerdemons.client.gltf;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.poptrain.innerdemons.core.gltf.GltfMesh;
import com.poptrain.innerdemons.core.gltf.GltfModel;
import com.poptrain.innerdemons.core.gltf.GltfNode;
import com.poptrain.innerdemons.core.gltf.GltfPose;
import com.poptrain.innerdemons.core.gltf.GltfPrimitive;
import com.poptrain.innerdemons.core.gltf.GltfSkin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;

public final class GltfModelRenderer {

    private Matrix4f[] world = new Matrix4f[0];
    private float[] joints = new float[0];
    private float[] positions = new float[0];
    private float[] normals = new float[0];
    private float[] morphPositions = new float[0];
    private float[] morphNormals = new float[0];
    private float[] srcPositions;
    private float[] srcNormals;
    private final float[] nodeMatrix = new float[16];

    public void render(GltfModel model, GltfPose pose, GltfTextureSet textures, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        render(model, pose, textures, poseStack, buffers, packedLight, packedOverlay, GltfMaterialBinding.WHITE);
    }

    public void render(GltfModel model, GltfPose pose, GltfTextureSet textures, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay, int tint) {
        ensureWorld(model.nodeCount());
        pose.computeWorldMatrices(model, world);

        PoseStack.Pose last = poseStack.last();
        Matrix4f poseMatrix = last.pose();
        Matrix3f normalMatrix = last.normal();

        int skinned = -1;
        for (int r = 0; r < model.renderNodeCount(); r++) {
            GltfNode node = model.node(model.renderNode(r));
            GltfMesh mesh = model.mesh(node.mesh());
            GltfSkin skin = node.hasSkin() ? model.skin(node.skin()) : null;
            if (skin != null && skinned != node.skin()) {
                ensureJoints(skin.jointCount());
                GltfPose.computeJointMatrices(skin, world, joints);
                skinned = node.skin();
            }
            world[node.index()].get(nodeMatrix);
            int morphOffset = model.morphOffset(node.index());
            for (GltfPrimitive primitive : mesh.primitives()) {
                GltfMaterialBinding binding = textures.resolve(primitive.materialName());
                if (!binding.visible()) {
                    continue;
                }
                applyMorphs(primitive, pose, morphOffset);
                if (skin != null && primitive.isSkinned()) {
                    skin(primitive, skin.jointCount());
                } else {
                    transformStatic(primitive);
                }
                VertexConsumer consumer = buffers.getBuffer(binding.layer().renderType(binding.texture()));
                int light = binding.layer().fullBright() ? LightTexture.FULL_BRIGHT : packedLight;
                emit(primitive, binding, consumer, poseMatrix, normalMatrix, light, packedOverlay, multiply(binding.color(), tint));
            }
        }
    }

    private void applyMorphs(GltfPrimitive primitive, GltfPose pose, int morphOffset) {
        srcPositions = primitive.positions();
        srcNormals = primitive.normals();
        if (morphOffset < 0 || !primitive.hasMorphTargets()) {
            return;
        }
        int targets = primitive.morphTargets().size();
        boolean any = false;
        for (int t = 0; t < targets && !any; t++) {
            any = pose.morphWeight(morphOffset + t) != 0f;
        }
        if (!any) {
            return;
        }
        int length = primitive.vertexCount() * 3;
        if (morphPositions.length < length) {
            morphPositions = new float[length];
            morphNormals = new float[length];
        }
        System.arraycopy(srcPositions, 0, morphPositions, 0, length);
        System.arraycopy(srcNormals, 0, morphNormals, 0, length);
        for (int t = 0; t < targets; t++) {
            primitive.morphTargets().get(t).apply(pose.morphWeight(morphOffset + t), morphPositions, morphNormals);
        }
        srcPositions = morphPositions;
        srcNormals = morphNormals;
    }

    private void skin(GltfPrimitive primitive, int jointCount) {
        int n = primitive.vertexCount();
        ensureVertices(n);
        float[] src = srcPositions;
        float[] srcN = srcNormals;
        int[] ji = primitive.joints();
        float[] jw = primitive.weights();
        float[] m = joints;
        for (int v = 0; v < n; v++) {
            int p = v * 3;
            float x = src[p];
            float y = src[p + 1];
            float z = src[p + 2];
            float nx = srcN[p];
            float ny = srcN[p + 1];
            float nz = srcN[p + 2];
            float ox = 0f, oy = 0f, oz = 0f, onx = 0f, ony = 0f, onz = 0f;
            float total = 0f;
            for (int k = 0; k < GltfPrimitive.MAX_INFLUENCES; k++) {
                float w = jw[v * 4 + k];
                int joint = ji[v * 4 + k];
                if (w <= 0f || joint < 0 || joint >= jointCount) {
                    continue;
                }
                int b = joint * 16;
                ox += w * (m[b] * x + m[b + 4] * y + m[b + 8] * z + m[b + 12]);
                oy += w * (m[b + 1] * x + m[b + 5] * y + m[b + 9] * z + m[b + 13]);
                oz += w * (m[b + 2] * x + m[b + 6] * y + m[b + 10] * z + m[b + 14]);
                onx += w * (m[b] * nx + m[b + 4] * ny + m[b + 8] * nz);
                ony += w * (m[b + 1] * nx + m[b + 5] * ny + m[b + 9] * nz);
                onz += w * (m[b + 2] * nx + m[b + 6] * ny + m[b + 10] * nz);
                total += w;
            }
            if (total <= 0f) {
                ox = x;
                oy = y;
                oz = z;
                onx = nx;
                ony = ny;
                onz = nz;
            }
            positions[p] = ox;
            positions[p + 1] = oy;
            positions[p + 2] = oz;
            normals[p] = onx;
            normals[p + 1] = ony;
            normals[p + 2] = onz;
        }
    }

    private void transformStatic(GltfPrimitive primitive) {
        int n = primitive.vertexCount();
        ensureVertices(n);
        float[] src = srcPositions;
        float[] srcN = srcNormals;
        float[] m = nodeMatrix;
        for (int v = 0; v < n; v++) {
            int p = v * 3;
            float x = src[p];
            float y = src[p + 1];
            float z = src[p + 2];
            positions[p] = m[0] * x + m[4] * y + m[8] * z + m[12];
            positions[p + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
            positions[p + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
            float nx = srcN[p];
            float ny = srcN[p + 1];
            float nz = srcN[p + 2];
            normals[p] = m[0] * nx + m[4] * ny + m[8] * nz;
            normals[p + 1] = m[1] * nx + m[5] * ny + m[9] * nz;
            normals[p + 2] = m[2] * nx + m[6] * ny + m[10] * nz;
        }
    }

    private void emit(GltfPrimitive primitive, GltfMaterialBinding binding, VertexConsumer consumer, Matrix4f pm,
                      Matrix3f nm, int light, int overlay, int color) {
        int[] indices = primitive.indices();
        float[] uvs = primitive.uvs();
        for (int t = 0; t < indices.length; t += 3) {
            vertex(consumer, indices[t], uvs, binding, pm, nm, light, overlay, color);
            vertex(consumer, indices[t + 1], uvs, binding, pm, nm, light, overlay, color);
            vertex(consumer, indices[t + 2], uvs, binding, pm, nm, light, overlay, color);
            vertex(consumer, indices[t + 2], uvs, binding, pm, nm, light, overlay, color);
        }
    }

    private void vertex(VertexConsumer consumer, int index, float[] uvs, GltfMaterialBinding binding, Matrix4f pm,
                        Matrix3f nm, int light, int overlay, int color) {
        int p = index * 3;
        float x = positions[p];
        float y = positions[p + 1];
        float z = positions[p + 2];
        float wx = pm.m00() * x + pm.m10() * y + pm.m20() * z + pm.m30();
        float wy = pm.m01() * x + pm.m11() * y + pm.m21() * z + pm.m31();
        float wz = pm.m02() * x + pm.m12() * y + pm.m22() * z + pm.m32();
        float nx = normals[p];
        float ny = normals[p + 1];
        float nz = normals[p + 2];
        float tx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
        float ty = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
        float tz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
        float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len > 1.0e-8f) {
            tx /= len;
            ty /= len;
            tz /= len;
        } else {
            ty = 1f;
        }
        consumer.addVertex(wx, wy, wz, color,
                binding.u(uvs[index * 2]), binding.v(uvs[index * 2 + 1]),
                overlay, light, tx, ty, tz);
    }

    private static int multiply(int a, int b) {
        int alpha = ((a >>> 24) * (b >>> 24)) / 255;
        int red = (((a >> 16) & 0xFF) * ((b >> 16) & 0xFF)) / 255;
        int green = (((a >> 8) & 0xFF) * ((b >> 8) & 0xFF)) / 255;
        int blue = ((a & 0xFF) * (b & 0xFF)) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private void ensureWorld(int count) {
        if (world.length < count) {
            Matrix4f[] grown = new Matrix4f[count];
            System.arraycopy(world, 0, grown, 0, world.length);
            for (int i = world.length; i < count; i++) {
                grown[i] = new Matrix4f();
            }
            world = grown;
        }
    }

    private void ensureJoints(int count) {
        if (joints.length < count * 16) {
            joints = new float[count * 16];
        }
    }

    private void ensureVertices(int count) {
        if (positions.length < count * 3) {
            positions = new float[count * 3];
            normals = new float[count * 3];
        }
    }
}
