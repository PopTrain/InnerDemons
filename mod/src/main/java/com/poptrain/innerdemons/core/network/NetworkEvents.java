package com.poptrain.innerdemons.core.network;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.data.DataContainer;
import com.poptrain.innerdemons.core.data.DataKey;
import com.poptrain.innerdemons.core.event.EventBus;
import com.poptrain.innerdemons.core.event.EventListener;
import com.poptrain.innerdemons.core.event.EventPriority;
import com.poptrain.innerdemons.core.event.Subscription;
import com.poptrain.innerdemons.core.statemachine.StateMachine;

public final class NetworkEvents {

    private NetworkEvents() {
    }

    public static <M> Subscription onReceived(EventBus bus, MessageType<M> type,
            EventListener<? super MessageReceivedEvent<M>> listener) {
        return onReceived(bus, type, EventPriority.NORMAL, listener);
    }

    public static <M> Subscription onReceived(EventBus bus, MessageType<M> type, EventPriority priority,
            EventListener<? super MessageReceivedEvent<M>> listener) {
        return bus.listen(MessageReceivedEvent.TYPE)
                .priority(priority)
                .filter(received(type))
                .named("received:" + type.id())
                .subscribe(e -> e.as(type).ifPresent(listener::onEvent));
    }

    public static <M> Subscription forward(EventBus bus, MessageType<M> type, StateMachine<?, ?> machine) {
        return forward(bus, type, machine, Condition.always());
    }

    public static <M> Subscription forward(EventBus bus, MessageType<M> type, StateMachine<?, ?> machine,
            Condition<? super M> filter) {
        return bus.listen(MessageReceivedEvent.TYPE)
                .priority(EventPriority.LOW)
                .filter(received(type))
                .named("forward:" + type.id() + "->" + machine)
                .subscribe(e -> e.as(type)
                        .map(MessageReceivedEvent::message)
                        .filter(filter::test)
                        .ifPresent(machine::fire));
    }

    public static Subscription onSynced(EventBus bus, EventListener<? super DataSyncedEvent> listener) {
        return bus.listen(DataSyncedEvent.class).named("synced").subscribe(listener);
    }

    public static Subscription onSynced(EventBus bus, DataKey<?> key, EventListener<? super DataSyncedEvent> listener) {
        return bus.listen(DataSyncedEvent.class)
                .filter(touched(key))
                .named("synced:" + key.name())
                .subscribe(listener);
    }

    public static Condition<MessageReceivedEvent<?>> received(MessageType<?> type) {
        return Condition.of("message:" + type.id(), e -> e.is(type));
    }

    public static Condition<DataSyncedEvent> touched(DataKey<?> key) {
        return Condition.of("synced:" + key.name(), e -> e.touched(key));
    }

    public static Condition<DataSyncedEvent> syncedInto(DataContainer container) {
        return Condition.of("syncedInto:" + container.name(), e -> e.container() == container);
    }
}
