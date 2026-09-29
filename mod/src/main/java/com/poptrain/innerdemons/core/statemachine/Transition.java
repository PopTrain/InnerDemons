package com.poptrain.innerdemons.core.statemachine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Transition<C, S> {

    public enum Kind {
        REQUEST,
        EVENT,
        AUTO
    }

    record NamedGuard<C, S>(String name, TransitionGuard<C, S> guard) {
    }

    private static final Comparator<Transition<?, ?>> ORDER =
            Comparator.<Transition<?, ?>>comparingInt(t -> -t.priority).thenComparingInt(t -> t.order);

    private final S from;
    private final S to;
    private final Kind kind;
    private final Class<?> eventType;
    private final List<NamedGuard<C, S>> guards;
    private final TransitionAction<C, S> action;
    private final int priority;
    private final int order;
    private final String name;

    Transition(S from, S to, Kind kind, Class<?> eventType, List<NamedGuard<C, S>> guards,
               TransitionAction<C, S> action, int priority, int order, String name) {
        this.from = from;
        this.to = to;
        this.kind = kind;
        this.eventType = eventType;
        this.guards = List.copyOf(guards);
        this.action = action;
        this.priority = priority;
        this.order = order;
        this.name = name;
    }

    static <C, S> List<Transition<C, S>> sorted(List<Transition<C, S>> list) {
        List<Transition<C, S>> copy = new ArrayList<>(list);
        copy.sort(ORDER);
        return List.copyOf(copy);
    }

    public S from() {
        return from;
    }

    public S to() {
        return to;
    }

    public Kind kind() {
        return kind;
    }

    public Class<?> eventType() {
        return eventType;
    }

    public int priority() {
        return priority;
    }

    public String name() {
        return name;
    }

    public boolean isGlobal() {
        return from == null;
    }

    TransitionAction<C, S> action() {
        return action;
    }

    List<NamedGuard<C, S>> guards() {
        return guards;
    }

    boolean matchesEvent(Object event) {
        return eventType != null && eventType.isInstance(event);
    }

    @Override
    public String toString() {
        return "Transition[" + name + ", " + kind + "]";
    }
}
