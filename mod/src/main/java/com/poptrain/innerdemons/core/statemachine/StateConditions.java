package com.poptrain.innerdemons.core.statemachine;

import java.util.Objects;
import java.util.function.Predicate;

import com.poptrain.innerdemons.core.condition.Condition;

public final class StateConditions {

    private StateConditions() {
    }

    public static <C, S> Condition<StateContext<C, S>> owner(Condition<? super C> condition) {
        Objects.requireNonNull(condition, "condition");
        return condition.adapt((StateContext<C, S> ctx) -> ctx.owner());
    }

    public static <C, S> Condition<StateContext<C, S>> ticksAtLeast(int ticks) {
        return Condition.of("ticksInState>=" + ticks, ctx -> ctx.ticksInState() >= ticks);
    }

    public static <C, S> Condition<StateContext<C, S>> ticksBelow(int ticks) {
        return Condition.of("ticksInState<" + ticks, ctx -> ctx.ticksInState() < ticks);
    }

    public static <C, S> Condition<StateContext<C, S>> machineIn(S state) {
        Objects.requireNonNull(state, "state");
        return Condition.of("in:" + state, ctx -> ctx.machine().isIn(state));
    }

    public static <C, S> Condition<StateContext<C, S>> has(StateDataKey<?> key) {
        Objects.requireNonNull(key, "key");
        return Condition.of("has:" + key.name(), ctx -> ctx.has(key));
    }

    public static <C, S, T> Condition<StateContext<C, S>> data(StateDataKey<T> key, String name, Predicate<? super T> test) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(test, "test");
        return Condition.of(name, ctx -> {
            T value = ctx.get(key);
            return value != null && test.test(value);
        });
    }
}
