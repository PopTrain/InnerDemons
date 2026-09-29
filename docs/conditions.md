# Conditions

Package: `com.poptrain.innerdemons.core.condition`

Conditions are reusable checks with names. Each one tests a context value of any type. The package has no Minecraft dependency and doesn't depend on the state machine, so the same conditions can also be used for evolution requirements, spawn rules, move usage, and so on. The state machine can use them as transition guards, auto-transition triggers, and `canEnter`/`canExit` checks.

## Pieces

| Type | Role |
|---|---|
| `Condition<T>` | A named check over a context `T`. `evaluate(T)` returns a `ConditionResult`, and `test(T)` returns a plain boolean. |
| `ConditionResult` | Whether the check passed. If it failed, it also gives the reason, which is the name of the check that failed. |
| `StateConditions` | State machine helpers that run on a `StateContext`, such as ticks in state, scoped data, or which states are active. `owner(...)` lifts an owner condition so it runs on the state context. |

`Condition` isn't a functional interface, so you can't write one as a bare lambda. Build it with `Condition.of(name, predicate)`, which gives every check a name. That name shows up in rejection messages and debug overlays.

## Building conditions

```java
public final class DemonConditions {

    public static final Condition<DemonEntity> HEALTHY =
            Condition.of("healthy", d -> d.getHealth() > d.getMaxHealth() * 0.5f);

    public static final Condition<DemonEntity> NIGHT =
            Condition.of("night", d -> d.level().isNight());

    public static final Condition<DemonEntity> HAS_TRAINER =
            Condition.of("hasTrainer", d -> d.getTrainer() != null);

    public static Condition<DemonEntity> bondAtLeast(int bond) {
        return Condition.of("bond>=" + bond, d -> d.getBond() >= bond);
    }
}
```

## Combining

```java
HEALTHY.and(NIGHT)                                // all(healthy, night)
HEALTHY.or(HAS_TRAINER)                           // any(healthy, hasTrainer)
NIGHT.negate()                                    // not night
Condition.allOf(HEALTHY, NIGHT, bondAtLeast(50))
Condition.anyOf(...)
Condition.noneOf(...)
Condition.atLeast(2, HEALTHY, NIGHT, HAS_TRAINER) // any 2 of the 3
HEALTHY.and(NIGHT).named("nightHunter")           // replaces the description
Condition.always() / Condition.never()
```

- `and`/`or` chains are flattened, so `a.and(b).and(c)` becomes `all(a, b, c)` and not a nested tree. `always()` is dropped from `all` and `never()` is dropped from `any`.
- `allOf` and `anyOf` short-circuit. `atLeast` stops as soon as the result is decided.
- Negating a negation gives back the original condition.
- The failure reason comes from the leaf check that failed. For example, `allOf(HEALTHY, NIGHT)` failing at night-time reports `night`. A `named(...)` wrapper adds its own name as a prefix, as in `nightHunter: night`.

## Using a condition on another type

`adapt` turns a `Condition<T>` into a `Condition<U>` by giving it a function that gets a `T` from a `U`:

```java
Condition<BattleContext> attackerHealthy = DemonConditions.HEALTHY.adapt(BattleContext::attacker);
```

A `Condition<LivingEntity>` also works anywhere a `Condition<? super DemonEntity>` is expected, so general entity checks can be shared across owner types.

## Pairing with the state machine

Every hook that used to take a lambda now also accepts a condition. Owner conditions run on `ctx.owner()`. Context conditions run on the `StateContext` of the state that declared the transition (or the current leaf state, for `anyTransition`).

```java
b.transition(IDLE, HUNT)
        .when(DemonConditions.NIGHT)                               // auto trigger, owner condition
        .guard(DemonConditions.HEALTHY)                            // extra guard, owner condition
        .guardContext(StateConditions.ticksAtLeast(100));         // guard on the StateContext

b.transition(WANDER, IDLE)
        .whenContext(StateConditions.ticksAtLeast(300));

b.anyTransition(FLEE)
        .on(HurtEvent.class, Condition.of("bigHit", (HurtEvent e) -> e.amount() > 4))
        .guard("coward", DemonConditions.HEALTHY.negate());        // custom guard name

b.state(EVOLVING)
        .canEnter(DemonConditions.bondAtLeast(80).and(DemonConditions.HAS_TRAINER));
b.state(FLEE)
        .canExit(StateConditions.ticksAtLeast(40));
```

| Builder method | Condition type | Evaluated on |
|---|---|---|
| `transition.guard(cond)` / `guard(name, cond)` | `Condition<? super C>` | owner |
| `transition.guardContext(cond)` / `guardContext(name, cond)` | `Condition<? super StateContext<C, S>>` | source state context |
| `transition.when(cond)` | `Condition<? super C>` | owner, makes the transition automatic |
| `transition.whenContext(cond)` | `Condition<? super StateContext<C, S>>` | source state context, makes the transition automatic |
| `transition.on(Event.class, cond)` | `Condition<? super E>` | the fired event |
| `state.canEnter(cond)` | `Condition<? super C>` | owner |
| `state.canExit(cond)` | `Condition<? super StateContext<C, S>>` | the exiting state's context |

Condition guards and lambda guards can be mixed on the same transition, and they run in the order they were declared. All the existing guarantees still hold: every guard is checked before anything exits or enters, and a condition that throws counts as a rejection and is sent to `onCallbackError`.

### `StateConditions`

```java
StateConditions.owner(DemonConditions.HEALTHY)                // owner condition as a context condition
StateConditions.ticksAtLeast(40)
StateConditions.ticksBelow(200)
StateConditions.machineIn(COMBAT)                             // any active state, including parents
StateConditions.has(TARGET)                                   // scoped data present (also checks parent states)
StateConditions.data(TARGET, "targetAlive", t -> t.isAlive()) // scoped data matches
```

Use `owner(...)` to mix owner and context checks in a single expression:

```java
.guardContext(StateConditions.<DemonEntity, DemonState>ticksAtLeast(20)
        .and(StateConditions.owner(DemonConditions.HEALTHY)))
```

## Debugging rejections

When a request or event is blocked by a condition guard, `onTransitionRejected` / `StateMachineListener.onTransitionRejected` receive the guard name and the leaf check that failed:

```
IDLE->HUNT blocked by healthy
IDLE->HUNT blocked by all(healthy, night) (night)
*->FLEE blocked by event:HurtEvent (bigHit)
IDLE->HUNT blocked by boom (threw IllegalStateException)
```

Automatic transitions are checked every tick and fail silently, as before.

## Guidelines

- Store shared conditions in `static final` fields, just like graphs. They're immutable and safe to share.
- Don't give conditions side effects or state that changes. Anything that needs to be remembered belongs in `StateContext` data or on the owner.
- Random checks, for example a 1-in-200 chance per tick, work but are evaluated exactly once per attempt. Build them with `RngConditions` from `core.random`, such as `RngConditions.oneIn(DemonEntity::rng, 200)`, so they're named `chance1/200` and draw from a seeded stream. See [random.md](random.md).
