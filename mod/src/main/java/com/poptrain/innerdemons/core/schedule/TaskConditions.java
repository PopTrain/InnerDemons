package com.poptrain.innerdemons.core.schedule;

import java.util.Objects;
import java.util.function.Function;

import com.poptrain.innerdemons.core.condition.Condition;

public final class TaskConditions {

    private TaskConditions() {
    }

    public static <C> Condition<C> scheduled(Function<? super C, ? extends TickScheduler<?>> scheduler, TaskKey<?> key) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(key, "key");
        return Condition.of("scheduled:" + key.name(), c -> scheduler.apply(c).isScheduled(key));
    }

    public static <C> Condition<C> notScheduled(Function<? super C, ? extends TickScheduler<?>> scheduler, TaskKey<?> key) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(key, "key");
        return Condition.of("ready:" + key.name(), c -> !scheduler.apply(c).isScheduled(key));
    }

    public static <C> Condition<C> paused(Function<? super C, ? extends TickScheduler<?>> scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        return Condition.of("schedulerPaused", c -> scheduler.apply(c).isPaused());
    }
}
