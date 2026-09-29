package com.poptrain.innerdemons.core.statemachine;

public interface StateMachineListener<C, S> {

    default void onStateEntered(StateMachine<C, S> machine, S state, TransitionInfo<S> info) {
    }

    default void onStateExited(StateMachine<C, S> machine, S state, TransitionInfo<S> info) {
    }

    default void onTransition(StateMachine<C, S> machine, TransitionInfo<S> info) {
    }

    default void onTransitionRejected(StateMachine<C, S> machine, S from, S target, TransitionResult result, String detail) {
    }
}
