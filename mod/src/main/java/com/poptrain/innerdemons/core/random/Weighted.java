package com.poptrain.innerdemons.core.random;

import java.util.List;
import java.util.Objects;

public record Weighted<T>(T value, double weight) {

    public Weighted {
        Objects.requireNonNull(value, "value");
        checkWeight(weight, value);
    }

    public static <T> Weighted<T> of(T value, double weight) {
        return new Weighted<>(value, weight);
    }

    static void checkWeight(double weight, Object value) {
        if (!Double.isFinite(weight) || weight < 0) {
            throw new IllegalArgumentException("weight for " + value + " must be finite and >= 0 but was " + weight);
        }
    }

    static <T> int select(List<Weighted<T>> entries, double draw) {
        double total = 0;
        for (Weighted<T> entry : entries) {
            total += entry.weight;
        }
        if (total <= 0) {
            return -1;
        }
        double target = draw * total;
        double running = 0;
        int lastPositive = -1;
        for (int i = 0; i < entries.size(); i++) {
            double w = entries.get(i).weight;
            if (w <= 0) {
                continue;
            }
            lastPositive = i;
            running += w;
            if (target < running) {
                return i;
            }
        }
        return lastPositive;
    }
}
