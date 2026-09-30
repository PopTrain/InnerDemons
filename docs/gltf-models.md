# glTF Models

Packages:

- `com.poptrain.innerdemons.core.gltf`: parser, data model, poses, animation sampling. Uses only JOML and Gson, both of which ship with Minecraft. Safe to load on a dedicated server.
- `com.poptrain.innerdemons.client.gltf`: resource loading, texture bindings, CPU skinning renderer, entity renderer base. Client only.

## Where files go

```
assets/innerdemons/models/gltf/demons/imp.glb            -> model id innerdemons:demons/imp
assets/innerdemons/models/gltf/demons/imp.gltf (+ .bin)  -> same id, text form
assets/innerdemons/textures/entity/imp/body.png
assets/innerdemons/textures/entity/imp/body_shiny.png
assets/innerdemons/textures/entity/imp/eyes.png
assets/innerdemons/textures/entity/imp/mouth.png
```

File names must be valid resource paths: lowercase letters, digits, `_ - . /`. Minecraft skips anything else. Models reload with F3+T. In game, `/gltf list` and `/gltf info <id>` show what loaded, the material names and the animation names with their lengths.

## Blender export settings

File > Export > glTF 2.0:

- Format: **glTF Binary (.glb)**.
- Include: selected objects or visible objects, as you like.
- Transform: **+Y Up** on (the default).
- Mesh: UVs and Normals on. Leave **Draco compression off**. Compressed files are rejected with a clear error.
- Materials: set to **Placeholder** or **Export**. Images aren't used, so don't embed them. Only the material *names* matter (see below).
- Animation: **Actions** mode, and **Always Sample Animations** on. Each action becomes one named animation. Names are case sensitive, so name the actions exactly as the code asks for them (`idle`, `walk`, `attack`, ...).
- Skinning on. Up to 4 bone influences per vertex (`JOINTS_0`/`WEIGHTS_0`). Blender's exporter limits to 4 by default.
- Shape keys: Mesh > **Shape Keys** on (Shape Key Normals too, for correct lighting). Animation > **Shape Keys Animations** on. See [Shape keys](#shape-keys) below.

Scale: 1 Blender unit = 1 block. The model's front should face Blender's **-Y** (the Front view looks at it). That becomes glTF +Z, which the renderer turns to face the entity's look direction.

## Textures and materials

Textures are never read from the model. Each glTF **material name** is mapped to a PNG in code with a `GltfTextureSet`. Any material without a mapping uses the fallback texture.

For a demon with a body plus separate eyes and mouth, give those faces their own materials in Blender (for example `body`, `eyes`, `mouth`). Then:

```java
static ResourceLocation tex(String path) {
    return ResourceLocation.fromNamespaceAndPath("innerdemons", "textures/entity/" + path + ".png");
}

static final GltfTextureSet NORMAL = GltfTextureSet.builder(tex("imp/body"))
        .material("eyes", tex("imp/eyes"))
        .material("mouth", tex("imp/mouth"))
        .build();

static final GltfTextureSet SHINY = NORMAL.retextured(Map.of(
        tex("imp/body"), tex("imp/body_shiny"),
        tex("imp/eyes"), tex("imp/eyes_shiny")));
```

`retextured` swaps texture paths and keeps every other setting. That way a shiny is one line per texture that changes.

### 2D face rigs (sprite sheets)

Put all expressions for a face part in one sheet: a grid of equal frames. In Blender, unwrap the eye (or mouth) plane so it fills the **whole 0-1 UV square**. Then pick a frame at render time:

```java
textures.withFrame("eyes", 4, 2, eyeFrame)
        .withFrame("mouth", 4, 1, mouthFrame);
```

The arguments are columns, rows and frame index. A 4×2 sheet has frames 0 to 7, numbered left to right, then top to bottom.

The renderer remaps that material's UVs into the chosen cell. You can also swap whole files per expression with `with("eyes", binding.withTexture(...))` if you'd rather not use sheets.

### Per-material options

`GltfMaterialBinding` also carries:

- `layer`: `CUTOUT` (default, no back-face culling), `CUTOUT_CULLED`, `TRANSLUCENT`, or `EMISSIVE` (full-bright glow, like spider eyes). Glowing eyes: `GltfMaterialBinding.of(tex("imp/eyes"), GltfRenderLayer.EMISSIVE)`.
- `color`: ARGB tint for that material.
- `visible`: hide a material, e.g. an alternate horn set. `builder.hide("horns_alt")`.

## Entity renderer

```java
public final class ImpRenderer extends GltfEntityRenderer<ImpEntity> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("innerdemons", "demons/imp");

    public ImpRenderer(EntityRendererProvider.Context context) {
        super(context, MODEL, 0.5f);
    }

    @Override
    protected GltfTextureSet textures(ImpEntity imp, float partialTick) {
        GltfTextureSet base = imp.isShiny() ? SHINY : NORMAL;
        return base.withFrame("eyes", 4, 2, imp.eyeFrame())
                   .withFrame("mouth", 4, 1, imp.mouthFrame());
    }

    @Override
    protected void animate(ImpEntity imp, GltfModel model, GltfAnimationController anim, float partialTick) {
        if (imp.isAttacking()) {
            anim.play("attack", GltfAnimationController.Playback.HOLD_LAST_FRAME, 0.1f, 1f);
        } else if (imp.walkAnimation.isMoving()) {
            anim.play("walk");
        } else {
            anim.play("idle");
        }
    }

    @Override
    protected void adjustPose(ImpEntity imp, GltfModel model, GltfPose pose, float partialTick) {
        int head = model.nodeIndex("head");
        if (head >= 0) {
            float yaw = Mth.rotLerp(partialTick, imp.yHeadRotO, imp.yHeadRot) - Mth.rotLerp(partialTick, imp.yBodyRotO, imp.yBodyRot);
            pose.rotateLocal(head, new Quaternionf().rotationY(-yaw * Mth.DEG_TO_RAD));
        }
    }
}
```

Register it in `EntityRenderersEvent.RegisterRenderers`:

```java
event.registerEntityRenderer(ModEntities.IMP.get(), ImpRenderer::new);
```

Other hooks you can override: `modelScale`, `applyTransforms` (default: face body yaw, tip over on death), `tint`, `overlay` (default: red hurt flash), `animationTime`, and `modelId(entity)` to pick a different model per entity (e.g. evolutions).

## Animation controller

`GltfAnimationController` is made per entity by the renderer. Its behavior:

- `play(name)` loops with a 0.2 s crossfade. Calling it again with the animation already playing does nothing, so calling it every frame from `animate` is fine.
- `play(name, Playback.HOLD_LAST_FRAME, fade, speed)` plays once and freezes on the last frame. `isFinished(model)` then returns true.
- `restart(...)` plays from the start even if that animation is already playing (e.g. a second attack).
- `stop(fade)` fades back to the rest pose.
- An unknown animation name leaves the model in its rest pose rather than crashing.

### Driving animations from the brain

Demon behavior runs on the server state machine, and the client never has a brain. To animate from it, mirror the machine's state into a synced `DataKey` with `StateSync` (see [network.md](network.md)), then read that key in `animate`. This replaces the older `SynchedEntityData` approach.

Declare the mirror key and add it to the demon's `SyncRegistry`:

```java
public static final DataKey<DemonState> ANIM_STATE = StateSync.stateKey("anim_state", DemonState.class);

public static final SyncRegistry SYNC = SyncRegistry.builder("demon")
        .key(ANIM_STATE, StateSync.stateSerializer(DemonState.class))
        .build();
```

Start the mirror on the server, in the brain's constructor:

```java
public DemonBrain(DemonEntity demon) {
    super(GRAPH, demon);
    if (!demon.level().isClientSide()) {
        StateSync.mirror(this, demon.data(), DemonData.ANIM_STATE);
    }
}
```

Then map the synced state to animation names on the client:

```java
@Override
protected void animate(ImpEntity imp, GltfModel model, GltfAnimationController anim, float partialTick) {
    DemonState state = imp.get(DemonData.ANIM_STATE);
    if (state == DemonState.ATTACK) {
        anim.play("attack", GltfAnimationController.Playback.HOLD_LAST_FRAME, 0.1f, 1f);
    } else if (imp.walkAnimation.isMoving()) {
        anim.play("walk");
    } else {
        anim.play("idle");
    }
}
```

- `mirror` writes the deepest active state on every transition, including the restore after a load. Use `mirrorPath` if the renderer also needs the parent states (for example, `COMBAT` as well as `STRIKE`). Use `mirrorAs(machine, data, key, mapping)` to sync something else, such as an animation name.
- Keep the mirror key transient, which `StateSync.stateKey` does for you. The machine saves itself, and the mirror is rebuilt on restore.
- To react once per change instead of polling every frame, listen on the client: `demon.data().listen(DemonData.ANIM_STATE, e -> ...)`.
- A synced value only sends when it changes. If the brain leaves and re-enters the same state between two server ticks, the client doesn't see it, and a `HOLD_LAST_FRAME` animation stays frozen. For one-shot moments that can repeat back to back, such as a second attack, send a message (see [network.md](network.md)) and call `restart(...)` when it arrives.

## Shape keys

Blender shape keys are imported as glTF morph targets, keeping the shape key names. `/gltf info <id>` lists them. They're applied before skinning, like in Blender, so a shape key on a mesh that bones also move stays correct.

### Animating them from Blender

Shape key animation is stored on the mesh's shape key data, not on the armature, so Blender keeps it in its own action. There are two ways to get it into the game:

1. **As part of a body animation.** Blender's exporter merges animations into one glTF animation by **NLA track name**. Push the armature action and the shape key action down to NLA tracks with the same name (e.g. both `attack`). Then `play("attack")` drives bones and shape keys together.
2. **As its own animation, played on top (recommended for faces).** Export the shape key action by itself (e.g. `talk`, `blink`, `angry`). Play it as an **overlay** so it runs alongside whatever the body is doing:

```java
@Override
protected void animate(ImpEntity imp, GltfModel model, GltfAnimationController anim, float partialTick) {
    anim.play(imp.walkAnimation.isMoving() ? "walk" : "idle");
    if (imp.isTalking()) {
        anim.playOverlay("face", "talk");
    } else {
        anim.stopOverlay("face", 0.15f);
    }
}
```

Overlays only change the bones and shape keys their animation actually keys. Everything else comes from the body animation. Each overlay slot (`"face"`, `"tail"`, ...) holds one animation at a time and crossfades when you switch it. The overlay methods mirror the body ones: `playOverlay`, `restartOverlay`, `stopOverlay`, `overlayAnimation`, `isOverlayFinished`.

If shape keys don't show up in the export, check that **Apply Modifiers** is off, or that the mesh has no modifiers other than Armature. Blender can't export shape keys through most modifiers.

### Setting them from code

Shape keys can be set directly in `adjustPose`, which runs after animations. This is useful for things driven by gameplay, like mood or health:

```java
@Override
protected void adjustPose(ImpEntity imp, GltfModel model, GltfPose pose, float partialTick) {
    pose.setMorphWeight(model, "Angry", imp.getAnger());
    pose.addMorphWeight(model, "Breathe", Mth.sin(imp.tickCount * 0.1f) * 0.2f);
}
```

A name that appears on several meshes (e.g. `Angry` on both body and horns) sets all of them. `pose.morphWeight(model, name)` reads a value back. `setNodeMorphWeight(model, node, index, weight)` targets a single mesh node by index. Weights outside 0-1 are allowed and exaggerate or invert the shape, like in Blender.

Shape keys and the texture-based face frames work together. For example, eye textures can be swapped for expressions while a shape key squashes the eyelid for a blink.

## Drawing a model outside an entity

For GUIs, block entities or battle screens, use the pieces directly:

```java
GltfModel model = GltfModelManager.INSTANCE.getOrNull(id);
GltfPose pose = new GltfPose(model);
controller.setTime(seconds);
controller.apply(model, pose);
renderer.render(model, pose, textures, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
```

Keep one `GltfModelRenderer` per caller on the render thread. It reuses internal buffers and is not thread safe.

## Supported / not supported

Supported: `.glb`, `.gltf` with external `.bin` or base64 data URIs, skinning (4 influences), shape keys / morph targets (position and normal, animated or set from code), node hierarchies, multiple meshes, primitives and materials, LINEAR/STEP/CUBICSPLINE keyframes, sparse accessors, quantized attributes (`KHR_mesh_quantization`), triangle strips and fans, and missing normals (computed).

Not supported: morph target tangents (not needed without normal maps), Draco and meshopt compression, a second set of joints (`JOINTS_1`, more than 4 influences), cameras, lights, textures inside the model file.

Skinning runs on the CPU each frame. That's fine for low-poly demons (a few thousand triangles). If a scene fills up with many high-poly models, the next step would be caching the skinned mesh per tick instead of per frame.