package com.poptrain.innerdemons.core.event;

public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);

    default void cancel() {
        setCancelled(true);
    }
}
