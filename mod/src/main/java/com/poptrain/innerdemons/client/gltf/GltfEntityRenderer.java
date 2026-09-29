package com.poptrain.innerdemons.client.gltf;

import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.poptrain.innerdemons.core.gltf.GltfAnimationController;
import com.poptrain.innerdemons.core.gltf.GltfModel;
import com.poptrain.innerdemons.core.gltf.GltfPose;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public abstract class GltfEntityRenderer<T extends Entity> extends EntityRenderer<T> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DEFAULT_IDLE_ANIMATION = "idle";

    private final ResourceLocation modelId;
    private final GltfModelRenderer modelRenderer = new GltfModelRenderer();
    private final Map<T, AnimationState> states = new WeakHashMap<>();
    private boolean warnedMissing;

    protected GltfEntityRenderer(EntityRendererProvider.Context context, ResourceLocation modelId, float shadowRadius) {
        super(context);
        this.modelId = modelId;
        this.shadowRadius = shadowRadius;
    }

    public ResourceLocation modelId() {
        return modelId;
    }

    protected abstract GltfTextureSet textures(T entity, float partialTick);

    protected ResourceLocation modelId(T entity) {
        return modelId;
    }

    protected void animate(T entity, GltfModel model, GltfAnimationController controller, float partialTick) {
        if (controller.currentAnimation() == null && model.hasAnimation(DEFAULT_IDLE_ANIMATION)) {
            controller.play(DEFAULT_IDLE_ANIMATION);
        }
    }

    protected void adjustPose(T entity, GltfModel model, GltfPose pose, float partialTick) {
    }

    protected float modelScale(T entity) {
        return 1f;
    }

    protected float bodyYaw(T entity, float entityYaw, float partialTick) {
        if (entity instanceof LivingEntity living) {
            return Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        }
        return entityYaw;
    }

    protected void applyTransforms(T entity, PoseStack poseStack, float entityYaw, float partialTick) {
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw(entity, entityYaw, partialTick)));
        if (entity instanceof LivingEntity living && living.deathTime > 0) {
            float progress = Math.min(1f, Mth.sqrt((living.deathTime + partialTick - 1f) / 20f * 1.6f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(progress * 90f));
        }
        float scale = modelScale(entity);
        if (scale != 1f) {
            poseStack.scale(scale, scale, scale);
        }
    }

    protected int overlay(T entity, float partialTick) {
        if (entity instanceof LivingEntity living) {
            return LivingEntityRenderer.getOverlayCoords(living, 0f);
        }
        return OverlayTexture.NO_OVERLAY;
    }

    protected int tint(T entity, float partialTick) {
        return GltfMaterialBinding.WHITE;
    }

    protected float animationTime(T entity, float partialTick) {
        return (entity.tickCount + partialTick) / 20f;
    }

    public GltfAnimationController controller(T entity) {
        return states.computeIfAbsent(entity, e -> new AnimationState()).controller;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        ResourceLocation id = modelId(entity);
        GltfModel model = GltfModelManager.INSTANCE.getOrNull(id);
        if (model == null) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOGGER.warn("glTF model {} is not loaded; expected assets/{}/{}/{}.glb", id, id.getNamespace(), GltfModelManager.DIRECTORY, id.getPath());
            }
        } else if (!entity.isInvisible()) {
            AnimationState state = states.computeIfAbsent(entity, e -> new AnimationState());
            if (state.pose == null || !state.pose.fits(model)) {
                state.pose = new GltfPose(model);
            }
            state.controller.setTime(animationTime(entity, partialTick));
            animate(entity, model, state.controller, partialTick);
            state.controller.apply(model, state.pose);
            adjustPose(entity, model, state.pose, partialTick);

            poseStack.pushPose();
            applyTransforms(entity, poseStack, entityYaw, partialTick);
            modelRenderer.render(model, state.pose, textures(entity, partialTick), poseStack, buffers,
                    packedLight, overlay(entity, partialTick), tint(entity, partialTick));
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return textures(entity, 1f).fallback().texture();
    }

    private static final class AnimationState {
        final GltfAnimationController controller = new GltfAnimationController();
        GltfPose pose;
    }
}
