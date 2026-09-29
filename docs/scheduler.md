# Tick Scheduler

Package: `com.poptrain.innerdemons.core.schedule`

An owner-scoped scheduler for "do this in N ticks", "do this every N ticks" and "do this once X is true". It has no Minecraft dependency, except for `TickSchedulerNbt`. Each owner (a demon, a battle, a trainer, the server) gets its own `TickScheduler`, with its own clock. When the owner stops ticking, its tasks stop too. When the owner is removed, one `close()` cancels everything it scheduled.

It follows the same rules as the other core systems: handles are `Subscription`s, gates are `Condition`s, random timing comes from a seeded `Rng`, priorities are `EventPriority`, and errors, loops and threads are guarded the same way.

## Pieces

| Type | Role |
|---|---|
| `TickScheduler<C>` | One per owner. Holds the clock and the task queue. Call `tick()` once per owner tick. |
| `ScheduledTask` | Handle for a scheduled task. Extends `Subscription`, so `cancel()`, `ctx.bind(...)` and `SubscriptionGroup` all work. |
| `TickTask<C>` | The task body: `ctx -> ...`. Plain `Runnable`s are accepted everywhere too. |
| `TaskContext<C>` | Passed to a running task: `owner()`, `scheduler()`, `task()`, `runCount()`, `isFinalRun()`, `tick()`, `rng()`, `cancel()`. |
| `TaskBuilder<C>` | Returned by `scheduler.task()`. Sets timing, conditions, timeout, priority, name and group. |
| `TaskKey<C>` | A named, `static final` task definition. Keyed tasks can be found, deduplicated and saved. |
| `TaskRegistry<C>` | The set of keys a scheduler may save and load. |
| `KeyedTaskBuilder<C>` | Returned by `scheduler.task(key)`. Timing only, plus `ifAbsent()` / `replacing()`. |
| `TaskConditions` | `scheduled(...)`, `notScheduled(...)`, `paused(...)` as `Condition`s, mainly for cooldown guards. |
| `SchedulerSnapshot` / `TaskSnapshot` / `TickSchedulerNbt` | Save and load keyed tasks and the clock. |
| `TaskErrorHandler` | Receives task and condition exceptions. It logs them by default. |

State machine bridge, in `core.statemachine`:

| Type | Role |
|---|---|
| `StateTasks` | `after`, `every`, `requestAfter`, `fireAfter`: tasks bound to a `StateContext`, cancelled when the state exits. |

## Creating one

```java
public static final TaskRegistry<DemonEntity> TASKS = TaskRegistry.of(DemonTasks.REGEN, DemonTasks.BITE_COOLDOWN);

scheduler = TickScheduler.builder("demon#" + getId(), this)
        .rng(rng.derive("scheduler"))
        .registry(TASKS)
        .aliveWhile(Condition.of("alive", DemonEntity::isAlive))
        .build();
```

| Builder method | Effect |
|---|---|
| `rng(rng)` | Needed for `delayBetween` / `everyBetween`. Use a derived stream so scheduling never shifts other rolls. The owner saves that stream with `RngNbt` as usual. |
| `registry(registry)` | Keys this scheduler can run, save and load. Scheduling a key that isn't in it throws. |
| `aliveWhile(condition)` | Checked at the start of every `tick()`. If it fails, the scheduler closes itself. |
| `errorHandler(handler)` | Defaults to logging. |
| `maxChained(n)` | Loop cap for delay-0 chains in one tick. Defaults to 256. |
| `threadConfined(false)` | Turns off the thread check. |

`TickScheduler.create(name, owner)` gives one with all defaults.

## Scheduling

```java
scheduler.after(20, () -> playSound());
scheduler.every(40, ctx -> ctx.owner().heal(1));
scheduler.waitUntil(DemonConditions.HAS_TRAINER, ctx -> ctx.owner().greetTrainer());

scheduler.task()
        .delayBetween(60, 120)
        .everyBetween(60, 120)
        .named("blink")
        .run(ctx -> ctx.owner().blink());

scheduler.task()
        .when(DemonConditions.NEAR_TRAINER)
        .timeout(200)
        .onTimeout(ctx -> ctx.owner().teleportToTrainer())
        .run(ctx -> ctx.owner().sit());

scheduler.task()
        .every(10)
        .times(5)
        .until(DemonConditions.HEALTHY)
        .priority(EventPriority.HIGH)
        .run(ctx -> ctx.owner().heal(2));
```

| Builder method | Effect |
|---|---|
| `delay(n)` | First run `n` ticks from now. `0` means as soon as possible (see timing below). Defaults to 0. |
| `delayBetween(min, max)` | Random first delay, drawn once from the scheduler's `Rng`. |
| `every(n)` | Repeat every `n` ticks, counted from the previous run. |
| `everyBetween(min, max)` | Repeat with a new random gap each time. |
| `times(n)` | Stop after `n` runs. Needs `every(...)`. |
| `when(condition)` | When due, only run if the condition passes. Otherwise, retry every tick. `blockedBy()` shows the failing check. Calling it more than once combines with AND. |
| `until(condition)` | When due, if the condition passes, the task ends without running. Calling it more than once combines with OR. |
| `timeout(n)` / `onTimeout(task)` | The task's maximum lifetime, counted from scheduling. If it hasn't finished by then, it ends and `onTimeout` runs. |
| `priority(p)` | Order among tasks due on the same tick. Defaults to `NORMAL`. |
| `named(name)` | Label used in error logs and `toString`. |
| `in(group)` | Adds the task to a `SubscriptionGroup`. |

`every(n)` on the scheduler is shorthand for `task().delay(n).every(n)`, so the first run is `n` ticks from now.

### Timing

- `tick()` advances the clock by one, then runs every task that is due.
- `delay(n)` with `n >= 1` runs on the `n`th `tick()` from now.
- `delay(0)` from outside a tick runs on the next `tick()`, the same as `delay(1)`.
- `delay(0)` from inside a running task runs later in the *same* tick, after the current task. This mirrors `EventBus.enqueue`.
- Tasks due on the same tick run by `priority`, then in the order they were scheduled. Repeating tasks keep their original place in that order.
- A repeating task's next run is counted from when it actually ran, so a `when(...)` that held it back pushes later runs back too.

## Keyed tasks

Lambdas can't be saved. A `TaskKey` is a named, static definition of a task's behaviour, so the scheduler can save "which key, how long is left" and rebuild it on load.

```java
public final class DemonTasks {

    public static final TaskKey<DemonEntity> REGEN = TaskKey.builder("regen", DemonEntity.class)
            .until(DemonConditions.FULL_HEALTH)
            .run(ctx -> ctx.owner().heal(1));

    public static final TaskKey<DemonEntity> BITE_COOLDOWN = TaskKey.marker("bite_cooldown", DemonEntity.class);

    public static final TaskKey<LivingEntity> BURNING = TaskKey.of("burning", LivingEntity.class,
            ctx -> ctx.owner().hurt(...));
}

scheduler.task(DemonTasks.REGEN).every(20).start();
scheduler.task(DemonTasks.BITE_COOLDOWN).delay(60).replacing().start();
scheduler.task(DemonTasks.REGEN).every(20).ifAbsent().start();

scheduler.isScheduled(DemonTasks.BITE_COOLDOWN);
scheduler.remainingTicks(DemonTasks.BITE_COOLDOWN);
scheduler.find(DemonTasks.REGEN).ifPresent(ScheduledTask::cancel);
scheduler.cancel(DemonTasks.REGEN);
```

- The key holds the behaviour: the task, `when`, `until` and `onTimeout`. The keyed builder only sets timing, priority, name and group. This keeps a saved task identical to the one that was scheduled.
- `marker(...)` is a key with an empty body. It's useful for cooldowns and "has X happened recently" flags.
- `ifAbsent()` returns the existing task if one with that key is already scheduled. `replacing()` cancels existing ones first. By default, duplicates are allowed.
- A key for a supertype works on a subtype's scheduler, so a `TaskKey<LivingEntity>` can go in a `TaskRegistry<DemonEntity>`.
- Registry names must be unique. `TaskRegistry.builder().include(other)` merges registries, such as a shared entity registry and a species-specific one.

## Saving and loading

```java
protected void addAdditionalSaveData(CompoundTag tag) {
    tag.put("Tasks", TickSchedulerNbt.save(scheduler));
}

protected void readAdditionalSaveData(CompoundTag tag) {
    TickSchedulerNbt.load(scheduler, tag.getCompound("Tasks"));
}
```

- Only keyed tasks are saved: the key name, remaining ticks, remaining timeout, period, runs left, run count, priority and name. The clock is saved too.
- On load, keyed tasks already in the scheduler are replaced by the saved ones. Lambda tasks already scheduled, for example from the constructor, are kept and keep their remaining delay.
- A saved key that isn't in the registry, or has invalid timing, is logged and dropped, and `load` returns `false`. The rest still load. `load` also returns `false`, and changes nothing, when the tag has no saved scheduler.
- As with state machines, anything a lambda task was doing is gone after a reload. Use keys for anything that must survive.

## Guard rails

- **Owner lifetime.** `close()` cancels every task, and cancels every subscription passed to `scheduler.bind(...)`. After that, `tick()` does nothing and scheduling throws. `aliveWhile(...)` closes it automatically.
- **Loop cap.** More than `maxChained` (256) delay-0 tasks chained in one tick is logged as a loop. The rest are deferred to the next tick instead of hanging the server.
- **No re-entrant ticking.** Calling `tick()` or `restore()` from inside a task throws. From inside a task, that exception goes to the error handler like any other task error.
- **Safe changes during a tick.** A task cancelled during a tick is skipped if it hasn't run yet. A task can cancel itself with `ctx.cancel()`. Tasks scheduled during a tick follow the timing rules above.
- **Task isolation.** An exception in a task, condition or `onTimeout` goes to the `TaskErrorHandler`. The rest of the tick still runs, and a repeating task keeps repeating. A condition that throws counts as failed: `when` keeps waiting, `until` doesn't end the task, and `aliveWhile` closes the scheduler.
- **Thread confinement.** A scheduler binds to the first thread that uses it and throws if another thread touches it.
- **Deterministic randomness.** Random delays and periods draw from the scheduler's own `Rng`. With a derived, saved stream, the same demon blinks on the same ticks after a reload.
- **Pause.** `pause()` freezes the clock, so every task keeps its remaining time. `resume()` continues. Use it for a demon stored in a party slot, or a battle waiting on the player.

## Using it with the state machine

Every `ScheduledTask` is a `Subscription`, so `ctx.bind(...)` ties a task to a state. `StateTasks` wraps the common cases:

```java
b.state(DemonState.GUARD).onEnter(ctx -> {
    StateTasks.every(ctx, ctx.owner().scheduler(), 20, () -> ctx.owner().lookAround());
    StateTasks.requestAfter(ctx, ctx.owner().scheduler(), 200, DemonState.IDLE);
});

b.state(DemonState.STUNNED).onEnter(ctx ->
        StateTasks.fireAfter(ctx, ctx.owner().scheduler(), 40, new StunEndedEvent()));

b.state(DemonState.ATTACK).onEnter(ctx ->
        ctx.bind(ctx.owner().scheduler().task().delay(8).named("strikeFrame").run(c -> c.owner().strike())));
```

- Bound tasks are cancelled when the state exits, together with its scoped data and other exit hooks.
- `requestAfter` and `fireAfter` go through `ctx.requestTransition` / `ctx.fire`, so stale-state protection still applies, and a switch that's already in progress queues them.
- For simple timeouts that don't need a task, `StateConditions.ticksAtLeast(n)` is still the lighter option.

### Cooldowns as guards

```java
public static final Condition<DemonEntity> BITE_READY =
        TaskConditions.notScheduled(DemonEntity::scheduler, DemonTasks.BITE_COOLDOWN);

b.transition(DemonState.IDLE, DemonState.BITE).guard(BITE_READY);
b.state(DemonState.BITE).onEnter(ctx ->
        ctx.owner().scheduler().task(DemonTasks.BITE_COOLDOWN).delay(60).replacing().start());
```

A blocked request reports `IDLE->BITE blocked by ready:bite_cooldown`.

### Tick order in the entity

```java
public void tick() {
    super.tick();
    if (!level().isClientSide) {
        scheduler.tick();
        brain.tick();
    }
}
```

Ticking the scheduler first means a task that requests a transition lands before the brain's auto transitions are checked. Either order works, as long as it stays the same.

## Using it with the event bus

- `scheduler.after(20, () -> bus.post(new DemonCalmedEvent(demon)))` delays an event.
- `scheduler.bind(bus.subscribe(...))` ties a subscription to the owner, so it's cancelled when the scheduler closes.
- A battle can own a scheduler next to its child bus. `Battle.end()` then calls `scheduler.close()` alongside `events.close()`. Field effects like "for 5 turns" can be a keyed task on the battle scheduler, or a subscription bound to a phase state.

## Guidelines

- One scheduler per owner. Don't share one across demons. Server-scoped schedulers follow the same lifetime advice as server buses: create them in `ServerStartingEvent`, close them in `ServerStoppedEvent`.
- Keep `TaskKey`s and `TaskRegistry`s in `static final` fields, like graphs and conditions.
- Use a keyed task for anything that must survive a save. Use a lambda for anything short-lived or rebuilt on load.
- Name long-lived lambda tasks with `named(...)` so error logs point to the right place.
- Keep conditions side-effect free. `when` is evaluated every tick while waiting.
