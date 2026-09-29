package com.poptrain.innerdemons.core.statemachine;

import java.util.Objects;

import com.poptrain.innerdemons.core.schedule.ScheduledTask;
import com.poptrain.innerdemons.core.schedule.TickScheduler;

public final class StateTasks {

    private StateTasks() {
    }

    public static <C, S> ScheduledTask after(StateContext<C, S> ctx, TickScheduler<?> scheduler, int delay, Runnable task) {
        Objects.requireNonNull(task, "task");
        return ctx.bind(scheduler.task().delay(delay).named(label(ctx, "after " + delay)).run(task));
    }

    public static <C, S> ScheduledTask every(StateContext<C, S> ctx, TickScheduler<?> scheduler, int period, Runnable task) {
        Objects.requireNonNull(task, "task");
        return ctx.bind(scheduler.task().delay(period).every(period).named(label(ctx, "every " + period)).run(task));
    }

    public static <C, S> ScheduledTask requestAfter(StateContext<C, S> ctx, TickScheduler<?> scheduler, int delay, S target) {
        Objects.requireNonNull(target, "target");
        return ctx.bind(scheduler.task().delay(delay).named(label(ctx, "-> " + target))
                .run(() -> ctx.requestTransition(target)));
    }

    public static <C, S> ScheduledTask fireAfter(StateContext<C, S> ctx, TickScheduler<?> scheduler, int delay, Object event) {
        Objects.requireNonNull(event, "event");
        return ctx.bind(scheduler.task().delay(delay).named(label(ctx, "fire " + event.getClass().getSimpleName()))
                .run(() -> ctx.fire(event)));
    }

    private static String label(StateContext<?, ?> ctx, String what) {
        return ctx.machine().graph().name() + ":" + ctx.state() + " " + what;
    }
}
