package com.poptrain.innerdemons.core.statemachine;

@FunctionalInterface
public interface EventHandler<C, S, E> {

    boolean handle(StateContext<C, S> context, E event);
}
