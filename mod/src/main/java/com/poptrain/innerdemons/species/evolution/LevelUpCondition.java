package com.poptrain.innerdemons.species.evolution;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

public record LevelUpCondition(int level) implements EvolutionCondition {

    public static final MapCodec<LevelUpCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("level").forGetter(LevelUpCondition::level)
    ).apply(instance, LevelUpCondition::new));

    @Override
    public EvolutionMethod<LevelUpCondition> method() {
        return EvolutionMethods.LEVEL_UP.get();
    }
}
