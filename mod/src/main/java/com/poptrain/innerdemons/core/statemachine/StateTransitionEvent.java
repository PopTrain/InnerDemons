package com.poptrain.innerdemons.core.statemachine;

import java.util.List;
import java.util.Optional;

public record StateTransitionEvent<C, S>(StateMachine<C, S> machine, TransitionInfo<S> info, List<S> exited, List<S> entered) {

    public static final Class<StateTransitionEvent<?, ?>> TYPE = wildcardType();

    public StateTransitionEvent {
        exited = List.copyOf(exited);
        entered = List.copyOf(entered);
    }

    public C owner() {
        return machine.owner();
    }

    public StateGraph<C, S> graph() {
        return machine.graph();
    }

    public S from() {
        return info.from();
    }

    public S to() {
        return info.to();
    }

    public TransitionCause cause() {
        return info.cause();
    }

    public boolean isFrom(StateGraph<?, ?> graph) {
        return machine.graph() == graph;
    }

    public boolean didEnter(Object state) {
        return entered.contains(state);
    }

    public boolean didExit(Object state) {
        return exited.contains(state);
    }

    @SuppressWarnings("unchecked")
    public <C2, S2> Optional<StateTransitionEvent<C2, S2>> as(StateGraph<C2, S2> graph) {
        return isFrom(graph) ? Optional.of((StateTransitionEvent<C2, S2>) (StateTransitionEvent<?, ?>) this) : Optional.empty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<StateTransitionEvent<?, ?>> wildcardType() {
        return (Class) StateTransitionEvent.class;
    }
}
