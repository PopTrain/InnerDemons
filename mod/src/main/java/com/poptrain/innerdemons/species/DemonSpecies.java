package com.poptrain.innerdemons.species;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.poptrain.innerdemons.species.evolution.Evolution;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

public record DemonSpecies(
        List<ResourceLocation> types,
        Rank rank,
        BaseStats baseStats,
        Training training,
        GenderRatio genderRatio,
        Behavior behavior,
        List<Evolution> evolutions,
        Movepool movepool) {

    public static final Codec<DemonSpecies> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.nonEmptyList(DemonCodecs.ID.listOf()).fieldOf("types").forGetter(DemonSpecies::types),
            Rank.CODEC.fieldOf("rank").forGetter(DemonSpecies::rank),
            BaseStats.CODEC.fieldOf("base_stats").forGetter(DemonSpecies::baseStats),
            Training.CODEC.fieldOf("training").forGetter(DemonSpecies::training),
            GenderRatio.CODEC.optionalFieldOf("gender_ratio", GenderRatio.GENDERLESS).forGetter(DemonSpecies::genderRatio),
            Behavior.CODEC.fieldOf("behavior").forGetter(DemonSpecies::behavior),
            Evolution.CODEC.listOf().optionalFieldOf("evolutions", List.of()).forGetter(DemonSpecies::evolutions),
            Movepool.CODEC.optionalFieldOf("movepool", Movepool.EMPTY).forGetter(DemonSpecies::movepool)
    ).apply(instance, DemonSpecies::new));

    public DemonSpecies {
        types = List.copyOf(types);
        evolutions = List.copyOf(evolutions);
    }

    public boolean hasType(ResourceLocation type) {
        return types.contains(type);
    }

    public static String translationKey(ResourceLocation id) {
        return "demon." + id.getNamespace() + "." + id.getPath();
    }
}
