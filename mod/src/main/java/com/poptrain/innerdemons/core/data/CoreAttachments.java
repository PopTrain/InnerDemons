package com.poptrain.innerdemons.core.data;

import com.poptrain.innerdemons.core.event.EventBus;
import com.poptrain.innerdemons.core.event.SubscriptionGroup;
import com.poptrain.innerdemons.core.random.PityCounter;
import com.poptrain.innerdemons.core.random.PityRule;
import com.poptrain.innerdemons.core.random.SeededRng;
import com.poptrain.innerdemons.core.schedule.TickScheduler;
import com.poptrain.innerdemons.core.statemachine.StateMachine;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class CoreAttachments {

    private CoreAttachments() {
    }

    public static <H> AttachmentKey<H, TickScheduler<H>> scheduler(String name, Class<H> holderType,
                                                                   Function<? super H, TickScheduler<H>> factory) {
        Objects.requireNonNull(factory, "factory");
        return AttachmentKey.<H, TickScheduler<H>>builder(name, holderType, TickScheduler.class)
                .factory(factory)
                .savedAs(DataSerializers.SCHEDULER_SNAPSHOT, TickScheduler::snapshot, TickScheduler::restore)
                .onDetach((holder, scheduler) -> scheduler.close())
                .build();
    }

    public static <H, M extends StateMachine<?, ?>> AttachmentKey<H, M> stateMachine(String name, Class<H> holderType,
                                                                                    Class<? super M> machineType,
                                                                                    Function<? super H, ? extends M> factory) {
        Objects.requireNonNull(factory, "factory");
        return AttachmentKey.<H, M>builder(name, holderType, machineType)
                .factory(factory)
                .savedAs(DataSerializers.STATE_SNAPSHOT, StateMachine::snapshot, StateMachine::restore)
                .onDetach((holder, machine) -> {
                    if (machine.isRunning()) {
                        machine.stop();
                    }
                })
                .build();
    }

    public static <H> AttachmentKey<H, SeededRng> rng(String name, Class<H> holderType, ToLongFunction<? super H> seed) {
        Objects.requireNonNull(seed, "seed");
        return AttachmentKey.<H, SeededRng>builder(name, holderType, SeededRng.class)
                .factory((H holder) -> SeededRng.of(seed.applyAsLong(holder)))
                .savedAs(DataSerializers.RNG_STATE, SeededRng::state, SeededRng::restore)
                .build();
    }

    public static <H> AttachmentKey<H, PityCounter> pity(String name, Class<H> holderType, PityRule rule) {
        Objects.requireNonNull(rule, "rule");
        return AttachmentKey.<H, PityCounter>builder(name, holderType, PityCounter.class)
                .factory(() -> new PityCounter(rule))
                .savedAs(DataSerializers.INT, PityCounter::failures, PityCounter::setFailures)
                .build();
    }

    public static <H> AttachmentKey<H, EventBus> bus(String name, Class<H> holderType, Function<? super H, EventBus> factory) {
        Objects.requireNonNull(factory, "factory");
        return AttachmentKey.<H, EventBus>builder(name, holderType, EventBus.class)
                .factory(factory)
                .onDetach((holder, bus) -> bus.close())
                .build();
    }

    public static <H> AttachmentKey<H, SubscriptionGroup> subscriptions(String name, Class<H> holderType) {
        return AttachmentKey.<H, SubscriptionGroup>builder(name, holderType, SubscriptionGroup.class)
                .factory(SubscriptionGroup::new)
                .build();
    }
}
