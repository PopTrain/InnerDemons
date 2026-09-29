package com.poptrain.innerdemons.client.gltf;

import com.example.innerdemons.InnerDemons;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.poptrain.innerdemons.core.gltf.GltfAnimation;
import com.poptrain.innerdemons.core.gltf.GltfModel;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = InnerDemons.MODID, value = Dist.CLIENT)
public final class GltfClientEvents {

    private GltfClientEvents() {
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(GltfModelManager.INSTANCE);
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gltf")
                .then(Commands.literal("list").executes(ctx -> {
                    var ids = GltfModelManager.INSTANCE.ids();
                    ctx.getSource().sendSuccess(() -> Component.literal(ids.size() + " glTF model(s) loaded"), false);
                    ids.stream().sorted().forEach(id -> ctx.getSource().sendSuccess(() -> Component.literal(" - " + id), false));
                    return ids.size();
                }))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.greedyString()).executes(ctx -> {
                            ResourceLocation id = ResourceLocation.tryParse(StringArgumentType.getString(ctx, "id"));
                            GltfModel model = id == null ? null : GltfModelManager.INSTANCE.getOrNull(id);
                            if (model == null) {
                                ctx.getSource().sendFailure(Component.literal("No glTF model loaded with id " + id));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(model.toString()), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Materials: " + model.materialNames()), false);
                            for (GltfAnimation animation : model.animations()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(String.format(" - %s (%.2fs)", animation.name(), animation.duration())), false);
                            }
                            return 1;
                        }))));
    }
}
