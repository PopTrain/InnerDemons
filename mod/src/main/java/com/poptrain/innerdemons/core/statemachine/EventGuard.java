package com.poptrain.innerdemons.core.statemachine;

@FunctionalInterface
public interface EventGuard<C, S, E> {

    boolean test(StateContext<C, S> source, E event);
}
