package com.poptrain.innerdemons.core.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class TableRollEvent<C, T> {

    public static final Class<TableRollEvent<?, ?>> TYPE = wildcardType();

    private final TableKey<C, T> key;
    private final C context;
    private final List<Weighted<T>> weights;
    private final List<Weighted<T>> view;
    private T forced;

    TableRollEvent(TableKey<C, T> key, C context, List<Weighted<T>> candidates) {
        this.key = Objects.requireNonNull(key, "key");
        this.context = context;
        this.weights = new ArrayList<>(candidates);
        this.view = Collections.unmodifiableList(weights);
    }

    public TableKey<C, T> key() {
        return key;
    }

    public C context() {
        return context;
    }

    public List<Weighted<T>> weights() {
        return view;
    }

    public double weightOf(T value) {
        double sum = 0;
        for (Weighted<T> entry : weights) {
            if (entry.value().equals(value)) {
                sum += entry.weight();
            }
        }
        return sum;
    }

    public double totalWeight() {
        double sum = 0;
        for (Weighted<T> entry : weights) {
            sum += entry.weight();
        }
        return sum;
    }

    public void add(T value, double weight) {
        weights.add(Weighted.of(value, weight));
    }

    public void setWeight(T value, double weight) {
        Objects.requireNonNull(value, "value");
        Weighted.checkWeight(weight, value);
        weights.removeIf(entry -> entry.value().equals(value));
        if (weight > 0) {
            weights.add(Weighted.of(value, weight));
        }
    }

    public void multiply(T value, double factor) {
        Objects.requireNonNull(value, "value");
        multiplyIf(value::equals, factor);
    }

    public void multiplyIf(Predicate<? super T> filter, double factor) {
        Objects.requireNonNull(filter, "filter");
        Weighted.checkWeight(factor, "factor");
        weights.replaceAll(entry -> filter.test(entry.value()) ? Weighted.of(entry.value(), entry.weight() * factor) : entry);
    }

    public void removeIf(Predicate<? super T> filter) {
        Objects.requireNonNull(filter, "filter");
        weights.removeIf(entry -> filter.test(entry.value()));
    }

    public void force(T value) {
        forced = Objects.requireNonNull(value, "value");
    }

    public void clearForce() {
        forced = null;
    }

    public Optional<T> forced() {
        return Optional.ofNullable(forced);
    }

    public boolean is(TableKey<?, ?> other) {
        return key == other;
    }

    @SuppressWarnings("unchecked")
    public <C2, T2> Optional<TableRollEvent<C2, T2>> as(TableKey<C2, T2> other) {
        return is(other) ? Optional.of((TableRollEvent<C2, T2>) (TableRollEvent<?, ?>) this) : Optional.empty();
    }

    List<Weighted<T>> snapshot() {
        return List.copyOf(weights);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<TableRollEvent<?, ?>> wildcardType() {
        return (Class) TableRollEvent.class;
    }

    @Override
    public String toString() {
        return "TableRollEvent[" + key.name() + ", " + weights + (forced != null ? ", forced=" + forced : "") + "]";
    }
}
