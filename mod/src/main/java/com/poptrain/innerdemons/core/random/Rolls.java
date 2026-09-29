package com.poptrain.innerdemons.core.random;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.poptrain.innerdemons.core.event.EventBus;

public final class Rolls {

    private Rolls() {
    }

    public static <C> ChanceResult chance(Rng rng, RollKey<C> key, C context, double baseChance) {
        return chance(rng, null, key, context, baseChance);
    }

    public static <C> ChanceResult chance(Rng rng, EventBus bus, RollKey<C> key, C context, double baseChance) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(key, "key");
        if (Double.isNaN(baseChance)) {
            throw new IllegalArgumentException("baseChance must not be NaN for " + key.name());
        }
        double chance = ChanceRollEvent.clamp(baseChance);
        Boolean forced = null;
        if (live(bus) && bus.hasListeners(ChanceRollEvent.class)) {
            ChanceRollEvent<C> event = bus.post(new ChanceRollEvent<>(key, context, baseChance));
            chance = event.effectiveChance();
            forced = event.forced().orElse(null);
        }
        double roll = rng.nextDouble();
        boolean success = forced != null ? forced : roll < chance;
        ChanceResult result = new ChanceResult(success, baseChance, chance, roll, forced != null);
        if (live(bus) && bus.hasListeners(ChanceRolledEvent.class)) {
            bus.post(new ChanceRolledEvent<>(key, context, result));
        }
        return result;
    }

    public static <C> ChanceResult chance(Rng rng, EventBus bus, RollKey<C> key, C context, PityCounter pity) {
        Objects.requireNonNull(pity, "pity");
        ChanceResult result = chance(rng, bus, key, context, pity.currentChance());
        pity.record(result.success());
        return result;
    }

    public static <C, T> Optional<T> table(Rng rng, TableKey<C, T> key, RollTable<? super C, T> table, C context) {
        return table(rng, null, key, table, context);
    }

    public static <C, T> Optional<T> table(Rng rng, EventBus bus, TableKey<C, T> key, RollTable<? super C, T> table,
                                           C context) {
        Objects.requireNonNull(table, "table");
        return weighted(rng, bus, key, table.candidates(context), context);
    }

    public static <C, T> Optional<T> table(Rng rng, EventBus bus, TableKey<C, T> key, WeightedTable<T> table,
                                           C context) {
        Objects.requireNonNull(table, "table");
        return weighted(rng, bus, key, table.entries(), context);
    }

    private static <C, T> Optional<T> weighted(Rng rng, EventBus bus, TableKey<C, T> key, List<Weighted<T>> candidates,
                                               C context) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(key, "key");
        List<Weighted<T>> weights = candidates;
        T forced = null;
        if (live(bus) && bus.hasListeners(TableRollEvent.class)) {
            TableRollEvent<C, T> event = bus.post(new TableRollEvent<>(key, context, candidates));
            weights = event.snapshot();
            forced = event.forced().orElse(null);
        }
        double roll = rng.nextDouble();
        Optional<T> result;
        if (forced != null) {
            result = Optional.of(forced);
        } else {
            int index = Weighted.select(weights, roll);
            result = index < 0 ? Optional.empty() : Optional.of(weights.get(index).value());
        }
        if (live(bus) && bus.hasListeners(TableRolledEvent.class)) {
            bus.post(new TableRolledEvent<>(key, context, weights, result, roll, forced != null));
        }
        return result;
    }

    public static <C> RollSubscriptionBuilder<C, ChanceRollEvent<C>> onChance(EventBus bus, RollKey<C> key) {
        Objects.requireNonNull(key, "key");
        return new RollSubscriptionBuilder<>(bus, ChanceRollEvent.class, key.name(),
                e -> ((ChanceRollEvent<?>) e).is(key), ChanceRollEvent::context);
    }

    public static <C> RollSubscriptionBuilder<C, ChanceRolledEvent<C>> onChanceRolled(EventBus bus, RollKey<C> key) {
        Objects.requireNonNull(key, "key");
        return new RollSubscriptionBuilder<>(bus, ChanceRolledEvent.class, key.name(),
                e -> ((ChanceRolledEvent<?>) e).is(key), ChanceRolledEvent::context);
    }

    public static <C, T> RollSubscriptionBuilder<C, TableRollEvent<C, T>> onTable(EventBus bus, TableKey<C, T> key) {
        Objects.requireNonNull(key, "key");
        return new RollSubscriptionBuilder<>(bus, TableRollEvent.class, key.name(),
                e -> ((TableRollEvent<?, ?>) e).is(key), TableRollEvent::context);
    }

    public static <C, T> RollSubscriptionBuilder<C, TableRolledEvent<C, T>> onTableRolled(EventBus bus,
                                                                                         TableKey<C, T> key) {
        Objects.requireNonNull(key, "key");
        return new RollSubscriptionBuilder<>(bus, TableRolledEvent.class, key.name(),
                e -> ((TableRolledEvent<?, ?>) e).is(key), TableRolledEvent::context);
    }

    private static boolean live(EventBus bus) {
        return bus != null && !bus.isClosed();
    }
}
