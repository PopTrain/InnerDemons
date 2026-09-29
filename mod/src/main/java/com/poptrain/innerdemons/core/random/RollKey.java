package com.poptrain.innerdemons.core.random;

import java.util.Objects;

public final class RollKey<C> {

    private final String name;
    private final Class<C> contextType;

    private RollKey(String name, Class<C> contextType) {
        this.name = Objects.requireNonNull(name, "name");
        this.contextType = Objects.requireNonNull(contextType, "contextType");
    }

    public static <C> RollKey<C> of(String name, Class<C> contextType) {
        return new RollKey<>(name, contextType);
    }

    public String name() {
        return name;
    }

    public Class<C> contextType() {
        return contextType;
    }

    @Override
    public String toString() {
        return "RollKey[" + name + ":" + contextType.getSimpleName() + "]";
    }
}
