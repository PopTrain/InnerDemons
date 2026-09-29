package com.poptrain.innerdemons.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DataSnapshot(Map<String, Object> values) {

    public static final DataSnapshot EMPTY = new DataSnapshot(Map.of());

    public DataSnapshot {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }
}
