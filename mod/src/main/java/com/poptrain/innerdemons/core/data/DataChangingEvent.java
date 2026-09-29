package com.poptrain.innerdemons.core.data;

import com.poptrain.innerdemons.core.event.CancellableEvent;

import java.util.Objects;
import java.util.Optional;

public final class DataChangingEvent<T> extends CancellableEvent {

    public static final Class<DataChangingEvent<?>> TYPE = wildcardType();

    private final DataContainer container;
    private final DataKey<T> key;
    private final T oldValue;
    private final T proposedValue;
    private T newValue;

    DataChangingEvent(DataContainer container, DataKey<T> key, T oldValue, T newValue) {
        this.container = container;
        this.key = key;
        this.oldValue = oldValue;
        this.proposedValue = newValue;
        this.newValue = newValue;
    }

    public DataContainer container() {
        return container;
    }

    public DataKey<T> key() {
        return key;
    }

    public T oldValue() {
        return oldValue;
    }

    public T proposedValue() {
        return proposedValue;
    }

    public T newValue() {
        return newValue;
    }

    public boolean isRemoval() {
        return proposedValue == null;
    }

    public void setNewValue(T value) {
        if (proposedValue == null) {
            throw new IllegalStateException("A removal of '" + key.name() + "' can only be cancelled, not changed");
        }
        this.newValue = key.cast(Objects.requireNonNull(value, "value"));
    }

    public boolean modified() {
        return !Objects.equals(proposedValue, newValue);
    }

    public Optional<Object> owner() {
        return container.owner();
    }

    public boolean is(DataKey<?> other) {
        return key == other;
    }

    @SuppressWarnings("unchecked")
    public <U> Optional<DataChangingEvent<U>> as(DataKey<U> other) {
        return key == other ? Optional.of((DataChangingEvent<U>) this) : Optional.empty();
    }

    @Override
    public String toString() {
        return "DataChangingEvent[" + container.name() + "." + key.name() + ": " + oldValue + " -> " + newValue
                + (isCancelled() ? ", cancelled" : "") + "]";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<DataChangingEvent<?>> wildcardType() {
        return (Class) DataChangingEvent.class;
    }
}
