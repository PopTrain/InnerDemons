package com.poptrain.innerdemons.species;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record Behavior(Flavor flavorPreference, ResourceLocation favoriteHabitat, ResourceLocation socialTendency) {

    public static final Codec<Behavior> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Flavor.CODEC.fieldOf("flavor_preference").forGetter(Behavior::flavorPreference),
            DemonCodecs.ID.fieldOf("favorite_habitat").forGetter(Behavior::favoriteHabitat),
            DemonCodecs.ID.fieldOf("social_tendency").forGetter(Behavior::socialTendency)
    ).apply(instance, Behavior::new));
}
