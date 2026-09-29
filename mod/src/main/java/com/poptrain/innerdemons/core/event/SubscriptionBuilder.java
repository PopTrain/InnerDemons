package com.poptrain.innerdemons.core.event;

import java.util.Objects;
import java.util.function.Predicate;

import com.poptrain.innerdemons.core.condition.Condition;

public final class SubscriptionBuilder<E> {

    private final EventBus bus;
    private final Class<E> type;
    private EventPriority priority = EventPriority.NORMAL;
    private boolean receiveCancelled;
    private Condition<? super E> filter;
    private boolean once;
    private String label;
    private SubscriptionGroup group;

    SubscriptionBuilder(EventBus bus, Class<E> type) {
        this.bus = bus;
        this.type = type;
    }

    public SubscriptionBuilder<E> priority(EventPriority value) {
        this.priority = Objects.requireNonNull(value, "priority");
        return this;
    }

    public SubscriptionBuilder<E> receiveCancelled() {
        this.receiveCancelled = true;
        return this;
    }

    public SubscriptionBuilder<E> filter(Condition<? super E> condition) {
        Objects.requireNonNull(condition, "condition");
        this.filter = filter == null ? condition : Condition.<E>allOf(filter, condition);
        return this;
    }

    public SubscriptionBuilder<E> filter(String name, Predicate<? super E> test) {
        return filter(Condition.of(name, test));
    }

    public SubscriptionBuilder<E> once() {
        this.once = true;
        return this;
    }

    public SubscriptionBuilder<E> named(String value) {
        this.label = Objects.requireNonNull(value, "name");
        return this;
    }

    public SubscriptionBuilder<E> in(SubscriptionGroup value) {
        this.group = Objects.requireNonNull(value, "group");
        return this;
    }

    public Subscription subscribe(EventListener<? super E> listener) {
        Objects.requireNonNull(listener, "listener");
        Subscription subscription = bus.register(type, listener, priority, receiveCancelled, filter, once, label);
        return group != null ? group.add(subscription) : subscription;
    }
}
