package com.poptrain.innerdemons.core.random;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

import com.poptrain.innerdemons.core.condition.Condition;

public final class RollTable<C, T> {

    private final String name;
    private final List<Entry<C, T>> entries;

    private RollTable(String name, List<Entry<C, T>> entries) {
        this.name = name;
        this.entries = List.copyOf(entries);
    }

    public static <C, T> Builder<C, T> builder(String name) {
        return new Builder<>(name);
    }

    public static <C, T> Builder<C, T> builder(String name, Class<C> contextType, Class<T> valueType) {
        return new Builder<>(name);
    }

    public String name() {
        return name;
    }

    public List<Entry<C, T>> entries() {
        return entries;
    }

    public List<Weighted<T>> candidates(C context) {
        List<Weighted<T>> result = new ArrayList<>(entries.size());
        for (Entry<C, T> entry : entries) {
            if (!entry.condition.test(context)) {
                continue;
            }
            double weight = entry.weight.applyAsDouble(context);
            if (Double.isFinite(weight) && weight > 0) {
                result.add(new Weighted<>(entry.value, weight));
            }
        }
        return result;
    }

    public WeightedTable<T> resolve(C context) {
        return WeightedTable.of(candidates(context));
    }

    public Optional<T> roll(Rng rng, C context) {
        Objects.requireNonNull(rng, "rng");
        List<Weighted<T>> candidates = candidates(context);
        int index = Weighted.select(candidates, rng.nextDouble());
        return index < 0 ? Optional.empty() : Optional.of(candidates.get(index).value());
    }

    public T pick(Rng rng, C context) {
        return roll(rng, context).orElseThrow(() ->
                new IllegalStateException("roll table " + name + " has no available entries for " + context));
    }

    @Override
    public String toString() {
        return "RollTable[" + name + ", " + entries.size() + " entries]";
    }

    public record Entry<C, T>(T value, ToDoubleFunction<? super C> weight, Condition<? super C> condition) {

        public Entry {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(weight, "weight");
            Objects.requireNonNull(condition, "condition");
        }
    }

    public static final class Builder<C, T> {

        private final String name;
        private final List<Entry<C, T>> entries = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder<C, T> add(T value, double weight) {
            return add(value, weight, Condition.always());
        }

        public Builder<C, T> add(T value, double weight, Condition<? super C> condition) {
            Weighted.checkWeight(weight, value);
            return add(value, ctx -> weight, condition);
        }

        public Builder<C, T> add(T value, ToDoubleFunction<? super C> weight) {
            return add(value, weight, Condition.always());
        }

        public Builder<C, T> add(T value, ToDoubleFunction<? super C> weight, Condition<? super C> condition) {
            entries.add(new Entry<>(value, weight, condition));
            return this;
        }

        public RollTable<C, T> build() {
            return new RollTable<>(name, entries);
        }
    }
}
