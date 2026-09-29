# Core State Machine

Package: `com.poptrain.innerdemons.core.statemachine`

A hierarchical state machine with no Minecraft dependency, except for `StateMachineNbt`. You describe a system once as an immutable `StateGraph`. Each entity or battle then gets its own lightweight `StateMachine` instance that runs on that graph.

## Pieces

| Type | Role |
|---|---|
| `StateGraph<C, S>` | Immutable definition: states, substates, transitions, guards. Build once, store in a `static final` field, share across all instances. |
| `StateMachine<C, S>` | Runtime instance per owner. Subclass it for each system (battle, demon AI, ...). |
| `StateBehavior<C, S>` | Optional class-based state logic (`onEnter/onExit/onTick/onEvent/canEnter/canExit`). Lambdas on the builder work too. |
| `StateContext<C, S>` | Handle given to a state while it is active: owner, ticks in state, scoped data, `requestTransition`, `fire`. |
| `StateDataKey<T>` | Typed key for per-state scratch data that is wiped when the state exits. |
| `StateSnapshot` / `StateMachineNbt` | Save and load the active path, ticks and history. |
| `StateConditions` | Ready-made `Condition`s over a `StateContext` (ticks, scoped data, active states, owner lift). |
| `StateEvents` / `StateTransitionEvent` | Bridge to the event bus: forward bus events into a machine, publish transitions, state-scoped subscriptions. |

`C` is the owner/context type (e.g. `DemonEntity`, `BattleContext`). `S` is the state key, usually an enum.

## Transition kinds

```java
b.transition(IDLE, WANDER);                                    // request: machine.requestTransition(WANDER)
b.transition(IDLE, FLEE).on(HurtEvent.class, (ctx, e) -> e.amount() > 4); // event: machine.fire(new HurtEvent(...))
b.transition(WANDER, IDLE).when(ctx -> ctx.ticksInState() > 200);       // auto: checked every tick()
b.anyTransition(DEAD).on(DeathEvent.class);                    // global: valid from any state
```

Transitions declared on a parent state also apply to every substate below it. The most specific (deepest) match wins, then the one with the highest `priority(...)`, then the one declared first.

## Guard rails

- **Whitelist only.** A switch happens only if a matching transition was declared. Anything else returns `NO_SUCH_TRANSITION`. `forceState` is the explicit escape hatch for commands and debugging.
- **All-or-nothing checks.** Transition guards, then `canExit` on every state being left, then `canEnter` on every state being entered, are all evaluated *before* anything changes. If one says no, nothing is exited or entered.
- **No re-entrant switching.** A request or event raised from inside `onEnter`, `onExit`, `onTick`, `onEvent`, a guard or an action is queued. It runs only after the current switch has fully finished.
- **Stale-state protection.** Each time a state is entered it gets a new `StateContext`. When the state exits, that context is dead. Requests from it return `STALE_REQUESTER`, and reading or writing its data throws. Delayed callbacks can't act on behalf of a state that's already gone.
- **Scoped data.** `ctx.put(key, value)` lives only as long as that state is active. `ctx.get(key)` also looks up through parent states, so substates can read shared data from their parent.
- **Loop cap.** More than `MAX_CHAINED_TRANSITIONS` (64) queued follow-ups in one call is logged as a loop and dropped instead of hanging the server.
- **Thread confinement.** A machine can only be used from the thread that started it. Turn this off with `threadConfined(false)`.
- **Callback isolation.** An exception in a callback goes to `onCallbackError` (logged by default). The machine still finishes the switch, so it never ends up half-transitioned.
- **Build-time validation.** Duplicate states, unknown parents or targets, parent cycles, composite states without `initial(...)`, `history()` on a leaf, or mixing `behavior(...)` with lambda hooks all fail at `build()`.

## Substates and history

```java
b.state(COMBAT).initial(APPROACH).history();
b.substate(COMBAT, APPROACH);
b.substate(COMBAT, ATTACK).initial(WINDUP);
b.substate(ATTACK, WINDUP);
b.substate(ATTACK, STRIKE);
```

Entering a composite state goes down through `initial(...)` substates until it reaches a leaf. With `history()`, re-entering a composite state resumes the direct child that was last active. A transition to an ancestor (or to the same state) exits and re-enters it, which resets it.

## Extending for a new system

```java
public final class DemonBrain extends StateMachine<DemonEntity, DemonState> {

    private static final StateGraph<DemonEntity, DemonState> GRAPH = build();

    public DemonBrain(DemonEntity demon) {
        super(GRAPH, demon);
    }

    private static StateGraph<DemonEntity, DemonState> build() {
        var b = StateGraph.builder("demon_brain", DemonEntity.class, DemonState.class);
        b.initial(DemonState.IDLE);
        b.state(DemonState.IDLE).onTick(ctx -> ctx.owner().lookAround());
        b.state(DemonState.WANDER).behavior(new WanderBehavior());
        b.state(DemonState.FLEE).canExit(ctx -> ctx.ticksInState() > 40);
        b.transition(DemonState.IDLE, DemonState.WANDER).when(ctx -> ctx.owner().getRandom().nextInt(200) == 0);
        b.transition(DemonState.WANDER, DemonState.IDLE).when(ctx -> ctx.ticksInState() > 300);
        b.anyTransition(DemonState.FLEE).on(HurtEvent.class, (ctx, e) -> ctx.owner().isCowardly());
        b.transition(DemonState.FLEE, DemonState.IDLE).when(ctx -> ctx.owner().isSafe());
        return b.build();
    }

    @Override
    protected void onTransitioned(TransitionInfo<DemonState> info) {
        owner().syncAnimationState(currentState());
    }
}
```

Wiring in the entity:

```java
brain = new DemonBrain(this);
brain.start();                                           // server side, e.g. first tick
brain.tick();                                            // in tick()/aiStep()
tag.put("Brain", StateMachineNbt.save(brain));           // addAdditionalSaveData
StateMachineNbt.load(brain, tag.getCompound("Brain"));   // readAdditionalSaveData
```

When a machine is restored, `onEnter` runs again with `info.isRestore() == true`, so states can rebuild anything that isn't saved. Scoped data isn't saved. If a saved path no longer fits the graph (for example, a state was renamed in an update), `restore` returns `false` and the machine starts from its initial state.

## Conditions

Guards, auto triggers, event filters, `canEnter` and `canExit` can all take a `Condition` from `core.condition`, not just a lambda. A failed condition reports which check failed in the rejection detail. See [conditions.md](conditions.md).

```java
b.transition(IDLE, HUNT).when(DemonConditions.NIGHT).guard(DemonConditions.HEALTHY);
b.state(FLEE).canExit(StateConditions.ticksAtLeast(40));
```

## Event bus

Machines connect to `core.event.EventBus` through `StateEvents`. Bus events can be forwarded into `fire`, transitions can be published as `StateTransitionEvent`s, and subscriptions can be tied to a state with `ctx.bind(...)` so they are cancelled when the state exits. `ctx.addExitHook(Runnable)` is the general cleanup hook behind it. See [event-bus.md](event-bus.md).

```java
StateEvents.connect(brain, bus, HurtEvent.class);
b.state(GUARD).onEnter(ctx -> ctx.bind(bus.subscribe(AllyHurtEvent.class, e -> ctx.owner().retaliate(e.attacker()))));
```

## Overridable hooks on `StateMachine`

`onStarted`, `onStopped`, `onStateEntered`, `onStateExited`, `onTransitioned`, `onTransitionRejected`, `onCallbackError`. Use `addListener(StateMachineListener)` for external observers such as debug overlays.
