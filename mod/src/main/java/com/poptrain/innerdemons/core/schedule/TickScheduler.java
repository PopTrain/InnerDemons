package com.poptrain.innerdemons.core.schedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.condition.ConditionResult;
import com.poptrain.innerdemons.core.event.EventPriority;
import com.poptrain.innerdemons.core.event.Subscription;
import com.poptrain.innerdemons.core.event.SubscriptionGroup;
import com.poptrain.innerdemons.core.random.Rng;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TickScheduler<C> {

    public static final int DEFAULT_MAX_CHAINED = 256;

    private static final Logger LOGGER = LoggerFactory.getLogger(TickScheduler.class);

    private static final long NEVER = Long.MAX_VALUE;

    private static final Comparator<Entry<?>> ORDER = Comparator.<Entry<?>>comparingLong(e -> e.wake)
            .thenComparingInt(e -> e.priority.ordinal())
            .thenComparingLong(e -> e.seq);

    private final String name;
    private final C owner;
    private final boolean threadConfined;
    private final int maxChained;
    private final TaskErrorHandler errorHandler;
    private final Rng rng;
    private final TaskRegistry<C> registry;
    private final Condition<? super C> aliveWhile;
    private final PriorityQueue<Entry<C>> queue = new PriorityQueue<>(ORDER);
    private final SubscriptionGroup lifetime = new SubscriptionGroup();
    private Entry<C> running;
    private long clock;
    private long nextSeq;
    private int chainedThisTick;
    private boolean ticking;
    private boolean paused;
    private boolean closed;
    private Thread ownerThread;

    private TickScheduler(Builder<C> builder) {
        this.name = builder.name;
        this.owner = builder.owner;
        this.threadConfined = builder.threadConfined;
        this.maxChained = builder.maxChained;
        this.errorHandler = builder.errorHandler;
        this.rng = builder.rng;
        this.registry = builder.registry;
        this.aliveWhile = builder.aliveWhile;
    }

    public static <C> TickScheduler<C> create(String name, C owner) {
        return builder(name, owner).build();
    }

    public static <C> Builder<C> builder(String name, C owner) {
        return new Builder<>(name, owner);
    }

    public String name() {
        return name;
    }

    public C owner() {
        return owner;
    }

    public long clock() {
        return clock;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isTicking() {
        return ticking;
    }

    public boolean hasRng() {
        return rng != null;
    }

    public Rng rng() {
        if (rng == null) {
            throw new IllegalStateException(name + ": no Rng configured. Pass one to TickScheduler.builder(...).rng(...)");
        }
        return rng;
    }

    public Optional<TaskRegistry<C>> registry() {
        return Optional.ofNullable(registry);
    }

    public void pause() {
        checkThread();
        paused = true;
    }

    public void resume() {
        checkThread();
        paused = false;
    }

    public void tick() {
        checkThread();
        if (ticking) {
            throw new IllegalStateException(name + ": tick() called from inside a scheduled task");
        }
        if (closed || paused) {
            return;
        }
        if (aliveWhile != null && !ownerAlive()) {
            LOGGER.debug("[{}] Owner {} failed {}, closing scheduler", name, owner, aliveWhile.describe());
            close();
            return;
        }
        clock++;
        ticking = true;
        chainedThisTick = 0;
        boolean warned = false;
        try {
            Entry<C> entry;
            while (!closed && (entry = queue.peek()) != null && entry.wake <= clock) {
                queue.poll();
                if (!entry.active) {
                    continue;
                }
                if (entry.chainedAt == clock && ++chainedThisTick > maxChained) {
                    if (!warned) {
                        LOGGER.error("[{}] More than {} tasks were chained with delay 0 in one tick; deferring the rest to the next tick. This is almost always a task rescheduling itself in a loop.",
                                name, maxChained);
                        warned = true;
                    }
                    entry.chainedAt = -1;
                    entry.due = clock + 1;
                    enqueue(entry);
                    continue;
                }
                process(entry);
            }
        } finally {
            ticking = false;
            running = null;
        }
    }

    public ScheduledTask after(int delay, Runnable task) {
        return task().delay(delay).run(task);
    }

    public ScheduledTask after(int delay, TickTask<? super C> task) {
        return task().delay(delay).run(task);
    }

    public ScheduledTask every(int period, Runnable task) {
        return task().delay(period).every(period).run(task);
    }

    public ScheduledTask every(int period, TickTask<? super C> task) {
        return task().delay(period).every(period).run(task);
    }

    public ScheduledTask waitUntil(Condition<? super C> condition, Runnable task) {
        return task().when(condition).run(task);
    }

    public ScheduledTask waitUntil(Condition<? super C> condition, TickTask<? super C> task) {
        return task().when(condition).run(task);
    }

    public TaskBuilder<C> task() {
        return new TaskBuilder<>(this);
    }

    public KeyedTaskBuilder<C> task(TaskKey<? super C> key) {
        return new KeyedTaskBuilder<>(this, Objects.requireNonNull(key, "key"));
    }

    public Optional<ScheduledTask> find(TaskKey<?> key) {
        checkThread();
        Entry<C> best = null;
        for (Entry<C> e : activeEntries()) {
            if (e.key == key && (best == null || ORDER.compare(e, best) < 0)) {
                best = e;
            }
        }
        return Optional.ofNullable(best);
    }

    public boolean isScheduled(TaskKey<?> key) {
        return find(key).isPresent();
    }

    public int remainingTicks(TaskKey<?> key) {
        return find(key).map(ScheduledTask::remainingTicks).orElse(0);
    }

    public int cancel(TaskKey<?> key) {
        checkThread();
        int count = 0;
        for (Entry<C> e : activeEntries()) {
            if (e.key == key) {
                e.cancel();
                count++;
            }
        }
        return count;
    }

    public List<ScheduledTask> tasks() {
        checkThread();
        List<Entry<C>> entries = activeEntries();
        entries.sort(ORDER);
        return List.copyOf(entries);
    }

    public int size() {
        return queue.size() + (running != null && running.active ? 1 : 0);
    }

    public <T extends Subscription> T bind(T subscription) {
        Objects.requireNonNull(subscription, "subscription");
        checkThread();
        return lifetime.add(subscription);
    }

    public void cancelAll() {
        checkThread();
        for (Entry<C> e : queue) {
            e.active = false;
        }
        queue.clear();
        if (running != null) {
            running.active = false;
        }
    }

    public void close() {
        checkThread();
        if (closed) {
            return;
        }
        closed = true;
        cancelAll();
        lifetime.cancel();
    }

    public SchedulerSnapshot snapshot() {
        checkThread();
        List<TaskSnapshot> saved = new ArrayList<>();
        List<Entry<C>> entries = activeEntries();
        entries.sort(ORDER);
        for (Entry<C> e : entries) {
            if (e.key == null) {
                continue;
            }
            saved.add(new TaskSnapshot(e.key.name(), e.name, e.remainingTicks(),
                    e.deadline == NEVER ? ScheduledTask.UNLIMITED : (int) Math.max(1, e.deadline - clock),
                    e.periodMin, e.periodMax, e.runsLeft, e.runCount, e.priority.name()));
        }
        return new SchedulerSnapshot(clock, saved);
    }

    public boolean restore(SchedulerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        checkThread();
        ensureOpen();
        if (ticking) {
            throw new IllegalStateException(name + ": restore() called from inside a scheduled task");
        }
        List<Entry<C>> kept = new ArrayList<>();
        for (Entry<C> e : queue) {
            if (e.key == null) {
                kept.add(e);
            } else {
                e.active = false;
            }
        }
        queue.clear();
        long delta = snapshot.clock() - clock;
        clock = snapshot.clock();
        for (Entry<C> e : kept) {
            e.due += delta;
            if (e.deadline != NEVER) {
                e.deadline += delta;
            }
            enqueue(e);
        }
        boolean complete = true;
        for (TaskSnapshot saved : snapshot.tasks()) {
            Entry<C> e = restoreEntry(saved);
            if (e == null) {
                complete = false;
            } else {
                enqueue(e);
            }
        }
        return complete;
    }

    ScheduledTask submit(TaskBuilder<C> b, TickTask<? super C> task) {
        checkThread();
        ensureOpen();
        validateTiming(b, null);
        Entry<C> e = new Entry<>(this, b.name != null ? b.name : "task#" + nextSeq, null, task, b.when, b.until,
                b.onTimeout, b.priority, nextSeq++);
        return schedule(e, b);
    }

    ScheduledTask submit(KeyedTaskBuilder<C> b) {
        checkThread();
        ensureOpen();
        TaskKey<? super C> key = b.key;
        validateTiming(b, key);
        if (registry == null || !registry.contains(key)) {
            throw new IllegalStateException(name + ": task key '" + key.name()
                    + "' is not in this scheduler's TaskRegistry, so it could not be saved. Add it to the registry passed to the builder.");
        }
        if (b.mode == KeyedTaskBuilder.Mode.IF_ABSENT) {
            Optional<ScheduledTask> existing = find(key);
            if (existing.isPresent()) {
                return existing.get();
            }
        } else if (b.mode == KeyedTaskBuilder.Mode.REPLACE) {
            cancel(key);
        }
        return schedule(keyed(key, b.name, b.priority, nextSeq++), b);
    }

    private Entry<C> keyed(TaskKey<? super C> key, String taskName, EventPriority priority, long seq) {
        return new Entry<>(this, taskName != null ? taskName : key.name(), key, key.task(), key.when(), key.until(),
                key.onTimeout(), priority, seq);
    }

    private ScheduledTask schedule(Entry<C> e, AbstractTaskBuilder<C, ?> b) {
        e.periodMin = b.periodMin;
        e.periodMax = b.periodMax;
        e.runsLeft = b.periodMax == 0 ? 1 : b.times;
        int delay = b.delayMin == b.delayMax ? b.delayMin : rng().range(b.delayMin, b.delayMax);
        if (delay == 0 && ticking) {
            e.due = clock;
            e.chainedAt = clock;
        } else {
            e.due = clock + Math.max(1, delay);
        }
        e.deadline = b.timeout > 0 ? clock + b.timeout : NEVER;
        enqueue(e);
        if (b.group != null) {
            b.group.add(e);
        }
        return e;
    }

    private void validateTiming(AbstractTaskBuilder<C, ?> b, TaskKey<?> key) {
        String label = key != null ? key.name() : b.name != null ? b.name : "task";
        if (b.times > 1 && b.periodMax == 0) {
            throw new IllegalArgumentException(name + ": " + label + " uses times(" + b.times
                    + ") but has no every(...) or everyBetween(...)");
        }
        if ((b.delayMin != b.delayMax || b.periodMin != b.periodMax) && rng == null) {
            throw new IllegalStateException(name + ": " + label
                    + " uses a random delay or period, but no Rng was given to TickScheduler.builder(...).rng(...)");
        }
    }

    private Entry<C> restoreEntry(TaskSnapshot saved) {
        TaskKey<? super C> key = registry == null ? null : registry.find(saved.key()).orElse(null);
        if (key == null) {
            LOGGER.warn("[{}] Dropping saved task '{}': no task key with that name is registered", name, saved.key());
            return null;
        }
        boolean badPeriod = saved.periodMin() < 0 || saved.periodMax() < saved.periodMin()
                || (saved.periodMax() == 0 && saved.runsLeft() != 1);
        if (badPeriod || saved.runsLeft() == 0 || saved.runsLeft() < ScheduledTask.UNLIMITED) {
            LOGGER.warn("[{}] Dropping saved task '{}': invalid timing {}", name, saved.key(), saved);
            return null;
        }
        if (saved.periodMin() != saved.periodMax() && rng == null) {
            LOGGER.warn("[{}] Dropping saved task '{}': it has a random period but this scheduler has no Rng", name, saved.key());
            return null;
        }
        EventPriority priority;
        try {
            priority = EventPriority.valueOf(saved.priority());
        } catch (IllegalArgumentException e) {
            priority = EventPriority.NORMAL;
        }
        Entry<C> e = keyed(key, saved.name(), priority, nextSeq++);
        e.periodMin = saved.periodMin();
        e.periodMax = saved.periodMax();
        e.runsLeft = saved.runsLeft();
        e.runCount = Math.max(0, saved.runCount());
        e.due = clock + Math.max(1, saved.remaining());
        e.deadline = saved.timeout() > 0 ? clock + saved.timeout() : NEVER;
        return e;
    }

    private void process(Entry<C> e) {
        running = e;
        try {
            boolean waiting = false;
            if (e.due <= clock) {
                if (e.until != null && passes(e, "until", e.until)) {
                    e.active = false;
                    return;
                }
                if (e.when != null) {
                    String blocked = blockedBy(e);
                    if (blocked != null) {
                        e.blockedBy = blocked;
                        waiting = true;
                    }
                }
                if (!waiting) {
                    e.blockedBy = null;
                    e.runCount++;
                    if (e.runsLeft > 0) {
                        e.runsLeft--;
                    }
                    invoke(e, "run", e.task);
                    if (!e.active) {
                        return;
                    }
                    if (e.runsLeft == 0) {
                        e.active = false;
                        return;
                    }
                    e.due = clock + e.nextPeriod();
                }
            }
            if (clock >= e.deadline) {
                e.active = false;
                if (e.onTimeout != null) {
                    invoke(e, "timeout", e.onTimeout);
                }
                return;
            }
            if (waiting) {
                e.due = clock + 1;
            }
            enqueue(e);
        } finally {
            running = null;
        }
    }

    private void invoke(Entry<C> e, String phase, TickTask<? super C> task) {
        try {
            task.run(e.context);
        } catch (RuntimeException error) {
            report(e.name, phase, error);
        }
    }

    private boolean passes(Entry<C> e, String phase, Condition<? super C> condition) {
        try {
            return condition.test(owner);
        } catch (RuntimeException error) {
            report(e.name, phase + " " + condition.describe(), error);
            return false;
        }
    }

    private String blockedBy(Entry<C> e) {
        try {
            ConditionResult result = e.when.evaluate(owner);
            return result.passed() ? null : result.reason();
        } catch (RuntimeException error) {
            report(e.name, "when " + e.when.describe(), error);
            return e.when.describe() + " (threw " + error.getClass().getSimpleName() + ")";
        }
    }

    private boolean ownerAlive() {
        try {
            return aliveWhile.test(owner);
        } catch (RuntimeException error) {
            report("aliveWhile", aliveWhile.describe(), error);
            return false;
        }
    }

    private void report(String task, String phase, RuntimeException error) {
        try {
            errorHandler.onTaskError(this, task, phase, error);
        } catch (RuntimeException nested) {
            LOGGER.error("[{}] Task error handler itself failed", name, nested);
        }
    }

    private void enqueue(Entry<C> e) {
        e.wake = Math.min(e.due, e.deadline);
        queue.add(e);
    }

    private List<Entry<C>> activeEntries() {
        List<Entry<C>> out = new ArrayList<>(queue.size() + 1);
        if (running != null && running.active) {
            out.add(running);
        }
        for (Entry<C> e : queue) {
            if (e.active) {
                out.add(e);
            }
        }
        return out;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Tick scheduler " + name + " is closed");
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
            throw new IllegalStateException("Tick scheduler " + name + " owned by thread " + ownerThread.getName()
                    + " was accessed from " + current.getName());
        }
    }

    private static void logError(TickScheduler<?> scheduler, String task, String phase, RuntimeException error) {
        LOGGER.error("[{}] Task {} failed during {} for {}", scheduler.name, task, phase, scheduler.owner, error);
    }

    @Override
    public String toString() {
        return "TickScheduler[" + name + ", tick " + clock + ", " + size() + " tasks"
                + (paused ? ", paused" : "") + (closed ? ", closed" : "") + "]";
    }

    public static final class Builder<C> {

        private final String name;
        private final C owner;
        private boolean threadConfined = true;
        private int maxChained = DEFAULT_MAX_CHAINED;
        private TaskErrorHandler errorHandler = TickScheduler::logError;
        private Rng rng;
        private TaskRegistry<C> registry;
        private Condition<? super C> aliveWhile;

        private Builder(String name, C owner) {
            this.name = Objects.requireNonNull(name, "name");
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        public Builder<C> threadConfined(boolean value) {
            this.threadConfined = value;
            return this;
        }

        public Builder<C> maxChained(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("maxChained must be at least 1");
            }
            this.maxChained = value;
            return this;
        }

        public Builder<C> errorHandler(TaskErrorHandler handler) {
            this.errorHandler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        public Builder<C> rng(Rng value) {
            this.rng = Objects.requireNonNull(value, "rng");
            return this;
        }

        public Builder<C> registry(TaskRegistry<C> value) {
            this.registry = Objects.requireNonNull(value, "registry");
            return this;
        }

        public Builder<C> aliveWhile(Condition<? super C> condition) {
            this.aliveWhile = Objects.requireNonNull(condition, "condition");
            return this;
        }

        public TickScheduler<C> build() {
            return new TickScheduler<>(this);
        }
    }

    private static final class Entry<C> implements ScheduledTask {

        private final TickScheduler<C> scheduler;
        private final String name;
        private final TaskKey<? super C> key;
        private final TickTask<? super C> task;
        private final Condition<? super C> when;
        private final Condition<? super C> until;
        private final TickTask<? super C> onTimeout;
        private final EventPriority priority;
        private final long seq;
        private final TaskContext<C> context;
        private long due;
        private long deadline = NEVER;
        private long wake;
        private long chainedAt = -1;
        private int periodMin;
        private int periodMax;
        private int runsLeft = 1;
        private int runCount;
        private String blockedBy;
        private boolean active = true;

        private Entry(TickScheduler<C> scheduler, String name, TaskKey<? super C> key, TickTask<? super C> task,
                      Condition<? super C> when, Condition<? super C> until, TickTask<? super C> onTimeout,
                      EventPriority priority, long seq) {
            this.scheduler = scheduler;
            this.name = name;
            this.key = key;
            this.task = task;
            this.when = when;
            this.until = until;
            this.onTimeout = onTimeout;
            this.priority = priority;
            this.seq = seq;
            this.context = new TaskContext<>(scheduler, this);
        }

        private int nextPeriod() {
            return periodMin == periodMax ? periodMin : scheduler.rng().range(periodMin, periodMax);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Optional<TaskKey<?>> key() {
            return Optional.ofNullable(key);
        }

        @Override
        public int runCount() {
            return runCount;
        }

        @Override
        public int runsLeft() {
            return runsLeft;
        }

        @Override
        public int remainingTicks() {
            if (!active) {
                return 0;
            }
            return (int) Math.max(1, Math.min(Integer.MAX_VALUE, due - scheduler.clock));
        }

        @Override
        public boolean isRepeating() {
            return periodMax > 0;
        }

        @Override
        public Optional<String> blockedBy() {
            return Optional.ofNullable(blockedBy);
        }

        @Override
        public void cancel() {
            if (!active) {
                return;
            }
            scheduler.checkThread();
            active = false;
            if (scheduler.running != this) {
                scheduler.queue.remove(this);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public String toString() {
            return "ScheduledTask[" + name + ", due " + due + (isRepeating() ? ", repeating" : "")
                    + (blockedBy != null ? ", blocked by " + blockedBy : "") + (active ? "" : ", done") + "]";
        }
    }
}
