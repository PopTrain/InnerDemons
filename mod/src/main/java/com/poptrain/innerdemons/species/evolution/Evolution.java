package com.poptrain.innerdemons.species.evolution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.poptrain.innerdemons.species.DemonCodecs;
import com.poptrain.innerdemons.species.DemonRegistries;
import com.poptrain.innerdemons.species.DemonSpecies;

import net.minecraft.resources.ResourceKey;

public record Evolution(EvolutionCondition condition, ResourceKey<DemonSpecies> target) {

    public static final Codec<Evolution> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EvolutionCondition.MAP_CODEC.forGetter(Evolution::condition),
            DemonCodecs.key(DemonRegistries.SPECIES).fieldOf("target").forGetter(Evolution::target)
    ).apply(instance, Evolution::new));
}
