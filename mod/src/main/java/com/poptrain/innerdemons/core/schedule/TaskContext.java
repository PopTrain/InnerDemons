package com.poptrain.innerdemons.core.schedule;

import com.poptrain.innerdemons.core.random.Rng;

public final class TaskContext<C> {

    private final TickScheduler<C> scheduler;
    private final ScheduledTask task;

    TaskContext(TickScheduler<C> scheduler, ScheduledTask task) {
        this.scheduler = scheduler;
        this.task = task;
    }

    public C owner() {
        return scheduler.owner();
    }

    public TickScheduler<C> scheduler() {
        return scheduler;
    }

    public ScheduledTask task() {
        return task;
    }

    public int runCount() {
        return task.runCount();
    }

    public boolean isFinalRun() {
        return task.runsLeft() == 0;
    }

    public long tick() {
        return scheduler.clock();
    }

    public Rng rng() {
        return scheduler.rng();
    }

    public void cancel() {
        task.cancel();
    }

    @Override
    public String toString() {
        return "TaskContext[" + task.name() + ", run " + task.runCount() + ", tick " + scheduler.clock() + "]";
    }
}
