package com.poptrain.innerdemons.core.schedule;

public final class KeyedTaskBuilder<C> extends AbstractTaskBuilder<C, KeyedTaskBuilder<C>> {

    enum Mode {
        ADD,
        IF_ABSENT,
        REPLACE
    }

    final TaskKey<? super C> key;
    Mode mode = Mode.ADD;

    KeyedTaskBuilder(TickScheduler<C> scheduler, TaskKey<? super C> key) {
        super(scheduler);
        this.key = key;
    }

    @Override
    KeyedTaskBuilder<C> self() {
        return this;
    }

    public KeyedTaskBuilder<C> ifAbsent() {
        mode = Mode.IF_ABSENT;
        return this;
    }

    public KeyedTaskBuilder<C> replacing() {
        mode = Mode.REPLACE;
        return this;
    }

    public ScheduledTask start() {
        return scheduler.submit(this);
    }
}
