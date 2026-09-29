package com.poptrain.innerdemons.core.random;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

public interface Rng {

    long nextLong();

    Rng split();

    static SeededRng seeded(long seed) {
        return SeededRng.of(seed);
    }

    static SeededRng seeded(String label) {
        return SeededRng.of(Seeds.of(label));
    }

    static SeededRng unseeded() {
        return SeededRng.of(Seeds.random());
    }

    default int nextInt() {
        return (int) (nextLong() >>> 32);
    }

    default int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        int r = nextInt() >>> 1;
        int m = bound - 1;
        if ((bound & m) == 0) {
            return (int) ((bound * (long) r) >> 31);
        }
        for (int u = r; u - (r = u % bound) + m < 0; u = nextInt() >>> 1) {
        }
        return r;
    }

    default long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        long m = bound - 1;
        if ((bound & m) == 0) {
            return nextLong() & m;
        }
        long bits;
        long value;
        do {
            bits = nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + m < 0);
        return value;
    }

    default int range(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException("max " + maxInclusive + " < min " + minInclusive);
        }
        return (int) (minInclusive + nextLong((long) maxInclusive - minInclusive + 1));
    }

    default double range(double minInclusive, double maxExclusive) {
        if (!(maxExclusive >= minInclusive)) {
            throw new IllegalArgumentException("max " + maxExclusive + " < min " + minInclusive);
        }
        return minInclusive + nextDouble() * (maxExclusive - minInclusive);
    }

    default double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    default float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    default boolean nextBoolean() {
        return nextLong() < 0;
    }

    default double nextGaussian() {
        double u1 = 1.0 - nextDouble();
        double u2 = nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    default double gaussian(double mean, double deviation) {
        return mean + nextGaussian() * deviation;
    }

    default double triangle(double center, double deviation) {
        return center + deviation * (nextDouble() - nextDouble());
    }

    default boolean chance(double probability) {
        double roll = nextDouble();
        if (Double.isNaN(probability)) {
            return false;
        }
        return roll < probability;
    }

    default boolean oneIn(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive: " + n);
        }
        return nextInt(n) == 0;
    }

    default <T> T pick(List<? extends T> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("cannot pick from an empty list");
        }
        return values.get(nextInt(values.size()));
    }

    @SuppressWarnings("unchecked")
    default <T> T pick(T... values) {
        return pick(List.of(values));
    }

    default <T> T pickWeighted(Collection<? extends T> values, ToDoubleFunction<? super T> weight) {
        return WeightedTable.<T>of(values, weight).pick(this);
    }

    default <T> void shuffle(List<T> values) {
        Objects.requireNonNull(values, "values");
        for (int i = values.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }

    default void skip(int count) {
        for (int i = 0; i < count; i++) {
            nextLong();
        }
    }
}
