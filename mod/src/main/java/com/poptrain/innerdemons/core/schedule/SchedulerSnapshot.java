package com.poptrain.innerdemons.core.schedule;

import java.util.List;

public record SchedulerSnapshot(long clock, List<TaskSnapshot> tasks) {

    public SchedulerSnapshot {
        tasks = List.copyOf(tasks);
    }
}
