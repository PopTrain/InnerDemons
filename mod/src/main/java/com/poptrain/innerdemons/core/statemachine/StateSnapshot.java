package com.poptrain.innerdemons.core.statemachine;

import java.util.List;
import java.util.Map;

public record StateSnapshot(List<String> path, List<Integer> ticks, Map<String, String> history) {

    public StateSnapshot {
        path = List.copyOf(path);
        ticks = List.copyOf(ticks);
        history = Map.copyOf(history);
    }

    public boolean isEmpty() {
        return path.isEmpty();
    }
}
