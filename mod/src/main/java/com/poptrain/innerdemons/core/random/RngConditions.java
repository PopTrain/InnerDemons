package com.poptrain.innerdemons.core.random;

import java.util.Objects;
import java.util.function.Function;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.event.EventBus;

public final class RngConditions {

    private RngConditions() {
    }

    public static <T> Condition<T> chance(Rng rng, double probability) {
        Objects.requireNonNull(rng, "rng");
        return chance(t -> rng, probability);
    }

    public static <T> Condition<T> chance(Function<? super T, ? extends Rng> rng, double probability) {
        Objects.requireNonNull(rng, "rng");
        return Condition.of("chance" + probability, t -> rng.apply(t).chance(probability));
    }

    public static <T> Condition<T> oneIn(Rng rng, int n) {
        Objects.requireNonNull(rng, "rng");
        return oneIn(t -> rng, n);
    }

    public static <T> Condition<T> oneIn(Function<? super T, ? extends Rng> rng, int n) {
        Objects.requireNonNull(rng, "rng");
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive: " + n);
        }
        return Condition.of("chance1/" + n, t -> rng.apply(t).oneIn(n));
    }

    public static <C> Condition<C> roll(Function<? super C, ? extends Rng> rng, RollKey<C> key, double baseChance) {
        return roll(rng, null, key, baseChance);
    }

    public static <C> Condition<C> roll(Function<? super C, ? extends Rng> rng, EventBus bus, RollKey<C> key,
                                        double baseChance) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(key, "key");
        return Condition.of("roll:" + key.name(), c -> Rolls.chance(rng.apply(c), bus, key, c, baseChance).success());
    }
}
