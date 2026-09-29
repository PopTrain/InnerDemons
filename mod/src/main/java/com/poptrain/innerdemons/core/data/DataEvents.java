package com.poptrain.innerdemons.core.data;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.event.EventBus;
import com.poptrain.innerdemons.core.event.EventListener;
import com.poptrain.innerdemons.core.event.EventPriority;
import com.poptrain.innerdemons.core.event.Subscription;

import java.util.Objects;

public final class DataEvents {

    private DataEvents() {
    }

    public static <T> Subscription onChanged(EventBus bus, DataKey<T> key, EventListener<? super DataChangedEvent<T>> listener) {
        return onChanged(bus, key, EventPriority.NORMAL, listener);
    }

    public static <T> Subscription onChanged(EventBus bus, DataKey<T> key, EventPriority priority,
                                             EventListener<? super DataChangedEvent<T>> listener) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(listener, "listener");
        return bus.listen(DataChangedEvent.TYPE)
                .priority(priority)
                .filter(changedKey(key))
                .named("dataChanged:" + key.name())
                .subscribe(e -> e.as(key).ifPresent(listener::onEvent));
    }

    public static <T> Subscription onChanging(EventBus bus, DataKey<T> key, EventListener<? super DataChangingEvent<T>> listener) {
        return onChanging(bus, key, EventPriority.NORMAL, listener);
    }

    public static <T> Subscription onChanging(EventBus bus, DataKey<T> key, EventPriority priority,
                                              EventListener<? super DataChangingEvent<T>> listener) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(listener, "listener");
        return bus.listen(DataChangingEvent.TYPE)
                .priority(priority)
                .filter(changingKey(key))
                .named("dataChanging:" + key.name())
                .subscribe(e -> e.as(key).ifPresent(listener::onEvent));
    }

    public static <H, T> Subscription onAttached(EventBus bus, AttachmentKey<H, T> key, EventListener<? super DataChangedEvent<T>> listener) {
        Objects.requireNonNull(listener, "listener");
        return onChanged(bus, key, EventPriority.NORMAL, e -> {
            if (e.kind() == DataChangedEvent.Kind.ATTACHED || e.kind() == DataChangedEvent.Kind.COPIED) {
                listener.onEvent(e);
            }
        });
    }

    public static <H, T> Subscription onDetached(EventBus bus, AttachmentKey<H, T> key, EventListener<? super DataChangedEvent<T>> listener) {
        Objects.requireNonNull(listener, "listener");
        return onChanged(bus, key, EventPriority.NORMAL, e -> {
            if (e.kind() == DataChangedEvent.Kind.DETACHED) {
                listener.onEvent(e);
            }
        });
    }

    public static Condition<DataChangedEvent<?>> changedKey(DataKey<?> key) {
        Objects.requireNonNull(key, "key");
        return Condition.of("key:" + key.name(), e -> e.key() == key);
    }

    public static Condition<DataChangingEvent<?>> changingKey(DataKey<?> key) {
        Objects.requireNonNull(key, "key");
        return Condition.of("key:" + key.name(), e -> e.key() == key);
    }

    public static Condition<DataChangedEvent<?>> from(DataContainer container) {
        Objects.requireNonNull(container, "container");
        return Condition.of("from:" + container.name(), e -> e.container() == container);
    }

    public static Condition<DataChangedEvent<?>> ownedBy(Object owner) {
        Objects.requireNonNull(owner, "owner");
        return Condition.of("ownedBy:" + owner, e -> e.container().owner().orElse(null) == owner);
    }
}
