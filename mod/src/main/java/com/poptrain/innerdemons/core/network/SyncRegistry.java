package com.poptrain.innerdemons.core.network;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.poptrain.innerdemons.core.data.DataKey;
import com.poptrain.innerdemons.core.data.DataSerializer;

import net.minecraft.resources.ResourceLocation;

public final class SyncRegistry {

    public static final int DEFAULT_POLL_INTERVAL = 20;

    private static final Map<ResourceLocation, SyncRegistry> BY_ID = new ConcurrentHashMap<>();

    private final ResourceLocation id;
    private final Map<String, SyncedKey<?>> keys;
    private final int pollInterval;

    private SyncRegistry(Builder builder) {
        this.id = builder.id;
        this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(builder.keys));
        this.pollInterval = builder.pollInterval;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static Builder builder(String path) {
        return new Builder(Network.id(path));
    }

    public static Optional<SyncRegistry> byId(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public ResourceLocation id() {
        return id;
    }

    public Collection<SyncedKey<?>> keys() {
        return keys.values();
    }

    public Collection<String> names() {
        return keys.keySet();
    }

    public Optional<SyncedKey<?>> find(String name) {
        return Optional.ofNullable(keys.get(name));
    }

    public boolean contains(DataKey<?> key) {
        SyncedKey<?> synced = keys.get(key.name());
        return synced != null && synced.key() == key;
    }

    public int pollInterval() {
        return pollInterval;
    }

    public int size() {
        return keys.size();
    }

    @Override
    public String toString() {
        return "SyncRegistry[" + id + ", " + keys.keySet() + "]";
    }

    public static final class Builder {

        private final ResourceLocation id;
        private final Map<String, SyncedKey<?>> keys = new LinkedHashMap<>();
        private int pollInterval = DEFAULT_POLL_INTERVAL;

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public <T> Builder key(DataKey<T> key) {
            return key(key, SyncScope.TRACKING);
        }

        public <T> Builder key(DataKey<T> key, SyncScope scope) {
            if (key.isAttachment()) {
                throw new IllegalArgumentException("Attachment keys can't be synced: " + key.name()
                        + ". Mirror the part the client needs into a plain key instead.");
            }
            DataSerializer<T> serializer = key.serializer().orElseThrow(() -> new IllegalArgumentException(
                    "Key " + key.name() + " has no serializer. Make it persistent or pass a serializer to key(...)"));
            return key(key, serializer, scope);
        }

        public <T> Builder key(DataKey<T> key, DataSerializer<T> serializer) {
            return key(key, serializer, SyncScope.TRACKING);
        }

        public <T> Builder key(DataKey<T> key, DataSerializer<T> serializer, SyncScope scope) {
            return add(new SyncedKey<>(key, serializer, scope));
        }

        public Builder include(SyncRegistry other) {
            other.keys().forEach(this::add);
            return this;
        }

        public Builder pollEvery(int ticks) {
            if (ticks < 0) {
                throw new IllegalArgumentException("pollEvery must be >= 0, was " + ticks);
            }
            this.pollInterval = ticks;
            return this;
        }

        private Builder add(SyncedKey<?> synced) {
            SyncedKey<?> existing = keys.get(synced.name());
            if (existing != null && existing.key() != synced.key()) {
                throw new IllegalArgumentException("Duplicate synced key name " + synced.name() + " in " + id);
            }
            if (existing != null && existing.scope() != synced.scope()) {
                throw new IllegalArgumentException("Key " + synced.name() + " is synced with two scopes in " + id);
            }
            keys.putIfAbsent(synced.name(), synced);
            return this;
        }

        public SyncRegistry build() {
            SyncRegistry registry = new SyncRegistry(this);
            SyncRegistry previous = BY_ID.putIfAbsent(id, registry);
            if (previous != null) {
                throw new IllegalStateException("A SyncRegistry with id " + id + " already exists");
            }
            return registry;
        }
    }
}
