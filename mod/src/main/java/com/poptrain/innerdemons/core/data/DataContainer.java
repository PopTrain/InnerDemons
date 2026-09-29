package com.poptrain.innerdemons.core.data;

import com.poptrain.innerdemons.core.condition.ConditionResult;
import com.poptrain.innerdemons.core.event.EventBus;
import com.poptrain.innerdemons.core.event.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

public class DataContainer implements DataHolder {

    public static final int DEFAULT_MAX_DEPTH = 16;

    private static final Logger LOGGER = LoggerFactory.getLogger(DataContainer.class);

    private final String name;
    private final Object owner;
    private final DataContainer parent;
    private final DataRegistry registry;
    private final DataErrorHandler errorHandler;
    private final boolean threadConfined;
    private final int maxDepth;
    private final Map<DataKey<?>, Object> values = new LinkedHashMap<>();
    private final Map<String, DataKey<?>> byName = new LinkedHashMap<>();
    private final Map<String, Object> retained = new LinkedHashMap<>();
    private final Set<DataKey<?>> dirty = new LinkedHashSet<>();
    private final List<ListenerEntry> listeners = new ArrayList<>();
    private final List<EventBus> buses = new ArrayList<>();
    private int depth;
    private boolean closed;
    private Thread ownerThread;

    protected DataContainer(Builder builder) {
        this.name = builder.name;
        this.owner = builder.owner;
        this.parent = builder.parent;
        this.registry = builder.registry;
        this.errorHandler = builder.errorHandler;
        this.threadConfined = builder.threadConfined;
        this.maxDepth = builder.maxDepth;
        this.buses.addAll(builder.buses);
    }

    public static DataContainer create(String name) {
        return builder(name).build();
    }

    public static DataContainer create(String name, Object owner) {
        return builder(name).owner(owner).build();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    @Override
    public final DataContainer data() {
        return this;
    }

    public final String name() {
        return name;
    }

    public final Optional<Object> owner() {
        return Optional.ofNullable(owner);
    }

    public final <H> Optional<H> owner(Class<H> type) {
        return type.isInstance(owner) ? Optional.of(type.cast(owner)) : Optional.empty();
    }

    public final Optional<DataContainer> parent() {
        return Optional.ofNullable(parent);
    }

    public final DataRegistry registry() {
        return registry;
    }

    public final boolean isClosed() {
        return closed;
    }

    public final boolean isEmpty() {
        return values.isEmpty();
    }

    public final int size() {
        return values.size();
    }

    public final Set<DataKey<?>> keys() {
        checkThread();
        return Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
    }

    public final void forEach(BiConsumer<? super DataKey<?>, Object> action) {
        checkThread();
        new ArrayList<>(values.entrySet()).forEach(e -> action.accept(e.getKey(), e.getValue()));
    }

    @Override
    public final <T> T get(DataKey<T> key) {
        Objects.requireNonNull(key, "key");
        checkThread();
        T local = local(key);
        if (local != null) {
            return local;
        }
        T inherited = inherited(key);
        if (inherited != null) {
            return inherited;
        }
        if (key.storesDefault()) {
            ensureOpen();
            T created = key.cast(key.createDefault(this));
            store(key, null, created, DataChangedEvent.Kind.ATTACHED);
            return created;
        }
        return key.createDefault(this);
    }

    @Override
    public final <T> Optional<T> find(DataKey<T> key) {
        Objects.requireNonNull(key, "key");
        checkThread();
        T local = local(key);
        return Optional.ofNullable(local != null ? local : inherited(key));
    }

    public final <T> Optional<T> findLocal(DataKey<T> key) {
        Objects.requireNonNull(key, "key");
        checkThread();
        return Optional.ofNullable(local(key));
    }

    @Override
    public final <T> T getOrDefault(DataKey<T> key, T fallback) {
        return find(key).orElse(fallback);
    }

    public final <T> T getOrCreate(DataKey<T> key) {
        Optional<T> existing = find(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (key.storesDefault()) {
            return get(key);
        }
        ensureOpen();
        T created = key.createDefault(this);
        if (created == null) {
            throw new DataException("Key '" + key.name() + "' has no default value to create");
        }
        T value = prepare(key, created);
        store(key, null, value, DataChangedEvent.Kind.SET);
        return value;
    }

    @Override
    public final boolean has(DataKey<?> key) {
        Objects.requireNonNull(key, "key");
        checkThread();
        return values.containsKey(key) || inherited(key) != null;
    }

    public final boolean hasLocal(DataKey<?> key) {
        checkThread();
        return values.containsKey(key);
    }

    @Override
    public final <T> boolean set(DataKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "Use remove(...) to clear '" + key.name() + "'");
        checkThread();
        ensureOpen();
        T next = prepare(key, value);
        T old = local(key);
        if (unchanged(key, old, next)) {
            return false;
        }
        if (!enter()) {
            return false;
        }
        try {
            if (!key.isAttachment()) {
                DataChangingEvent<T> pending = postChanging(key, old, next);
                if (pending != null) {
                    if (pending.isCancelled()) {
                        return false;
                    }
                    if (pending.modified()) {
                        try {
                            next = prepare(key, pending.newValue());
                        } catch (RuntimeException e) {
                            report("changing:" + key.name(), e);
                            return false;
                        }
                        if (unchanged(key, old, next)) {
                            return false;
                        }
                    }
                }
                old = local(key);
            }
            store(key, old, next, key.isAttachment() ? DataChangedEvent.Kind.ATTACHED : DataChangedEvent.Kind.SET);
            return true;
        } finally {
            depth--;
        }
    }

    public final <T> boolean setIfAbsent(DataKey<T> key, T value) {
        return !hasLocal(key) && set(key, value);
    }

    @Override
    public final <T> T update(DataKey<T> key, UnaryOperator<T> operator) {
        Objects.requireNonNull(operator, "operator");
        T current = get(key);
        T next = operator.apply(current);
        if (next == null) {
            remove(key);
        } else {
            set(key, next);
        }
        return find(key).orElseGet(() -> key.createDefault(this));
    }

    public final boolean remove(DataKey<?> key) {
        Objects.requireNonNull(key, "key");
        checkThread();
        ensureOpen();
        return removeTyped(key, true);
    }

    public final void clear() {
        checkThread();
        ensureOpen();
        for (DataKey<?> key : new ArrayList<>(values.keySet())) {
            removeTyped(key, false);
        }
        retained.clear();
    }

    public final void markDirty(DataKey<?> key) {
        checkThread();
        dirty.add(key);
    }

    public final boolean isDirty() {
        return !dirty.isEmpty();
    }

    public final Set<DataKey<?>> dirtyKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(dirty));
    }

    public final void clearDirty() {
        dirty.clear();
    }

    public final Subscription addListener(DataListener listener) {
        return addListener(null, listener);
    }

    public final <T> Subscription listen(DataKey<T> key, DataListener listener) {
        return addListener(Objects.requireNonNull(key, "key"), listener);
    }

    public final Subscription publishTo(EventBus bus) {
        Objects.requireNonNull(bus, "bus");
        checkThread();
        ensureOpen();
        buses.add(bus);
        return Subscription.of(() -> buses.remove(bus));
    }

    public final List<EventBus> buses() {
        return List.copyOf(buses);
    }

    public DataContainer child(String childName, Object childOwner) {
        return builder(childName)
                .owner(childOwner)
                .parent(this)
                .registry(registry)
                .errorHandler(errorHandler)
                .threadConfined(threadConfined)
                .maxDepth(maxDepth)
                .build();
    }

    public final DataSnapshot snapshot() {
        checkThread();
        Map<String, Object> out = new LinkedHashMap<>(retained);
        values.forEach((key, value) -> encodeInto(out, key, value));
        return new DataSnapshot(out);
    }

    public final boolean restore(DataSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        checkThread();
        ensureOpen();
        if (snapshot.isEmpty()) {
            return false;
        }
        boolean complete = true;
        for (Map.Entry<String, Object> entry : snapshot.values().entrySet()) {
            DataKey<?> key = resolve(entry.getKey());
            if (key == null || !key.isPersistent()) {
                retained.put(entry.getKey(), entry.getValue());
                LOGGER.warn("[{}] Keeping unknown saved data '{}' so it is not lost", name, entry.getKey());
                complete = false;
                continue;
            }
            try {
                restoreEntry(key, entry.getValue());
                retained.remove(entry.getKey());
            } catch (RuntimeException e) {
                report("restore:" + entry.getKey(), e);
                complete = false;
            }
        }
        dirty.clear();
        return complete;
    }

    public final Map<String, Object> retained() {
        return Collections.unmodifiableMap(retained);
    }

    public final int copyTo(DataContainer target) {
        Objects.requireNonNull(target, "target");
        checkThread();
        target.checkThread();
        target.ensureOpen();
        int copied = 0;
        for (Map.Entry<DataKey<?>, Object> entry : new ArrayList<>(values.entrySet())) {
            try {
                if (copyEntry(target, entry.getKey(), entry.getValue())) {
                    copied++;
                }
            } catch (RuntimeException e) {
                report("copy:" + entry.getKey().name(), e);
            }
        }
        retained.forEach(target.retained::putIfAbsent);
        return copied;
    }

    public final void close() {
        if (closed) {
            return;
        }
        checkThread();
        for (Map.Entry<DataKey<?>, Object> entry : new ArrayList<>(values.entrySet())) {
            if (entry.getKey().isAttachment()) {
                detach(entry.getKey(), entry.getValue());
                values.remove(entry.getKey());
                byName.remove(entry.getKey().name());
            }
        }
        for (ListenerEntry listener : listeners) {
            listener.active = false;
        }
        listeners.clear();
        buses.clear();
        closed = true;
        onClosed();
    }

    protected void onChanged(DataChangedEvent<?> change) {
    }

    protected void onClosed() {
    }

    protected final void report(String where, RuntimeException error) {
        try {
            errorHandler.onDataError(this, where, error);
        } catch (RuntimeException nested) {
            LOGGER.error("[{}] Data error handler failed while handling {}", name, where, nested);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T local(DataKey<T> key) {
        return (T) values.get(key);
    }

    private <T> T inherited(DataKey<T> key) {
        if (!key.isInherited()) {
            return null;
        }
        for (DataContainer p = parent; p != null; p = p.parent) {
            T value = p.local(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private <T> T prepare(DataKey<T> key, Object raw) {
        T value = key.sanitize(key.cast(raw));
        Objects.requireNonNull(value, "Sanitizer for '" + key.name() + "' returned null");
        ConditionResult result = key.check(value);
        if (result.failed()) {
            throw new IllegalArgumentException("Rejected " + value + " for '" + key.name() + "' (" + result.reason() + ")");
        }
        return value;
    }

    private static <T> boolean unchanged(DataKey<T> key, T old, T next) {
        return old == next || (!key.isAttachment() && Objects.equals(old, next));
    }

    private boolean enter() {
        if (depth >= maxDepth) {
            LOGGER.error("[{}] More than {} nested data changes; dropping the rest (listener loop?)", name, maxDepth);
            return false;
        }
        depth++;
        return true;
    }

    private <T> DataChangingEvent<T> postChanging(DataKey<T> key, T old, T next) {
        DataChangingEvent<T> event = null;
        for (EventBus bus : List.copyOf(buses)) {
            if (bus.isClosed() || !bus.hasListeners(DataChangingEvent.class)) {
                continue;
            }
            if (event == null) {
                event = new DataChangingEvent<>(this, key, old, next);
            }
            bus.post(event);
            if (event.isCancelled()) {
                break;
            }
        }
        return event;
    }

    private <T> void store(DataKey<T> key, T old, T next, DataChangedEvent.Kind kind) {
        claimName(key);
        values.put(key, next);
        dirty.add(key);
        if (key.isAttachment()) {
            if (old != null) {
                detach(key, old);
            }
            attach(key, next);
        }
        notifyChange(new DataChangedEvent<>(this, key, kind, old, next));
    }

    private <T> boolean removeTyped(DataKey<T> key, boolean vetoable) {
        T old = local(key);
        if (old == null) {
            return false;
        }
        if (!enter()) {
            return false;
        }
        try {
            if (vetoable && !key.isAttachment()) {
                DataChangingEvent<T> pending = postChanging(key, old, null);
                if (pending != null && pending.isCancelled()) {
                    return false;
                }
                old = local(key);
                if (old == null) {
                    return false;
                }
            }
            values.remove(key);
            byName.remove(key.name());
            dirty.add(key);
            if (key.isAttachment()) {
                detach(key, old);
            }
            notifyChange(new DataChangedEvent<>(this, key,
                    key.isAttachment() ? DataChangedEvent.Kind.DETACHED : DataChangedEvent.Kind.REMOVED, old, null));
            return true;
        } finally {
            depth--;
        }
    }

    private void claimName(DataKey<?> key) {
        DataKey<?> existing = byName.get(key.name());
        if (existing != null && existing != key) {
            throw new DataException("Container " + name + " already holds a different key named '" + key.name() + "': " + existing);
        }
        byName.put(key.name(), key);
    }

    private DataKey<?> resolve(String keyName) {
        DataKey<?> local = byName.get(keyName);
        if (local != null) {
            return local;
        }
        return registry.find(keyName).orElse(null);
    }

    private <T> void encodeInto(Map<String, Object> out, DataKey<T> key, Object value) {
        if (!key.isPersistent()) {
            return;
        }
        try {
            Object tree = key.encode(key.cast(value));
            if (tree != null) {
                out.put(key.name(), tree);
            }
        } catch (RuntimeException e) {
            report("save:" + key.name(), e);
        }
    }

    private <T> void restoreEntry(DataKey<T> key, Object tree) {
        T existing = local(key);
        T loaded = key.decode(this, existing, tree);
        if (loaded == null) {
            throw new DataException("Key '" + key.name() + "' decoded to nothing");
        }
        T value = prepare(key, loaded);
        claimName(key);
        values.put(key, value);
        if (key.isAttachment() && value != existing) {
            if (existing != null) {
                detach(key, existing);
            }
            attach(key, value);
        }
    }

    private <T> boolean copyEntry(DataContainer target, DataKey<T> key, Object value) {
        if (key instanceof AttachmentKey<?, ?> attachment && !target.owner().map(attachment::accepts).orElse(false)) {
            return false;
        }
        T copy = key.copyFor(target, key.cast(value));
        if (copy == null) {
            return false;
        }
        T prepared = target.prepare(key, copy);
        T old = target.local(key);
        if (unchanged(key, old, prepared)) {
            return false;
        }
        if (!target.enter()) {
            return false;
        }
        try {
            target.store(key, old, prepared, DataChangedEvent.Kind.COPIED);
        } finally {
            target.depth--;
        }
        return true;
    }

    private <T> void attach(DataKey<T> key, Object value) {
        try {
            key.onAdded(this, key.cast(value));
        } catch (RuntimeException e) {
            report("attach:" + key.name(), e);
        }
    }

    private <T> void detach(DataKey<T> key, Object value) {
        try {
            key.onRemoved(this, key.cast(value));
        } catch (RuntimeException e) {
            report("detach:" + key.name(), e);
        }
    }

    private void notifyChange(DataChangedEvent<?> change) {
        try {
            onChanged(change);
        } catch (RuntimeException e) {
            report("onChanged:" + change.key().name(), e);
        }
        if (!listeners.isEmpty()) {
            for (ListenerEntry entry : List.copyOf(listeners)) {
                if (!entry.active || (entry.key != null && entry.key != change.key())) {
                    continue;
                }
                try {
                    entry.listener.onChanged(change);
                } catch (RuntimeException e) {
                    report("listener:" + change.key().name(), e);
                }
            }
        }
        for (EventBus bus : List.copyOf(buses)) {
            if (!bus.isClosed() && bus.hasListeners(DataChangedEvent.class)) {
                bus.post(change);
            }
        }
    }

    private Subscription addListener(DataKey<?> key, DataListener listener) {
        Objects.requireNonNull(listener, "listener");
        checkThread();
        ensureOpen();
        ListenerEntry entry = new ListenerEntry(key, listener);
        listeners.add(entry);
        return new Subscription() {
            @Override
            public void cancel() {
                entry.active = false;
                listeners.remove(entry);
            }

            @Override
            public boolean isActive() {
                return entry.active;
            }
        };
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Data container " + name + " is closed");
        }
    }

    private void checkThread() {
        if (!threadConfined) {
            return;
        }
        Thread current = Thread.currentThread();
        if (ownerThread == null) {
            ownerThread = current;
        } else if (ownerThread != current) {
            throw new IllegalStateException("Data container " + name + " is confined to thread " + ownerThread.getName()
                    + " but was used from " + current.getName());
        }
    }

    private static void logError(DataContainer container, String where, RuntimeException error) {
        LOGGER.error("[{}] Data error in {}", container.name(), where, error);
    }

    @Override
    public String toString() {
        return "DataContainer[" + name + ", " + values.size() + " entries" + (closed ? ", closed" : "") + "]";
    }

    private static final class ListenerEntry {
        private final DataKey<?> key;
        private final DataListener listener;
        private boolean active = true;

        private ListenerEntry(DataKey<?> key, DataListener listener) {
            this.key = key;
            this.listener = listener;
        }
    }

    public static class Builder {

        private final String name;
        private Object owner;
        private DataContainer parent;
        private DataRegistry registry = DataRegistry.EMPTY;
        private DataErrorHandler errorHandler = DataContainer::logError;
        private boolean threadConfined = true;
        private int maxDepth = DEFAULT_MAX_DEPTH;
        private final List<EventBus> buses = new ArrayList<>();

        protected Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder owner(Object owner) {
            this.owner = owner;
            return this;
        }

        public Builder parent(DataContainer parent) {
            this.parent = parent;
            return this;
        }

        public Builder registry(DataRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "registry");
            return this;
        }

        public Builder bus(EventBus bus) {
            this.buses.add(Objects.requireNonNull(bus, "bus"));
            return this;
        }

        public Builder errorHandler(DataErrorHandler errorHandler) {
            this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
            return this;
        }

        public Builder threadConfined(boolean threadConfined) {
            this.threadConfined = threadConfined;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            if (maxDepth < 1) {
                throw new IllegalArgumentException("maxDepth must be at least 1");
            }
            this.maxDepth = maxDepth;
            return this;
        }

        public String name() {
            return name;
        }

        public DataContainer build() {
            return new DataContainer(this);
        }
    }
}
