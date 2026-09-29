package com.poptrain.innerdemons.core.network;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.poptrain.innerdemons.core.data.DataContainer;
import com.poptrain.innerdemons.core.data.DataKey;
import com.poptrain.innerdemons.core.data.DataSerializer;
import com.poptrain.innerdemons.core.data.DataSerializers;
import com.poptrain.innerdemons.core.event.Subscription;
import com.poptrain.innerdemons.core.statemachine.StateMachine;
import com.poptrain.innerdemons.core.statemachine.StateMachineListener;
import com.poptrain.innerdemons.core.statemachine.TransitionInfo;

public final class StateSync {

    private StateSync() {
    }

    public static <E extends Enum<E>> DataKey<E> stateKey(String name, Class<E> states) {
        return DataKey.<E>builder(name, states).noCopy().build();
    }

    public static <E extends Enum<E>> DataKey<List<E>> pathKey(String name, Class<E> states) {
        return DataKey.<List<E>>builder(name, List.class).noCopy().build();
    }

    public static <E extends Enum<E>> DataSerializer<E> stateSerializer(Class<E> states) {
        return DataSerializers.enumOf(states);
    }

    public static <E extends Enum<E>> DataSerializer<List<E>> pathSerializer(Class<E> states) {
        return DataSerializers.listOf(DataSerializers.enumOf(states));
    }

    public static <C, S> Subscription mirror(StateMachine<C, S> machine, DataContainer data, DataKey<S> key) {
        return mirrorAs(machine, data, key, Function.identity());
    }

    public static <C, S, T> Subscription mirrorAs(StateMachine<C, S> machine, DataContainer data, DataKey<T> key,
            Function<? super S, ? extends T> mapping) {
        Objects.requireNonNull(mapping, "mapping");
        return attach(machine, data, m -> {
            S state = m.currentState();
            if (state == null) {
                data.remove(key);
            } else {
                data.set(key, mapping.apply(state));
            }
        });
    }

    public static <C, S> Subscription mirrorPath(StateMachine<C, S> machine, DataContainer data, DataKey<List<S>> key) {
        return attach(machine, data, m -> {
            List<S> path = m.activeStates();
            if (path.isEmpty()) {
                data.remove(key);
            } else {
                data.set(key, List.copyOf(path));
            }
        });
    }

    private static <C, S> Subscription attach(StateMachine<C, S> machine, DataContainer data,
            java.util.function.Consumer<StateMachine<C, S>> write) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(data, "data");
        StateMachineListener<C, S> listener = new StateMachineListener<>() {
            @Override
            public void onTransition(StateMachine<C, S> m, TransitionInfo<S> info) {
                if (!data.isClosed()) {
                    write.accept(m);
                }
            }
        };
        machine.addListener(listener);
        if (machine.isRunning()) {
            write.accept(machine);
        }
        return Subscription.of(() -> machine.removeListener(listener));
    }
}
