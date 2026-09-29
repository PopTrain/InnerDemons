package com.poptrain.innerdemons.core.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class DataRegistry {

    public static final DataRegistry EMPTY = new DataRegistry(Map.of());

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_.:/-]+");

    private final Map<String, DataKey<?>> keys;

    private DataRegistry(Map<String, DataKey<?>> keys) {
        this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    public static DataRegistry of(DataKey<?>... keys) {
        Builder builder = builder();
        for (DataKey<?> key : keys) {
            builder.add(key);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<DataKey<?>> find(String name) {
        return Optional.ofNullable(keys.get(name));
    }

    public boolean contains(DataKey<?> key) {
        return keys.get(key.name()) == key;
    }

    public Collection<DataKey<?>> keys() {
        return keys.values();
    }

    public int size() {
        return keys.size();
    }

    static String checkName(String name) {
        Objects.requireNonNull(name, "name");
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid data key name '" + name + "': use lowercase letters, digits and _ . : / -");
        }
        return name;
    }

    @Override
    public String toString() {
        return "DataRegistry" + keys.keySet();
    }

    public static final class Builder {

        private final Map<String, DataKey<?>> keys = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder add(DataKey<?> key) {
            Objects.requireNonNull(key, "key");
            DataKey<?> existing = keys.get(key.name());
            if (existing != null && existing != key) {
                throw new IllegalArgumentException("Duplicate data key name '" + key.name() + "': " + existing + " and " + key);
            }
            keys.put(key.name(), key);
            return this;
        }

        public Builder addAll(Collection<? extends DataKey<?>> keys) {
            keys.forEach(this::add);
            return this;
        }

        public Builder include(DataRegistry other) {
            return addAll(other.keys());
        }

        public DataRegistry build() {
            return new DataRegistry(keys);
        }
    }
}
