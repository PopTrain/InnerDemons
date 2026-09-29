package com.poptrain.innerdemons.core.event;

@FunctionalInterface
public interface EventListener<E> {

    void onEvent(E event);
}
