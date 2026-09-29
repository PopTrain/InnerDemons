package com.poptrain.innerdemons.client.gltf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.poptrain.innerdemons.core.gltf.GltfBufferResolver;
import com.poptrain.innerdemons.core.gltf.GltfException;
import com.poptrain.innerdemons.core.gltf.GltfModel;
import com.poptrain.innerdemons.core.gltf.GltfParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class GltfModelManager extends SimplePreparableReloadListener<Map<ResourceLocation, GltfModel>> {

    public static final String DIRECTORY = "models/gltf";
    public static final GltfModelManager INSTANCE = new GltfModelManager();

    private static final Logger LOGGER = LogUtils.getLogger();

    private volatile Map<ResourceLocation, GltfModel> models = Map.of();
    private final AtomicInteger generation = new AtomicInteger();

    private GltfModelManager() {
    }

    public Optional<GltfModel> get(ResourceLocation id) {
        return Optional.ofNullable(models.get(id));
    }

    public GltfModel getOrNull(ResourceLocation id) {
        return models.get(id);
    }

    public Set<ResourceLocation> ids() {
        return models.keySet();
    }

    public int generation() {
        return generation.get();
    }

    public static ResourceLocation fileToId(ResourceLocation file) {
        String path = file.getPath();
        String trimmed = path.substring(DIRECTORY.length() + 1);
        int dot = trimmed.lastIndexOf('.');
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), dot < 0 ? trimmed : trimmed.substring(0, dot));
    }

    public static ResourceLocation idToFile(ResourceLocation id, boolean binary) {
        return id.withPath(DIRECTORY + "/" + id.getPath() + (binary ? ".glb" : ".gltf"));
    }

    @Override
    protected Map<ResourceLocation, GltfModel> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Resource> found = resourceManager.listResources(DIRECTORY,
                rl -> rl.getPath().endsWith(".glb") || rl.getPath().endsWith(".gltf"));
        Map<ResourceLocation, GltfModel> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation file = entry.getKey();
            ResourceLocation id = fileToId(file);
            try (InputStream in = entry.getValue().open()) {
                GltfModel model = GltfParser.parse(id.toString(), in, resolverFor(resourceManager, file));
                GltfModel previous = loaded.put(id, model);
                if (previous != null) {
                    LOGGER.warn("glTF model {} exists as both .glb and .gltf; using {}", id, file);
                }
                LOGGER.debug("Loaded glTF model {}", model);
            } catch (IOException | GltfException e) {
                LOGGER.error("Failed to load glTF model {}: {}", file, e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.error("Unexpected error loading glTF model {}", file, e);
            }
        }
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, GltfModel> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        models = Collections.unmodifiableMap(prepared);
        generation.incrementAndGet();
        LOGGER.info("Loaded {} glTF model(s)", prepared.size());
    }

    private static GltfBufferResolver resolverFor(ResourceManager resourceManager, ResourceLocation file) {
        String path = file.getPath();
        String directory = path.substring(0, path.lastIndexOf('/') + 1);
        return uri -> {
            ResourceLocation target = ResourceLocation.tryBuild(file.getNamespace(), directory + uri);
            if (target == null) {
                throw new IOException("'" + uri + "' is not a valid resource path (use lowercase letters, digits, _ - . /)");
            }
            Resource resource = resourceManager.getResource(target)
                    .orElseThrow(() -> new IOException("Missing " + target));
            try (InputStream in = resource.open()) {
                return in.readAllBytes();
            }
        };
    }
}
