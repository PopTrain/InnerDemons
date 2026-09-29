package com.poptrain.innerdemons.core.data;

import java.util.Optional;

public record DataChangedEvent<T>(DataContainer container, DataKey<T> key, Kind kind, T oldValue, T newValue) {

    public static final Class<DataChangedEvent<?>> TYPE = wildcardType();

    public enum Kind {
        SET,
        REMOVED,
        ATTACHED,
        DETACHED,
        COPIED
    }

    public boolean is(DataKey<?> other) {
        return key == other;
    }

    @SuppressWarnings("unchecked")
    public <U> Optional<DataChangedEvent<U>> as(DataKey<U> other) {
        return key == other ? Optional.of((DataChangedEvent<U>) this) : Optional.empty();
    }

    public Optional<T> previous() {
        return Optional.ofNullable(oldValue);
    }

    public Optional<T> current() {
        return Optional.ofNullable(newValue);
    }

    public boolean isRemoval() {
        return newValue == null;
    }

    public Optional<Object> owner() {
        return container.owner();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<DataChangedEvent<?>> wildcardType() {
        return (Class) DataChangedEvent.class;
    }
}
