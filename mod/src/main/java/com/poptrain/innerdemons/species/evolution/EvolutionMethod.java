package com.poptrain.innerdemons.species.evolution;

import com.mojang.serialization.MapCodec;

public record EvolutionMethod<T extends EvolutionCondition>(MapCodec<T> codec) {
}
