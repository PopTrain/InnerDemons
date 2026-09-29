package com.poptrain.innerdemons.core.random;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.event.EventBus;
import com.poptrain.innerdemons.core.event.EventListener;
import com.poptrain.innerdemons.core.event.EventPriority;
import com.poptrain.innerdemons.core.event.Subscription;
import com.poptrain.innerdemons.core.event.SubscriptionBuilder;
import com.poptrain.innerdemons.core.event.SubscriptionGroup;

public final class RollSubscriptionBuilder<C, E> {

    private final SubscriptionBuilder<Object> delegate;
    private final Function<Object, E> cast;
    private final Function<? super E, ? extends C> context;

    @SuppressWarnings("unchecked")
    RollSubscriptionBuilder(EventBus bus, Class<?> type, String keyName, Predicate<Object> matchesKey,
                            Function<? super E, ? extends C> context) {
        Objects.requireNonNull(bus, "bus");
        this.delegate = (SubscriptionBuilder<Object>) bus.listen(type);
        this.cast = event -> (E) event;
        this.context = context;
        delegate.named(type.getSimpleName() + ":" + keyName);
        delegate.filter(Condition.of("key:" + keyName, matchesKey));
    }

    public RollSubscriptionBuilder<C, E> priority(EventPriority value) {
        delegate.priority(value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public RollSubscriptionBuilder<C, E> when(Condition<? super C> condition) {
        Objects.requireNonNull(condition, "condition");
        Function<Object, C> contextOf = event -> context.apply(cast.apply(event));
        delegate.filter(((Condition<C>) condition).adapt(contextOf));
        return this;
    }

    public RollSubscriptionBuilder<C, E> when(String name, Predicate<? super C> test) {
        return when(Condition.of(name, test));
    }

    @SuppressWarnings("unchecked")
    public RollSubscriptionBuilder<C, E> filter(Condition<? super E> condition) {
        Objects.requireNonNull(condition, "condition");
        delegate.filter(((Condition<E>) condition).adapt(cast));
        return this;
    }

    public RollSubscriptionBuilder<C, E> filter(String name, Predicate<? super E> test) {
        return filter(Condition.of(name, test));
    }

    public RollSubscriptionBuilder<C, E> once() {
        delegate.once();
        return this;
    }

    public RollSubscriptionBuilder<C, E> named(String value) {
        delegate.named(value);
        return this;
    }

    public RollSubscriptionBuilder<C, E> in(SubscriptionGroup group) {
        delegate.in(group);
        return this;
    }

    public Subscription subscribe(EventListener<? super E> listener) {
        Objects.requireNonNull(listener, "listener");
        return delegate.subscribe(event -> listener.onEvent(cast.apply(event)));
    }
}
