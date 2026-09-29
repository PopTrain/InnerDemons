package com.poptrain.innerdemons.core.data;

import com.poptrain.innerdemons.core.condition.Condition;

import java.util.Objects;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;

public final class DataConditions {

    private DataConditions() {
    }

    public static Condition<DataHolder> has(DataKey<?> key) {
        Objects.requireNonNull(key, "key");
        return Condition.of("has:" + key.name(), h -> h.data().has(key));
    }

    public static Condition<DataHolder> missing(DataKey<?> key) {
        Objects.requireNonNull(key, "key");
        return Condition.of("missing:" + key.name(), h -> !h.data().has(key));
    }

    public static <T> Condition<DataHolder> value(DataKey<T> key, String name, Predicate<? super T> predicate) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(predicate, "predicate");
        return Condition.of(key.name() + ":" + name, h -> {
            T value = peek(h, key);
            return value != null && predicate.test(value);
        });
    }

    public static <T> Condition<DataHolder> value(DataKey<T> key, Condition<? super T> condition) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(condition, "condition");
        Condition<DataHolder> present = Condition.of("present:" + key.name(), h -> peek(h, key) != null);
        Condition<DataHolder> lifted = condition.adapt(h -> peek(h, key));
        return Condition.<DataHolder>allOf(present, lifted).named(key.name());
    }

    public static <T> Condition<DataHolder> equalTo(DataKey<T> key, T expected) {
        return value(key, "=" + expected, v -> Objects.equals(v, expected));
    }

    public static Condition<DataHolder> isTrue(DataKey<Boolean> key) {
        return value(key, "true", Boolean::booleanValue);
    }

    public static Condition<DataHolder> atLeast(DataKey<? extends Number> key, double min) {
        return number(key, ">=" + trim(min), v -> v >= min);
    }

    public static Condition<DataHolder> atMost(DataKey<? extends Number> key, double max) {
        return number(key, "<=" + trim(max), v -> v <= max);
    }

    public static Condition<DataHolder> below(DataKey<? extends Number> key, double max) {
        return number(key, "<" + trim(max), v -> v < max);
    }

    public static Condition<DataHolder> between(DataKey<? extends Number> key, double min, double max) {
        return number(key, " in " + trim(min) + ".." + trim(max), v -> v >= min && v <= max);
    }

    public static Condition<DataHolder> dirty() {
        return Condition.of("dirty", h -> h.data().isDirty());
    }

    private static Condition<DataHolder> number(DataKey<? extends Number> key, String suffix, DoublePredicate test) {
        Objects.requireNonNull(key, "key");
        return Condition.of(key.name() + suffix, h -> {
            Number value = peek(h, key);
            return value != null && test.test(value.doubleValue());
        });
    }

    static <T> T peek(DataHolder holder, DataKey<T> key) {
        DataContainer data = holder.data();
        return data.find(key).orElseGet(() -> key.isAttachment() ? null : key.createDefault(data));
    }

    private static String trim(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
