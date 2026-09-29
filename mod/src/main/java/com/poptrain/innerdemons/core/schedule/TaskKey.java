package com.poptrain.innerdemons.core.schedule;

import java.util.Objects;
import java.util.function.Predicate;

import com.poptrain.innerdemons.core.condition.Condition;

public final class TaskKey<C> {

    private final String name;
    private final Class<C> ownerType;
    private final TickTask<? super C> task;
    private final Condition<? super C> when;
    private final Condition<? super C> until;
    private final TickTask<? super C> onTimeout;

    private TaskKey(Builder<C> builder, TickTask<? super C> task) {
        this.name = builder.name;
        this.ownerType = builder.ownerType;
        this.task = task;
        this.when = builder.when;
        this.until = builder.until;
        this.onTimeout = builder.onTimeout;
    }

    public static <C> Builder<C> builder(String name, Class<C> ownerType) {
        return new Builder<>(name, ownerType);
    }

    public static <C> TaskKey<C> of(String name, Class<C> ownerType, TickTask<? super C> task) {
        return builder(name, ownerType).run(task);
    }

    public static <C> TaskKey<C> marker(String name, Class<C> ownerType) {
        return builder(name, ownerType).run(TickTask.none());
    }

    public String name() {
        return name;
    }

    public Class<C> ownerType() {
        return ownerType;
    }

    public TickTask<? super C> task() {
        return task;
    }

    public Condition<? super C> when() {
        return when;
    }

    public Condition<? super C> until() {
        return until;
    }

    public TickTask<? super C> onTimeout() {
        return onTimeout;
    }

    @Override
    public String toString() {
        return "TaskKey[" + name + ":" + ownerType.getSimpleName() + "]";
    }

    public static final class Builder<C> {

        private final String name;
        private final Class<C> ownerType;
        private Condition<? super C> when;
        private Condition<? super C> until;
        private TickTask<? super C> onTimeout;

        private Builder(String name, Class<C> ownerType) {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Task key name must not be blank");
            }
            this.name = name;
            this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        }

        public Builder<C> when(Condition<? super C> condition) {
            Objects.requireNonNull(condition, "condition");
            this.when = when == null ? condition : Condition.<C>allOf(when, condition);
            return this;
        }

        public Builder<C> when(String name, Predicate<? super C> test) {
            return when(Condition.of(name, test));
        }

        public Builder<C> until(Condition<? super C> condition) {
            Objects.requireNonNull(condition, "condition");
            this.until = until == null ? condition : Condition.<C>anyOf(until, condition);
            return this;
        }

        public Builder<C> until(String name, Predicate<? super C> test) {
            return until(Condition.of(name, test));
        }

        public Builder<C> onTimeout(TickTask<? super C> task) {
            this.onTimeout = Objects.requireNonNull(task, "task");
            return this;
        }

        public TaskKey<C> run(TickTask<? super C> task) {
            return new TaskKey<>(this, Objects.requireNonNull(task, "task"));
        }

        public TaskKey<C> run(Runnable task) {
            return run(TickTask.of(task));
        }
    }
}
