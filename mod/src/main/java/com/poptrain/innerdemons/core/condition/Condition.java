package com.poptrain.innerdemons.core.condition;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Condition<T> {

    ConditionResult evaluate(T context);

    String describe();

    default boolean test(T context) {
        return evaluate(context).passed();
    }

    default Condition<T> and(Condition<? super T> other) {
        return CompositeConditions.<T>all(List.<Condition<? super T>>of(this, Objects.requireNonNull(other, "other")));
    }

    default Condition<T> or(Condition<? super T> other) {
        return CompositeConditions.<T>any(List.<Condition<? super T>>of(this, Objects.requireNonNull(other, "other")));
    }

    default Condition<T> negate() {
        return new CompositeConditions.Not<>(this);
    }

    default Condition<T> named(String name) {
        return new CompositeConditions.Named<>(Objects.requireNonNull(name, "name"), this);
    }

    default <U> Condition<U> adapt(Function<? super U, ? extends T> mapper) {
        return new CompositeConditions.Adapted<>(this, Objects.requireNonNull(mapper, "mapper"));
    }

    default Predicate<T> asPredicate() {
        return this::test;
    }

    static <T> Condition<T> of(String name, Predicate<? super T> test) {
        return new CompositeConditions.Simple<>(Objects.requireNonNull(name, "name"), Objects.requireNonNull(test, "test"));
    }

    static <T> Condition<T> always() {
        return CompositeConditions.always();
    }

    static <T> Condition<T> never() {
        return CompositeConditions.never();
    }

    static <T> Condition<T> not(Condition<T> condition) {
        return Objects.requireNonNull(condition, "condition").negate();
    }

    @SafeVarargs
    static <T> Condition<T> allOf(Condition<? super T>... conditions) {
        return CompositeConditions.<T>all(List.of(conditions));
    }

    static <T> Condition<T> allOf(List<? extends Condition<? super T>> conditions) {
        return CompositeConditions.all(conditions);
    }

    @SafeVarargs
    static <T> Condition<T> anyOf(Condition<? super T>... conditions) {
        return CompositeConditions.<T>any(List.of(conditions));
    }

    static <T> Condition<T> anyOf(List<? extends Condition<? super T>> conditions) {
        return CompositeConditions.any(conditions);
    }

    @SafeVarargs
    static <T> Condition<T> noneOf(Condition<? super T>... conditions) {
        return CompositeConditions.<T>any(List.of(conditions)).negate();
    }

    @SafeVarargs
    static <T> Condition<T> atLeast(int required, Condition<? super T>... conditions) {
        return CompositeConditions.<T>atLeast(required, List.of(conditions));
    }

    static <T> Condition<T> atLeast(int required, List<? extends Condition<? super T>> conditions) {
        return CompositeConditions.atLeast(required, conditions);
    }
}
