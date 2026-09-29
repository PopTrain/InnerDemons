package com.poptrain.innerdemons.core.event;

@FunctionalInterface
public interface ListenerErrorHandler {

    void onListenerError(EventBus bus, Object event, String listener, RuntimeException error);
}
