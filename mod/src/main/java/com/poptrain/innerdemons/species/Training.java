package com.poptrain.innerdemons.species;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

public record Training(int tpYield, int catchRate, int baseFriendship, int baseExp, GrowthRate growthRate) {

    public static final int MAX_CATCH_RATE = 255;
    public static final int MAX_FRIENDSHIP = 255;

    public static final Codec<Training> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("tp_yield").forGetter(Training::tpYield),
            Codec.intRange(0, MAX_CATCH_RATE).fieldOf("catch_rate").forGetter(Training::catchRate),
            Codec.intRange(0, MAX_FRIENDSHIP).fieldOf("base_friendship").forGetter(Training::baseFriendship),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("base_exp").forGetter(Training::baseExp),
            GrowthRate.CODEC.fieldOf("growth_rate").forGetter(Training::growthRate)
    ).apply(instance, Training::new));

    public boolean isCatchable() {
        return catchRate > 0;
    }
}
