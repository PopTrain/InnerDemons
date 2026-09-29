package com.poptrain.innerdemons.core.statemachine;

@FunctionalInterface
public interface TransitionAction<C, S> {

    void run(C owner, TransitionInfo<S> info);

    default TransitionAction<C, S> andThen(TransitionAction<C, S> next) {
        return (owner, info) -> {
            run(owner, info);
            next.run(owner, info);
        };
    }
}
