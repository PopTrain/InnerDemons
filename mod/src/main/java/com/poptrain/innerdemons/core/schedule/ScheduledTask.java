package com.poptrain.innerdemons.core.schedule;

import java.util.Optional;

import com.poptrain.innerdemons.core.event.Subscription;

public interface ScheduledTask extends Subscription {

    int UNLIMITED = -1;

    String name();

    Optional<TaskKey<?>> key();

    int runCount();

    int runsLeft();

    int remainingTicks();

    boolean isRepeating();

    Optional<String> blockedBy();
}
