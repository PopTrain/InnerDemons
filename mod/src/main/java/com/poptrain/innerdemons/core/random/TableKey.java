package com.poptrain.innerdemons.core.random;

import java.util.Objects;

public final class TableKey<C, T> {

    private final String name;
    private final Class<C> contextType;
    private final Class<T> valueType;

    private TableKey(String name, Class<C> contextType, Class<T> valueType) {
        this.name = Objects.requireNonNull(name, "name");
        this.contextType = Objects.requireNonNull(contextType, "contextType");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
    }

    public static <C, T> TableKey<C, T> of(String name, Class<C> contextType, Class<T> valueType) {
        return new TableKey<>(name, contextType, valueType);
    }

    public String name() {
        return name;
    }

    public Class<C> contextType() {
        return contextType;
    }

    public Class<T> valueType() {
        return valueType;
    }

    @Override
    public String toString() {
        return "TableKey[" + name + ":" + contextType.getSimpleName() + "->" + valueType.getSimpleName() + "]";
    }
}
