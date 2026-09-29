package com.poptrain.innerdemons.core.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.event.EventBus;
import com.poptrain.innerdemons.core.event.EventListener;
import com.poptrain.innerdemons.core.event.EventPriority;
import com.poptrain.innerdemons.core.event.Subscription;
import com.poptrain.innerdemons.core.event.SubscriptionGroup;

public final class StateEvents {

    private StateEvents() {
    }

    public static <E> Subscription forward(EventBus bus, Class<E> type, StateMachine<?, ?> machine) {
        return forward(bus, type, machine, null);
    }

    public static <E> Subscription forward(EventBus bus, Class<E> type, StateMachine<?, ?> machine,
                                           Condition<? super E> filter) {
        Objects.requireNonNull(machine, "machine");
        var builder = bus.listen(type).priority(EventPriority.LOW).named(machine.graph().name() + " <- " + type.getSimpleName());
        if (filter != null) {
            builder.filter(filter);
        }
        return builder.subscribe(event -> {
            if (machine.isRunning()) {
                machine.fire(event);
            }
        });
    }

    public static <C, S> Subscription publish(StateMachine<C, S> machine, EventBus bus) {
        Objects.requireNonNull(bus, "bus");
        Publisher<C, S> publisher = new Publisher<>(bus);
        machine.addListener(publisher);
        return Subscription.of(() -> machine.removeListener(publisher));
    }

    public static <C, S> Subscription onTransition(EventBus bus, StateGraph<C, S> graph,
                                                   EventListener<? super StateTransitionEvent<C, S>> listener) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(listener, "listener");
        return bus.listen(StateTransitionEvent.TYPE)
                .named(graph.name() + " transitions")
                .subscribe(event -> event.as(graph).ifPresent(listener::onEvent));
    }

    public static <C, S> SubscriptionGroup connect(StateMachine<C, S> machine, EventBus bus, Class<?>... forwarded) {
        SubscriptionGroup group = new SubscriptionGroup();
        for (Class<?> type : forwarded) {
            group.add(forward(bus, type, machine));
        }
        group.add(publish(machine, bus));
        return group;
    }

    public static <C, S, E> Subscription subscribe(StateContext<C, S> context, EventBus bus, Class<E> type,
                                                   EventListener<? super E> listener) {
        Objects.requireNonNull(context, "context");
        return context.bind(bus.subscribe(type, listener));
    }

    public static <C, S, E> Subscription fireWhileActive(StateContext<C, S> context, EventBus bus, Class<E> type) {
        return fireWhileActive(context, bus, type, null);
    }

    public static <C, S, E> Subscription fireWhileActive(StateContext<C, S> context, EventBus bus, Class<E> type,
                                                         Condition<? super E> filter) {
        Objects.requireNonNull(context, "context");
        var builder = bus.listen(type).priority(EventPriority.LOW)
                .named(context.machine().graph().name() + ":" + context.state() + " <- " + type.getSimpleName());
        if (filter != null) {
            builder.filter(filter);
        }
        return context.bind(builder.subscribe(context::fire));
    }

    private static final class Publisher<C, S> implements StateMachineListener<C, S> {

        private final EventBus bus;
        private final List<S> exited = new ArrayList<>();
        private final List<S> entered = new ArrayList<>();

        private Publisher(EventBus bus) {
            this.bus = bus;
        }

        @Override
        public void onStateExited(StateMachine<C, S> machine, S state, TransitionInfo<S> info) {
            exited.add(state);
        }

        @Override
        public void onStateEntered(StateMachine<C, S> machine, S state, TransitionInfo<S> info) {
            entered.add(state);
        }

        @Override
        public void onTransition(StateMachine<C, S> machine, TransitionInfo<S> info) {
            StateTransitionEvent<C, S> event = new StateTransitionEvent<>(machine, info, exited, entered);
            exited.clear();
            entered.clear();
            if (!bus.isClosed()) {
                bus.post(event);
            }
        }
    }
}
