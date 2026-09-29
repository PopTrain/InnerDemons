package com.poptrain.innerdemons.core.statemachine;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.data.DataChangedEvent;
import com.poptrain.innerdemons.core.data.DataContainer;
import com.poptrain.innerdemons.core.data.DataKey;
import com.poptrain.innerdemons.core.data.DataListener;
import com.poptrain.innerdemons.core.event.Subscription;
import com.poptrain.innerdemons.core.event.SubscriptionGroup;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class StateData {

    private StateData() {
    }

    public static <C, S, T> void setWhileActive(StateContext<C, S> ctx, DataContainer data, DataKey<T> key, T value) {
        Objects.requireNonNull(ctx, "ctx");
        Optional<T> previous = data.findLocal(key);
        data.set(key, value);
        ctx.addExitHook(() -> {
            if (data.isClosed()) {
                return;
            }
            if (previous.isPresent()) {
                data.set(key, previous.get());
            } else {
                data.remove(key);
            }
        });
    }

    public static <C, S> Subscription listenWhileActive(StateContext<C, S> ctx, DataContainer data, DataKey<?> key,
                                                        DataListener listener) {
        return ctx.bind(data.listen(key, listener));
    }

    public static <C, S, T> Subscription requestOnChange(StateContext<C, S> ctx, DataContainer data, DataKey<T> key,
                                                         Condition<? super T> when, S target) {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(target, "target");
        return ctx.bind(data.listen(key, change -> change.as(key)
                .map(DataChangedEvent::newValue)
                .filter(when::test)
                .ifPresent(v -> ctx.requestTransition(target))));
    }

    public static <C, S> Subscription fireChangesWhileActive(StateContext<C, S> ctx, DataContainer data, DataKey<?>... keys) {
        return ctx.bind(forward(data, ctx::fire, keys));
    }

    public static <C, S> SubscriptionGroup forwardChanges(StateMachine<C, S> machine, DataContainer data, DataKey<?>... keys) {
        Objects.requireNonNull(machine, "machine");
        return forward(data, machine::fire, keys);
    }

    private static SubscriptionGroup forward(DataContainer data, Function<Object, TransitionResult> fire,
                                             DataKey<?>... keys) {
        Objects.requireNonNull(data, "data");
        SubscriptionGroup group = new SubscriptionGroup();
        DataListener listener = fire::apply;
        if (keys.length == 0) {
            group.add(data.addListener(listener));
        } else {
            for (DataKey<?> key : keys) {
                group.add(data.listen(key, listener));
            }
        }
        return group;
    }
}
