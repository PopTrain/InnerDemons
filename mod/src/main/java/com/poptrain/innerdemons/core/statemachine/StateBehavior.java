package com.poptrain.innerdemons.core.statemachine;

public interface StateBehavior<C, S> {

    default void onEnter(StateContext<C, S> context, TransitionInfo<S> info) {
    }

    default void onExit(StateContext<C, S> context, TransitionInfo<S> info) {
    }

    default void onTick(StateContext<C, S> context) {
    }

    default boolean onEvent(StateContext<C, S> context, Object event) {
        return false;
    }

    default boolean canEnter(C owner, TransitionInfo<S> info) {
        return true;
    }

    default boolean canExit(StateContext<C, S> context, TransitionInfo<S> info) {
        return true;
    }
}
