package com.poptrain.innerdemons.core.statemachine;

import java.util.Objects;

public final class StateDataKey<T> {

    private final String name;
    private final Class<T> type;

    private StateDataKey(String name, Class<T> type) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> StateDataKey<T> of(String name, Class<T> type) {
        return new StateDataKey<>(name, type);
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return "StateDataKey[" + name + ":" + type.getSimpleName() + "]";
    }
}
