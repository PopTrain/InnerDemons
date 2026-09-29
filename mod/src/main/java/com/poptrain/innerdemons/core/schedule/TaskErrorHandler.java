package com.poptrain.innerdemons.core.schedule;

@FunctionalInterface
public interface TaskErrorHandler {

    void onTaskError(TickScheduler<?> scheduler, String task, String phase, RuntimeException error);
}
