package com.poptrain.innerdemons.core.schedule;

import java.util.Objects;

import com.poptrain.innerdemons.core.event.EventPriority;
import com.poptrain.innerdemons.core.event.SubscriptionGroup;

public abstract class AbstractTaskBuilder<C, B extends AbstractTaskBuilder<C, B>> {

    final TickScheduler<C> scheduler;
    int delayMin;
    int delayMax;
    int periodMin;
    int periodMax;
    int times = ScheduledTask.UNLIMITED;
    int timeout = ScheduledTask.UNLIMITED;
    EventPriority priority = EventPriority.NORMAL;
    String name;
    SubscriptionGroup group;

    AbstractTaskBuilder(TickScheduler<C> scheduler) {
        this.scheduler = scheduler;
    }

    abstract B self();

    public B delay(int ticks) {
        requireAtLeast("delay", ticks, 0);
        delayMin = ticks;
        delayMax = ticks;
        return self();
    }

    public B delayBetween(int minTicks, int maxTicks) {
        requireRange("delayBetween", minTicks, maxTicks, 0);
        delayMin = minTicks;
        delayMax = maxTicks;
        return self();
    }

    public B every(int periodTicks) {
        requireAtLeast("every", periodTicks, 1);
        periodMin = periodTicks;
        periodMax = periodTicks;
        return self();
    }

    public B everyBetween(int minTicks, int maxTicks) {
        requireRange("everyBetween", minTicks, maxTicks, 1);
        periodMin = minTicks;
        periodMax = maxTicks;
        return self();
    }

    public B times(int runs) {
        requireAtLeast("times", runs, 1);
        times = runs;
        return self();
    }

    public B timeout(int ticks) {
        requireAtLeast("timeout", ticks, 1);
        timeout = ticks;
        return self();
    }

    public B priority(EventPriority value) {
        priority = Objects.requireNonNull(value, "priority");
        return self();
    }

    public B named(String value) {
        name = Objects.requireNonNull(value, "name");
        return self();
    }

    public B in(SubscriptionGroup value) {
        group = Objects.requireNonNull(value, "group");
        return self();
    }

    private static void requireAtLeast(String what, int value, int min) {
        if (value < min) {
            throw new IllegalArgumentException(what + " must be at least " + min + " but was " + value);
        }
    }

    private static void requireRange(String what, int min, int max, int floor) {
        requireAtLeast(what + " min", min, floor);
        if (max < min) {
            throw new IllegalArgumentException(what + " max " + max + " < min " + min);
        }
    }
}
