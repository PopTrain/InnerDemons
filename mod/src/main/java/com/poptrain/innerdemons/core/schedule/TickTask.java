package com.poptrain.innerdemons.core.schedule;

import java.util.Objects;

@FunctionalInterface
public interface TickTask<C> {

    void run(TaskContext<? extends C> ctx);

    static <C> TickTask<C> of(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        return ctx -> runnable.run();
    }

    static <C> TickTask<C> none() {
        return ctx -> {
        };
    }
}
