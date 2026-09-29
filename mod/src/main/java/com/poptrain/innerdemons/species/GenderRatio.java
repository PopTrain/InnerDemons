package com.poptrain.innerdemons.species;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

public record GenderRatio(int male, int female) {

    public static final GenderRatio GENDERLESS = new GenderRatio(0, 0);

    public static final Codec<GenderRatio> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("male", 0).forGetter(GenderRatio::male),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("female", 0).forGetter(GenderRatio::female)
    ).apply(instance, GenderRatio::new));

    public boolean isGenderless() {
        return male + female == 0;
    }

    public double maleChance() {
        return isGenderless() ? 0.0 : (double) male / (male + female);
    }

    public double femaleChance() {
        return isGenderless() ? 0.0 : (double) female / (male + female);
    }
}
