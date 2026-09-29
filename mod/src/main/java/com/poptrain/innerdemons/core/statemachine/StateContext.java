package com.poptrain.innerdemons.core.statemachine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class StateContext<C, S> {

    private final StateMachine<C, S> machine;
    private final StateNode<C, S> node;
    private final StateContext<C, S> parent;
    private Map<StateDataKey<?>, Object> data;
    private int ticks;
    private boolean active = true;

    StateContext(StateMachine<C, S> machine, StateNode<C, S> node, StateContext<C, S> parent) {
        this.machine = machine;
        this.node = node;
        this.parent = parent;
    }

    public C owner() {
        return machine.owner();
    }

    public S state() {
        return node.key;
    }

    public StateMachine<C, S> machine() {
        return machine;
    }

    public Optional<StateContext<C, S>> parent() {
        return Optional.ofNullable(parent);
    }

    public int ticksInState() {
        return ticks;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isLeaf() {
        return !node.isComposite();
    }

    public TransitionResult requestTransition(S target) {
        if (!active) {
            return TransitionResult.STALE_REQUESTER;
        }
        return machine.request(this, target);
    }

    public TransitionResult fire(Object event) {
        if (!active) {
            return TransitionResult.STALE_REQUESTER;
        }
        return machine.dispatch(this, event);
    }

    public <T> T get(StateDataKey<T> key) {
        ensureActive();
        for (StateContext<C, S> ctx = this; ctx != null; ctx = ctx.parent) {
            if (ctx.data != null && ctx.data.containsKey(key)) {
                return key.type().cast(ctx.data.get(key));
            }
        }
        return null;
    }

    public <T> T getOrDefault(StateDataKey<T> key, T fallback) {
        T value = get(key);
        return value != null ? value : fallback;
    }

    public <T> T getLocal(StateDataKey<T> key) {
        ensureActive();
        return data == null ? null : key.type().cast(data.get(key));
    }

    public boolean has(StateDataKey<?> key) {
        ensureActive();
        for (StateContext<C, S> ctx = this; ctx != null; ctx = ctx.parent) {
            if (ctx.data != null && ctx.data.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    public <T> void put(StateDataKey<T> key, T value) {
        ensureActive();
        if (value == null) {
            remove(key);
            return;
        }
        localData().put(key, key.type().cast(value));
    }

    public <T> T computeIfAbsent(StateDataKey<T> key, Supplier<? extends T> factory) {
        T existing = getLocal(key);
        if (existing != null) {
            return existing;
        }
        T created = factory.get();
        put(key, created);
        return created;
    }

    public void remove(StateDataKey<?> key) {
        ensureActive();
        if (data != null) {
            data.remove(key);
        }
    }

    StateNode<C, S> node() {
        return node;
    }

    void tick() {
        ticks++;
    }

    void setTicks(int value) {
        ticks = Math.max(0, value);
    }

    void deactivate() {
        active = false;
        data = null;
    }

    private Map<StateDataKey<?>, Object> localData() {
        if (data == null) {
            data = new HashMap<>();
        }
        return data;
    }

    private void ensureActive() {
        if (!active) {
            throw new IllegalStateException("State context for " + node.name + " in " + machine.graph().name()
                    + " was used after the state exited");
        }
    }

    @Override
    public String toString() {
        return "StateContext[" + node.name + ", ticks=" + ticks + (active ? "" : ", exited") + "]";
    }
}
