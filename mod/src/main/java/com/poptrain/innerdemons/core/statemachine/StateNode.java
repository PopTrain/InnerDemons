package com.poptrain.innerdemons.core.statemachine;

import java.util.ArrayList;
import java.util.List;

final class StateNode<C, S> {

    final S key;
    final String name;
    final boolean history;
    final StateBehavior<C, S> behavior;
    StateNode<C, S> parent;
    StateNode<C, S> initialChild;
    int depth;
    List<StateNode<C, S>> children = new ArrayList<>();
    List<Transition<C, S>> requestTransitions = new ArrayList<>();
    List<Transition<C, S>> eventTransitions = new ArrayList<>();
    List<Transition<C, S>> autoTransitions = new ArrayList<>();

    StateNode(S key, String name, boolean history, StateBehavior<C, S> behavior) {
        this.key = key;
        this.name = name;
        this.history = history;
        this.behavior = behavior;
    }

    boolean isComposite() {
        return !children.isEmpty();
    }

    boolean isDescendantOf(StateNode<C, S> other) {
        for (StateNode<C, S> n = parent; n != null; n = n.parent) {
            if (n == other) {
                return true;
            }
        }
        return false;
    }

    void freeze() {
        children = List.copyOf(children);
        requestTransitions = Transition.sorted(requestTransitions);
        eventTransitions = Transition.sorted(eventTransitions);
        autoTransitions = Transition.sorted(autoTransitions);
    }

    @Override
    public String toString() {
        return name;
    }
}
