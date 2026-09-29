# Event Bus

Package: `com.poptrain.innerdemons.core.event`

A synchronous, typed publish/subscribe bus with no Minecraft dependency. Systems post plain Java objects, and listeners subscribe by class. The same bus type is meant to carry demon AI events today and battle, capture, evolution or quest events later, without any of those systems knowing about each other.

It works alongside NeoForge's `NeoForge.EVENT_BUS`; it doesn't replace it. NeoForge's bus is for hooking vanilla and loader events. This bus is for the mod's own gameplay events, where you want per-battle or per-demon scoping, deterministic order and a clean link to the state machine.

## Pieces

| Type | Role |
|---|---|
| `EventBus` | Holds subscriptions and dispatches events. Create one with `EventBus.create(name)` or `EventBus.builder(name)`. |
| `SubscriptionBuilder<E>` | Returned by `bus.listen(type)`. Sets the priority, filter, once, receiveCancelled, name and group. |
| `Subscription` | Handle returned by every subscribe call. `cancel()` removes the listener. |
| `SubscriptionGroup` | Collects subscriptions so they can all be cancelled together, for example when a battle ends or a demon is removed. |
| `EventPriority` | `HIGHEST`, `HIGH`, `NORMAL`, `LOW`, `LOWEST`, `MONITOR`. |
| `Cancellable` / `CancellableEvent` | Opt-in cancellation. Records can implement `Cancellable` only if they hold mutable state, so cancellable events are usually classes that extend `CancellableEvent`. |
| `ListenerErrorHandler` | Receives listener and filter exceptions. It logs them by default. |

State machine bridge, in `core.statemachine`:

| Type | Role |
|---|---|
| `StateEvents` | Forwards bus events into a machine, publishes machine transitions onto a bus, and adds state-scoped subscriptions. |
| `StateTransitionEvent<C, S>` | Posted for every transition by `StateEvents.publish`. It carries the machine, the `TransitionInfo`, and the exact states exited and entered. |
| `StateContext.addExitHook` / `bind` | Runs cleanup when a state exits. `bind(subscription)` cancels that subscription when the state exits. |

## Defining events

Any object can be an event, and records are the usual choice. Listeners subscribed to a supertype or interface also receive subtypes, so a sealed interface per system gives you a "listen to everything from this system" handle for free.

```java
public record DemonHurtEvent(DemonEntity demon, float amount, Entity attacker) {}

public final class DemonCaptureAttemptEvent extends CancellableEvent {
    private final DemonEntity demon;
    private final Player player;
    private float chance;
    ...
}
```

## Subscribing and posting

```java
EventBus bus = EventBus.create("demon");

bus.subscribe(DemonHurtEvent.class, e -> playFlinch(e.demon()));

bus.listen(DemonCaptureAttemptEvent.class)
        .priority(EventPriority.HIGH)
        .filter(DemonConditions.HEALTHY.adapt(DemonCaptureAttemptEvent::demon))
        .named("healthyResistsCapture")
        .subscribe(e -> e.setChance(e.chance() * 0.5f));

bus.listen(DemonCaptureAttemptEvent.class)
        .priority(EventPriority.MONITOR)
        .subscribe(e -> stats.recordAttempt(e, e.isCancelled()));

DemonCaptureAttemptEvent result = bus.post(new DemonCaptureAttemptEvent(demon, player, 0.4f));
if (!result.isCancelled()) {
    rollCapture(result.chance());
}
```

`post` returns the same event object, so mutable events work as "ask the listeners" calls: a damage calculation, a capture chance or a list of drops.

### Dispatch order

1. Every listener whose type is the event's class, a superclass or an interface is collected.
2. They run by priority from `HIGHEST` to `MONITOR`. Listeners with the same priority run in the order they subscribed, across all types. The merged order is cached per event class and rebuilt only when subscriptions change.
3. After a `Cancellable` event is cancelled, the remaining listeners are skipped unless they used `receiveCancelled()`. `MONITOR` listeners always receive the event. Treat `MONITOR` as observe-only: logging, stats, debug overlays.
4. If the bus has a parent, the event is then posted to the parent. See [Child buses](#child-buses).

### Subscription options

| Builder method | Effect |
|---|---|
| `priority(p)` | Dispatch slot. Defaults to `NORMAL`. |
| `filter(Condition<? super E>)` / `filter(name, predicate)` | The listener runs only if the condition passes. Calling it more than once combines the filters with AND. It uses the same `Condition` type as the state machine and [conditions.md](conditions.md). |
| `once()` | Cancels itself right before the first delivery that passes the filter. |
| `receiveCancelled()` | Still runs after the event was cancelled. |
| `named(name)` | Label used in error logs. Worth setting for anything long-lived. |
| `in(group)` | Adds the subscription to a `SubscriptionGroup`. |

## Guard rails

These mirror the state machine's guarantees.

- **Nested vs queued posting.** `post` from inside a listener dispatches straight away, nested inside the current event. `enqueue` from inside a listener waits until the outermost `post` on that bus finishes, then runs in FIFO order. If the bus is idle, `enqueue` posts immediately. Use `enqueue` for "this happened as a result" follow-ups, such as a faint after damage, so every listener sees the cause before the effect.
- **Loop caps.** More than `maxDepth` (16) nested posts, or more than `maxQueued` (256) chained queued events, is logged as a loop and dropped instead of hanging the server. Both limits can be set on the builder.
- **Safe changes during dispatch.** A listener cancelled during a dispatch is skipped if it hasn't run yet. A listener added during a dispatch starts with the next event.
- **Listener isolation.** An exception in a listener or filter goes to the `ListenerErrorHandler`, and the rest of the listeners still run.
- **Thread confinement.** A bus binds to the first thread that uses it and throws if another thread touches it. Turn this off with `threadConfined(false)`. That only removes the check; it doesn't make the bus safe to use from two threads at once.
- **Closing.** `close()` cancels every subscription and closes child buses. After that, `post`/`enqueue` are silently ignored, and subscribing throws.

## Child buses

`bus.child(name)` creates a bus with its own subscriptions. Events posted on the child go to the child's listeners first, then to the parent's listeners. Events posted on the parent never reach children.

```java
EventBus world = EventBus.create("world");
EventBus battle = world.child("battle#12");

world.subscribe(BattleEvent.class, e -> achievements.observe(e));
battle.subscribe(TurnStartedEvent.class, e -> ui.showTurn(e.turn()));

battle.post(new TurnStartedEvent(b, 1));
battle.close();
```

Priorities are ordered within each bus, not across the parent and child. The parent's `HIGHEST` still runs after the child's `LOWEST`. `hasListeners(type)` checks parents too, so you can skip building an expensive event nobody listens to.

## Using it with the state machine

### Bus events drive a machine

```java
StateEvents.forward(bus, DemonHurtEvent.class, brain);
StateEvents.forward(bus, DemonHurtEvent.class, brain, Condition.of("bigHit", e -> e.amount() > 4));
```

Each forwarded event is passed to `machine.fire(event)`, so it goes through `.on(Event.class, ...)` transitions and `onEvent` like any other fired event. Forwarding subscribes at `LOW` priority, so normal listeners can modify or cancel the event first. Cancelled events aren't forwarded. Forward specific types, not `Object.class`, or the machine will also receive its own `StateTransitionEvent`s.

### A machine publishes its transitions

```java
StateEvents.publish(brain, bus);

StateEvents.onTransition(bus, DemonBrain.GRAPH, e -> {
    if (e.didEnter(DemonState.COMBAT)) {
        e.owner().playBattleCry();
    }
});

bus.subscribe(StateTransitionEvent.TYPE, e -> debugOverlay.log(e));
```

`StateTransitionEvent` lists every state exited and entered in that switch, parents included. For example, IDLE → COMBAT reports `exited=[IDLE]` and `entered=[COMBAT, APPROACH]`. That makes "entered COMBAT from anywhere" a one-line check. Call `publish` before `start()` if you also want the START transition. `StateTransitionEvent.TYPE` is the class constant typed with wildcards, so `bus.subscribe` doesn't give you a raw type. `StateEvents.onTransition` filters by graph and gives you the fully typed event.

A bus listener that reacts to a transition runs while the machine is still switching. Any `requestTransition` or `fire` it makes is queued (`QUEUED`) and runs once the switch is done, as with any other callback.

### Both at once

```java
SubscriptionGroup link = StateEvents.connect(brain, bus, DemonHurtEvent.class, DemonCommandEvent.class);
link.cancel();
```

### Subscriptions scoped to a state

A subscription made while a state is active can be tied to that state, so it's cancelled automatically when the state exits. This uses the same lifetime rules as scoped data.

```java
b.state(DemonState.GUARD).onEnter(ctx -> {
    ctx.bind(bus.subscribe(AllyHurtEvent.class, e -> ctx.owner().retaliate(e.attacker())));
    StateEvents.fireWhileActive(ctx, bus, TrainerRecallEvent.class);
});
```

- `ctx.bind(subscription)` cancels the subscription when the state exits and returns it.
- `ctx.addExitHook(runnable)` is the general version. Hooks run after the state's own `onExit`, in reverse order of registration, before its scoped data is cleared. Each hook gets the same callback isolation as other callbacks: errors go to `onCallbackError` with phase `exitHook`.
- `StateEvents.subscribe(ctx, bus, type, listener)` is shorthand for `ctx.bind(bus.subscribe(...))`.
- `StateEvents.fireWhileActive(ctx, bus, type)` fires matching events into the machine through `ctx.fire`, so only while that state is active. Stale-requester protection applies as usual.

Calling `bind` or `addExitHook` on a context whose state has already exited throws, just like reading its scoped data.

## Where this goes next: battles (not built yet)

The bus is shaped so a battle system can plug in without changes to the core.

```java
public sealed interface BattleEvent permits TurnStartedEvent, MoveSelectedEvent, MoveUsedEvent,
        DamageCalculationEvent, DemonFaintedEvent, BattleEndedEvent {
    Battle battle();
}

public final class Battle {
    private final EventBus events;
    private final BattleMachine machine;
    private final SubscriptionGroup lifetime = new SubscriptionGroup();

    public Battle(EventBus worldBus, ...) {
        events = worldBus.child("battle#" + id);
        machine = new BattleMachine(this);
        lifetime.add(StateEvents.connect(machine, events, MoveSelectedEvent.class, DemonFaintedEvent.class));
        participants.forEach(p -> p.abilities().forEach(a -> a.register(events, lifetime)));
    }

    public void end() {
        machine.stop();
        lifetime.cancel();
        events.close();
    }
}
```

- **One child bus per battle.** Battle listeners are removed in one call, and the world bus still sees every battle for achievements, stats and quests.
- **Abilities, held items and field effects are listeners.** For example, an ability that halves fire damage subscribes to `DamageCalculationEvent` with a filter. Priorities give a deterministic stacking order.
- **The battle state machine drives the flow.** It goes through turn phases like `SELECT → RESOLVE → END_OF_TURN`, reacts to forwarded events, and its transitions are published for UI, sound and animation.
- **Cause and effect.** Damage is `post`ed so modifiers can change it. The faint that results is `enqueue`d, so it resolves after the whole damage event has finished.
- **Status effects scoped to a phase.** For example, "Until end of turn" is a subscription bound to the `RESOLVE` state's context.

## Guidelines

- Keep events as small, immutable records unless listeners need to change them or cancel them.
- One bus per scope: a world or server bus, a child per battle, and optionally one per demon for its brain. Don't share a single bus between the logical client and server. A `static` bus that outlives an integrated server restart will hit the thread check, so create server-scoped buses in `ServerStartingEvent` and close them in `ServerStoppedEvent`.
- Give long-lived subscriptions a `named(...)` label so error logs point to the right place.
- Anything that owns subscriptions should keep them in a `SubscriptionGroup`, or bind them to a `StateContext`, and cancel them when it's done.
