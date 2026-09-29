package com.poptrain.innerdemons.core.statemachine;

public enum TransitionResult {
    TRANSITIONED,
    HANDLED,
    QUEUED,
    UNHANDLED,
    NO_SUCH_TRANSITION,
    GUARD_REJECTED,
    EXIT_BLOCKED,
    ENTRY_BLOCKED,
    STALE_REQUESTER,
    NOT_RUNNING;

    public boolean isSuccess() {
        return this == TRANSITIONED || this == HANDLED;
    }

    public boolean isAccepted() {
        return isSuccess() || this == QUEUED;
    }

    public boolean isRejection() {
        return this == NO_SUCH_TRANSITION
                || this == GUARD_REJECTED
                || this == EXIT_BLOCKED
                || this == ENTRY_BLOCKED
                || this == STALE_REQUESTER;
    }
}
