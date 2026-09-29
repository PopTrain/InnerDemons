package com.poptrain.innerdemons.core.schedule;

import java.util.Objects;
import java.util.function.Predicate;

import com.poptrain.innerdemons.core.condition.Condition;

public final class TaskBuilder<C> extends AbstractTaskBuilder<C, TaskBuilder<C>> {

    Condition<? super C> when;
    Condition<? super C> until;
    TickTask<? super C> onTimeout;

    TaskBuilder(TickScheduler<C> scheduler) {
        super(scheduler);
    }

    @Override
    TaskBuilder<C> self() {
        return this;
    }

    public TaskBuilder<C> when(Condition<? super C> condition) {
        Objects.requireNonNull(condition, "condition");
        when = when == null ? condition : Condition.<C>allOf(when, condition);
        return this;
    }

    public TaskBuilder<C> when(String name, Predicate<? super C> test) {
        return when(Condition.of(name, test));
    }

    public TaskBuilder<C> until(Condition<? super C> condition) {
        Objects.requireNonNull(condition, "condition");
        until = until == null ? condition : Condition.<C>anyOf(until, condition);
        return this;
    }

    public TaskBuilder<C> until(String name, Predicate<? super C> test) {
        return until(Condition.of(name, test));
    }

    public TaskBuilder<C> onTimeout(TickTask<? super C> task) {
        onTimeout = Objects.requireNonNull(task, "task");
        return this;
    }

    public TaskBuilder<C> onTimeout(Runnable task) {
        return onTimeout(TickTask.of(task));
    }

    public ScheduledTask run(TickTask<? super C> task) {
        return scheduler.submit(this, Objects.requireNonNull(task, "task"));
    }

    public ScheduledTask run(Runnable task) {
        return run(TickTask.of(task));
    }
}
