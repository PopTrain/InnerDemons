package com.poptrain.innerdemons.species.evolution;

import com.mojang.serialization.Codec;
import com.poptrain.innerdemons.species.DemonCodecs;
import com.poptrain.innerdemons.species.DemonRegistries;

import net.minecraft.core.Registry;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EvolutionMethods {

    public static final DeferredRegister<EvolutionMethod<?>> METHODS =
            DeferredRegister.create(DemonRegistries.EVOLUTION_METHOD, DemonCodecs.NAMESPACE);

    public static final Registry<EvolutionMethod<?>> REGISTRY = METHODS.makeRegistry(builder -> builder.sync(true));

    public static final Codec<EvolutionMethod<?>> CODEC = DemonCodecs.registryEntry(REGISTRY);

    public static final DeferredHolder<EvolutionMethod<?>, EvolutionMethod<LevelUpCondition>> LEVEL_UP =
            METHODS.register("level_up", () -> new EvolutionMethod<>(LevelUpCondition.MAP_CODEC));

    public static final DeferredHolder<EvolutionMethod<?>, EvolutionMethod<LowFriendshipCondition>> LOW_FRIENDSHIP =
            METHODS.register("low_friendship", () -> new EvolutionMethod<>(LowFriendshipCondition.MAP_CODEC));

    private EvolutionMethods() {
    }
}
