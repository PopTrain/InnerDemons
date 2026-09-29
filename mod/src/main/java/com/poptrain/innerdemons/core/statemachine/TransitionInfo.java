package com.poptrain.innerdemons.core.statemachine;

import java.util.Optional;

public record TransitionInfo<S>(S from, S to, Object event, TransitionCause cause, String transitionName) {

    public boolean hasEvent() {
        return event != null;
    }

    public <E> Optional<E> eventAs(Class<E> type) {
        return type.isInstance(event) ? Optional.of(type.cast(event)) : Optional.empty();
    }

    public boolean isRestore() {
        return cause == TransitionCause.RESTORE;
    }
}
