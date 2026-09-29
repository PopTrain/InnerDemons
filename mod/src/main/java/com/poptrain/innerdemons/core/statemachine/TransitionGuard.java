package com.poptrain.innerdemons.core.statemachine;

@FunctionalInterface
public interface TransitionGuard<C, S> {

    boolean test(StateContext<C, S> source, TransitionInfo<S> info);
}
