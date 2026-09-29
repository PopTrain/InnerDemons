package com.poptrain.innerdemons.core.random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

public final class WeightedTable<T> {

    private static final WeightedTable<?> EMPTY = new WeightedTable<>(List.of());

    private final List<Weighted<T>> entries;
    private final double[] cumulative;
    private final double total;

    private WeightedTable(List<Weighted<T>> entries) {
        List<Weighted<T>> positive = new ArrayList<>(entries.size());
        for (Weighted<T> entry : entries) {
            if (entry.weight() > 0) {
                positive.add(entry);
            }
        }
        this.entries = List.copyOf(positive);
        this.cumulative = new double[this.entries.size()];
        double running = 0;
        for (int i = 0; i < cumulative.length; i++) {
            running += this.entries.get(i).weight();
            cumulative[i] = running;
        }
        this.total = running;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @SuppressWarnings("unchecked")
    public static <T> WeightedTable<T> empty() {
        return (WeightedTable<T>) EMPTY;
    }

    public static <T> WeightedTable<T> of(List<Weighted<T>> entries) {
        return new WeightedTable<>(List.copyOf(entries));
    }

    public static <T> WeightedTable<T> of(Collection<? extends T> values, ToDoubleFunction<? super T> weight) {
        Objects.requireNonNull(weight, "weight");
        Builder<T> builder = builder();
        for (T value : values) {
            builder.add(value, weight.applyAsDouble(value));
        }
        return builder.build();
    }

    public static <T> WeightedTable<T> uniform(Collection<? extends T> values) {
        return of(values, v -> 1.0);
    }

    public T pick(Rng rng) {
        return roll(rng).orElseThrow(() -> new IllegalStateException("cannot pick from an empty weighted table"));
    }

    public Optional<T> roll(Rng rng) {
        Objects.requireNonNull(rng, "rng");
        double draw = rng.nextDouble();
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        int index = Arrays.binarySearch(cumulative, draw * total);
        index = index >= 0 ? index + 1 : -index - 1;
        return Optional.of(entries.get(Math.min(index, entries.size() - 1)).value());
    }

    public List<Weighted<T>> entries() {
        return entries;
    }

    public double totalWeight() {
        return total;
    }

    public double probabilityOf(T value) {
        if (total <= 0) {
            return 0;
        }
        double sum = 0;
        for (Weighted<T> entry : entries) {
            if (entry.value().equals(value)) {
                sum += entry.weight();
            }
        }
        return sum / total;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    @Override
    public String toString() {
        return "WeightedTable" + entries;
    }

    public static final class Builder<T> {

        private final List<Weighted<T>> entries = new ArrayList<>();

        private Builder() {
        }

        public Builder<T> add(T value, double weight) {
            entries.add(Weighted.of(value, weight));
            return this;
        }

        public Builder<T> addAll(Collection<? extends T> values, double weight) {
            for (T value : values) {
                add(value, weight);
            }
            return this;
        }

        public WeightedTable<T> build() {
            return new WeightedTable<>(entries);
        }
    }
}
