package com.poptrain.innerdemons.species.evolution;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.poptrain.innerdemons.species.Training;

public record LowFriendshipCondition(Optional<Integer> maxFriendship) implements EvolutionCondition {

    public static final MapCodec<LowFriendshipCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(0, Training.MAX_FRIENDSHIP).optionalFieldOf("max_friendship").forGetter(LowFriendshipCondition::maxFriendship)
    ).apply(instance, LowFriendshipCondition::new));

    public int maxFriendshipOr(int fallback) {
        return maxFriendship.orElse(fallback);
    }

    @Override
    public EvolutionMethod<LowFriendshipCondition> method() {
        return EvolutionMethods.LOW_FRIENDSHIP.get();
    }
}
