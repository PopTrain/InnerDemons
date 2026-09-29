package com.poptrain.innerdemons.species.evolution;

import com.mojang.serialization.MapCodec;

public interface EvolutionCondition {

    MapCodec<EvolutionCondition> MAP_CODEC = EvolutionMethods.CODEC.<EvolutionCondition>dispatchMap(
            "method", EvolutionCondition::method, EvolutionMethod::codec);

    EvolutionMethod<?> method();
}
