package com.poptrain.innerdemons.core.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.poptrain.innerdemons.core.condition.Condition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EventBus {

    public static final int DEFAULT_MAX_DEPTH = 16;
    public static final int DEFAULT_MAX_QUEUED = 256;

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);

    private static final Registration<?>[] NONE = new Registration<?>[0];

    private static final Comparator<Registration<?>> ORDER =
            Comparator.<Registration<?>>comparingInt(r -> r.priority.ordinal()).thenComparingLong(r -> r.order);

    private static final ClassValue<List<Class<?>>> HIERARCHY = new ClassValue<>() {
        @Override
        protected List<Class<?>> computeValue(Class<?> type) {
            Set<Class<?>> out = new LinkedHashSet<>();
            collect(type, out);
            return List.copyOf(out);
        }

        private void collect(Class<?> type, Set<Class<?>> out) {
            if (type == null || !out.add(type)) {
                return;
            }
            collect(type.getSuperclass(), out);
            for (Class<?> iface : type.getInterfaces()) {
                collect(iface, out);
            }
        }
    };

    private final String name;
    private final EventBus parent;
    private final boolean threadConfined;
    private final int maxDepth;
    private final int maxQueued;
    private final ListenerErrorHandler errorHandler;
    private final Map<Class<?>, List<Registration<?>>> byType = new HashMap<>();
    private final Map<Class<?>, Registration<?>[]> dispatchCache = new HashMap<>();
    private final List<EventBus> children = new ArrayList<>();
    private final ArrayDeque<Object> queue = new ArrayDeque<>();
    private long nextOrder;
    private int depth;
    private boolean draining;
    private boolean closed;
    private Thread ownerThread;

    private EventBus(Builder builder, EventBus parent) {
        this.name = builder.name;
        this.parent = parent;
        this.threadConfined = builder.threadConfined;
        this.maxDepth = builder.maxDepth;
        this.maxQueued = builder.maxQueued;
        this.errorHandler = builder.errorHandler;
    }

    public static EventBus create(String name) {
        return builder(name).build();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public EventBus parent() {
        return parent;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isDispatching() {
        return depth > 0 || draining;
    }

    public EventBus child(String childName) {
        checkThread();
        ensureOpen();
        Builder builder = new Builder(name + "/" + Objects.requireNonNull(childName, "childName"));
        builder.threadConfined = threadConfined;
        builder.maxDepth = maxDepth;
        builder.maxQueued = maxQueued;
        builder.errorHandler = errorHandler;
        EventBus child = new EventBus(builder, this);
        children.add(child);
        return child;
    }

    public <E> SubscriptionBuilder<E> listen(Class<E> type) {
        return new SubscriptionBuilder<>(this, Objects.requireNonNull(type, "type"));
    }

    public <E> Subscription subscribe(Class<E> type, EventListener<? super E> listener) {
        return listen(type).subscribe(listener);
    }

    public <E> Subscription subscribe(Class<E> type, EventPriority priority, EventListener<? super E> listener) {
        return listen(type).priority(priority).subscribe(listener);
    }

    public boolean hasListeners(Class<?> type) {
        checkThread();
        if (listenersFor(type).length > 0) {
            return true;
        }
        return parent != null && parent.hasListeners(type);
    }

    public int listenerCount(Class<?> type) {
        checkThread();
        return listenersFor(type).length;
    }

    public <E> E post(E event) {
        Objects.requireNonNull(event, "event");
        checkThread();
        if (closed) {
            LOGGER.debug("[{}] Ignoring {} posted after the bus was closed", name, event);
            return event;
        }
        if (depth >= maxDepth) {
            LOGGER.error("[{}] Dropping {}: events nested more than {} deep. A listener is almost certainly re-posting in a loop.",
                    name, event.getClass().getSimpleName(), maxDepth);
            return event;
        }
        depth++;
        try {
            dispatch(event);
            if (parent != null && !parent.closed) {
                parent.post(event);
            }
        } finally {
            depth--;
        }
        if (depth == 0) {
            drain();
        }
        return event;
    }

    public void enqueue(Object event) {
        Objects.requireNonNull(event, "event");
        checkThread();
        if (closed) {
            LOGGER.debug("[{}] Ignoring {} queued after the bus was closed", name, event);
            return;
        }
        if (!isDispatching()) {
            post(event);
            return;
        }
        queue.add(event);
    }

    public boolean isCancelled(Object event) {
        return event instanceof Cancellable c && c.isCancelled();
    }

    public void close() {
        checkThread();
        if (closed) {
            return;
        }
        for (EventBus child : List.copyOf(children)) {
            child.close();
        }
        closed = true;
        queue.clear();
        for (List<Registration<?>> list : byType.values()) {
            for (Registration<?> r : list) {
                r.active = false;
            }
        }
        byType.clear();
        dispatchCache.clear();
        if (parent != null) {
            parent.children.remove(this);
        }
    }

    <E> Subscription register(Class<E> type, EventListener<? super E> listener, EventPriority priority,
                              boolean receiveCancelled, Condition<? super E> filter, boolean once, String label) {
        checkThread();
        ensureOpen();
        Registration<E> registration = new Registration<>(this, type, listener, priority,
                receiveCancelled || priority == EventPriority.MONITOR, filter, once,
                label != null ? label : listener.toString(), nextOrder++);
        byType.computeIfAbsent(type, k -> new ArrayList<>()).add(registration);
        invalidate(type);
        return registration;
    }

    private void unregister(Registration<?> registration) {
        List<Registration<?>> list = byType.get(registration.type);
        if (list != null && list.remove(registration)) {
            if (list.isEmpty()) {
                byType.remove(registration.type);
            }
            invalidate(registration.type);
        }
    }

    private void invalidate(Class<?> type) {
        dispatchCache.keySet().removeIf(type::isAssignableFrom);
    }

    private Registration<?>[] listenersFor(Class<?> eventType) {
        Registration<?>[] cached = dispatchCache.get(eventType);
        if (cached != null) {
            return cached;
        }
        List<Registration<?>> merged = new ArrayList<>();
        for (Class<?> type : HIERARCHY.get(eventType)) {
            List<Registration<?>> list = byType.get(type);
            if (list != null) {
                merged.addAll(list);
            }
        }
        Registration<?>[] result;
        if (merged.isEmpty()) {
            result = NONE;
        } else {
            merged.sort(ORDER);
            result = merged.toArray(NONE);
        }
        dispatchCache.put(eventType, result);
        return result;
    }

    private void dispatch(Object event) {
        Registration<?>[] listeners = listenersFor(event.getClass());
        Cancellable cancellable = event instanceof Cancellable c ? c : null;
        for (Registration<?> registration : listeners) {
            if (!registration.active) {
                continue;
            }
            if (cancellable != null && cancellable.isCancelled() && !registration.receiveCancelled) {
                continue;
            }
            registration.deliver(event);
        }
    }

    private void drain() {
        if (draining || queue.isEmpty()) {
            return;
        }
        draining = true;
        try {
            int processed = 0;
            while (!queue.isEmpty()) {
                if (closed) {
                    queue.clear();
                    return;
                }
                if (++processed > maxQueued) {
                    LOGGER.error("[{}] More than {} chained queued events, dropping {} more. This is almost always an event loop.",
                            name, maxQueued, queue.size());
                    queue.clear();
                    return;
                }
                post(queue.poll());
            }
        } finally {
            draining = false;
        }
    }

    private void reportError(Object event, String listener, RuntimeException error) {
        try {
            errorHandler.onListenerError(this, event, listener, error);
        } catch (RuntimeException nested) {
            LOGGER.error("[{}] Listener error handler itself failed", name, nested);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Event bus " + name + " is closed");
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
            throw new IllegalStateException("Event bus " + name + " owned by thread " + ownerThread.getName()
                    + " was accessed from " + current.getName());
        }
    }

    private static void logError(EventBus bus, Object event, String listener, RuntimeException error) {
        LOGGER.error("[{}] Listener {} failed while handling {}", bus.name, listener, event, error);
    }

    @Override
    public String toString() {
        return "EventBus[" + name + (closed ? ", closed" : "") + "]";
    }

    public static final class Builder {

        private final String name;
        private boolean threadConfined = true;
        private int maxDepth = DEFAULT_MAX_DEPTH;
        private int maxQueued = DEFAULT_MAX_QUEUED;
        private ListenerErrorHandler errorHandler = EventBus::logError;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder threadConfined(boolean value) {
            this.threadConfined = value;
            return this;
        }

        public Builder maxDepth(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("maxDepth must be at least 1");
            }
            this.maxDepth = value;
            return this;
        }

        public Builder maxQueued(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("maxQueued must be at least 1");
            }
            this.maxQueued = value;
            return this;
        }

        public Builder errorHandler(ListenerErrorHandler handler) {
            this.errorHandler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        public EventBus build() {
            return new EventBus(this, null);
        }
    }

    private static final class Registration<E> implements Subscription {

        private final EventBus bus;
        private final Class<E> type;
        private final EventListener<? super E> listener;
        private final EventPriority priority;
        private final boolean receiveCancelled;
        private final Condition<? super E> filter;
        private final boolean once;
        private final String label;
        private final long order;
        private boolean active = true;

        private Registration(EventBus bus, Class<E> type, EventListener<? super E> listener, EventPriority priority,
                             boolean receiveCancelled, Condition<? super E> filter, boolean once, String label, long order) {
            this.bus = bus;
            this.type = type;
            this.listener = listener;
            this.priority = priority;
            this.receiveCancelled = receiveCancelled;
            this.filter = filter;
            this.once = once;
            this.label = label;
            this.order = order;
        }

        private void deliver(Object raw) {
            E event = type.cast(raw);
            if (filter != null) {
                boolean passed;
                try {
                    passed = filter.test(event);
                } catch (RuntimeException e) {
                    bus.reportError(raw, label + " filter " + filter.describe(), e);
                    return;
                }
                if (!passed) {
                    return;
                }
            }
            if (once) {
                cancel();
            }
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                bus.reportError(raw, label, e);
            }
        }

        @Override
        public void cancel() {
            if (!active) {
                return;
            }
            bus.checkThread();
            active = false;
            bus.unregister(this);
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public String toString() {
            return "Subscription[" + label + " -> " + type.getSimpleName() + ", " + priority + (active ? "" : ", cancelled") + "]";
        }
    }
}
