package com.poptrain.innerdemons.core.schedule;

import java.util.Objects;

public record TaskSnapshot(String key, String name, int remaining, int timeout, int periodMin, int periodMax,
                           int runsLeft, int runCount, String priority) {

    public TaskSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(priority, "priority");
    }
}
