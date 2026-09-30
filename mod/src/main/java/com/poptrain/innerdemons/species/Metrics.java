package com.poptrain.innerdemons.species;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

public record Metrics(float height, float weight) {

    public static final Codec<Metrics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("height").forGetter(Metrics::height),
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("weight").forGetter(Metrics::weight)
    ).apply(instance, Metrics::new));
}
