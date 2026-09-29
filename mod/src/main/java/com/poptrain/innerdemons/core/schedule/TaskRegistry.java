package com.poptrain.innerdemons.core.schedule;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TaskRegistry<C> {

    private final Map<String, TaskKey<? super C>> keys;

    private TaskRegistry(Map<String, TaskKey<? super C>> keys) {
        this.keys = Collections.unmodifiableMap(keys);
    }

    @SafeVarargs
    public static <C> TaskRegistry<C> of(TaskKey<? super C>... keys) {
        return TaskRegistry.<C>builder().addAll(List.of(keys)).build();
    }

    public static <C> Builder<C> builder() {
        return new Builder<>();
    }

    public Optional<TaskKey<? super C>> find(String name) {
        return Optional.ofNullable(keys.get(name));
    }

    public boolean contains(TaskKey<?> key) {
        return key != null && keys.get(key.name()) == key;
    }

    public Collection<TaskKey<? super C>> keys() {
        return keys.values();
    }

    public int size() {
        return keys.size();
    }

    @Override
    public String toString() {
        return "TaskRegistry" + keys.keySet();
    }

    public static final class Builder<C> {

        private final Map<String, TaskKey<? super C>> keys = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder<C> add(TaskKey<? super C> key) {
            Objects.requireNonNull(key, "key");
            TaskKey<? super C> existing = keys.putIfAbsent(key.name(), key);
            if (existing != null && existing != key) {
                throw new IllegalArgumentException("Duplicate task key name: " + key.name());
            }
            return this;
        }

        public Builder<C> addAll(Collection<? extends TaskKey<? super C>> values) {
            for (TaskKey<? super C> key : values) {
                add(key);
            }
            return this;
        }

        public Builder<C> include(TaskRegistry<? super C> other) {
            for (TaskKey<?> key : other.keys.values()) {
                @SuppressWarnings("unchecked")
                TaskKey<? super C> typed = (TaskKey<? super C>) key;
                add(typed);
            }
            return this;
        }

        public TaskRegistry<C> build() {
            return new TaskRegistry<C>(new LinkedHashMap<String, TaskKey<? super C>>(keys));
        }
    }
}
