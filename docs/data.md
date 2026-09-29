# Data Containers and Attachments

Package: `com.poptrain.innerdemons.core.data`

A typed key/value store plus typed attachments, with no Minecraft dependency except `DataNbt` and `NeoForgeData`. Every owner (a demon, a battle, a trainer, a party slot) gets one `DataContainer`. Plain values like bond, mood or nickname live next to attached components like the owner's scheduler, brain or RNG stream. The container saves all of them in one call and publishes changes onto the event bus. Its values can also be checked with `Condition`s.

It follows the same rules as the other core systems: keys are `static final`, handles are `Subscription`s, checks are `Condition`s, change events go through `EventBus`, and errors, loops and threads are guarded the same way.

## Pieces

| Type | Role |
|---|---|
| `DataKey<T>` | Typed, named key for a plain value: default, serializer, sanitizer, validator, copy rule. Subclass it through `DataKey.AbstractBuilder` for new key kinds. |
| `AttachmentKey<H, T>` | A `DataKey` for a component built per holder `H` by a factory. Created on first `get`, detached (hooks, `cancel`, `close`) when removed or when the container closes. |
| `Attachment<H>` | Optional interface for attachment values: `onAttached(holder)`, `onDetached(holder)`. |
| `DataContainer` | The store. One per owner. Not final: subclass it for a system-specific container with typed getters. |
| `DataHolder` | Interface for anything that owns a container: `data()` plus `get`, `find`, `has`, `set`, `update`. `DataContainer` implements it too. |
| `AbstractDataHolder` | Base class for non-entity systems (battles, parties, trainers). Builds its container with itself as owner. |
| `DataRegistry` | The keys a container may load by name, like `TaskRegistry`. |
| `DataSerializer<T>` / `DataSerializers` | Converts values to a neutral tree (numbers, strings, booleans, lists, maps, arrays) and back. Includes serializers for `RngState`, `SeededRng`, `StateSnapshot`, `SchedulerSnapshot`. |
| `DataSnapshot` / `DataNbt` | Save and load everything persistent. |
| `DataChangingEvent<T>` | Cancellable, mutable bus event posted *before* a plain value changes. Listeners can veto or rewrite it. |
| `DataChangedEvent<T>` | Record posted *after* any change: `SET`, `REMOVED`, `ATTACHED`, `DETACHED`, `COPIED`. |
| `DataEvents` | Key-filtered bus subscriptions: `onChanged`, `onChanging`, `onAttached`, `onDetached`. |
| `DataConditions` | `has`, `missing`, `value`, `equalTo`, `isTrue`, `atLeast`, `atMost`, `below`, `between` as `Condition<DataHolder>`. |
| `CoreAttachments` | Ready-made attachment keys for a `TickScheduler`, a `StateMachine`, a `SeededRng`, a `PityCounter`, an `EventBus`, a `SubscriptionGroup`. |
| `NeoForgeData` | Puts a `DataContainer` on vanilla holders (players, levels, chunks) through NeoForge's attachment system. |

State machine bridge, in `core.statemachine`:

| Type | Role |
|---|---|
| `StateData` | `setWhileActive`, `listenWhileActive`, `requestOnChange`, `fireChangesWhileActive`, `forwardChanges`. |

## Keys

```java
public final class DemonData {

    public static final DataKey<Integer> BOND = DataKey.<Integer>builder("bond", Integer.class)
            .defaultValue(0)
            .persistent(DataSerializers.INT)
            .sanitize(v -> Math.max(0, Math.min(100, v)))
            .build();

    public static final DataKey<Mood> MOOD =
            DataKey.persistent("mood", Mood.class, DataSerializers.enumOf(Mood.class), Mood.CALM);

    public static final DataKey<String> NICKNAME = DataKey.<String>builder("nickname", String.class)
            .persistent(DataSerializers.STRING)
            .validate("short", s -> s.length() <= 16)
            .build();

    public static final DataKey<List<String>> RIBBONS = DataKey.<List<String>>builder("ribbons", List.class)
            .persistent(DataSerializers.listOf(DataSerializers.STRING))
            .defaultValue(List::of)
            .build();

    public static final DataKey<Integer> DEFENSE_BONUS = DataKey.of("defense_bonus", Integer.class, 0);
}
```

| Builder method | Effect |
|---|---|
| `defaultValue(v)` / `defaultValue(supplier)` | What `get` returns when nothing is stored. The default is *not* stored. |
| `persistent(serializer)` | Saved by `snapshot()`/`DataNbt`. Without it, the key is transient. Persistent keys are also copied by `copyTo` unless you call `noCopy()`. |
| `sanitize(op)` | Runs on every write and load, e.g. clamping. Chained sanitizers run in order. |
| `validate(condition)` / `validate(name, predicate)` | A write that fails throws `IllegalArgumentException` naming the failed check. A saved value that fails is dropped on load. |
| `copy(op)` / `copyAsIs()` / `noCopy()` | What `copyTo` does with the value. |
| `inherited(false)` | Don't read this key through a parent container. |

Names must be lowercase `a-z 0-9 _ . : / -` and unique within a registry. Use one key object per name: a container refuses a second, different key with a name it already holds.

**Values should be immutable.** Records, enums, `List.of`, boxed numbers. `set` compares with `equals` and skips unchanged writes. If you store something mutable and change it in place, call `markDirty(key)`.

## Containers

```java
DataContainer data = DataContainer.builder("demon#" + getId())
        .owner(this)
        .registry(DemonData.REGISTRY)
        .bus(worldBus)
        .build();

data.get(BOND);                        // stored value, else parent's, else default
data.find(NICKNAME);                   // Optional, no default
data.set(BOND, 40);                    // true if the value changed
data.update(BOND, b -> b + 5);         // returns the new value
data.getOrCreate(RIBBONS);             // stores the default
data.remove(NICKNAME);
data.setIfAbsent(MOOD, Mood.CALM);
```

`set` returns `false` when the value is unchanged, a bus listener vetoed it, or a loop cap dropped it. It throws on `null` (use `remove`), on the wrong type, or on a failed validator.

### Child containers

`data.child(name, owner)` makes a container that reads through to its parent for any key it doesn't hold itself. Writes are always local. Use it for "species defaults, individual overrides":

```java
DataContainer species = DataContainer.create("species:imp");
species.set(BASE_SPEED, 7);

DataContainer imp = species.child("imp#12", impEntity);
imp.get(BASE_SPEED);      // 7, from the species
imp.set(BASE_SPEED, 9);   // only this imp
```

Attachments are never inherited. Listeners and bus links on the parent don't fire for the child's writes.

## Attachments

An attachment is a component that belongs to one holder and is built from it: a scheduler, a brain, an RNG stream, battle stats, a party's inventory. `get` creates it the first time and stores it. After that, `get` returns the same instance.

```java
public static final AttachmentKey<DemonEntity, TickScheduler<DemonEntity>> SCHEDULER =
        CoreAttachments.scheduler("scheduler", DemonEntity.class,
                d -> TickScheduler.builder("demon#" + d.getId(), d).registry(DemonTasks.REGISTRY).build());

public static final AttachmentKey<DemonEntity, DemonBrain> BRAIN =
        CoreAttachments.stateMachine("brain", DemonEntity.class, DemonBrain.class, DemonBrain::new);

public static final AttachmentKey<DemonEntity, SeededRng> RNG =
        CoreAttachments.rng("rng", DemonEntity.class, d -> Seeds.of(d.getUUID()));

public static final AttachmentKey<DemonEntity, PityCounter> VARIANT_PITY =
        CoreAttachments.pity("variant_pity", DemonEntity.class, PityRule.soft(1.0 / 4096, 2000, 1.0 / 2048));
```

Custom ones:

```java
public static final AttachmentKey<DemonEntity, BattleStats> STATS =
        AttachmentKey.<DemonEntity, BattleStats>builder("stats", DemonEntity.class, BattleStats.class)
                .factory(BattleStats::rollFor)
                .persistent(BattleStats.SERIALIZER)
                .onAttach((demon, stats) -> demon.refreshAttributes())
                .build();
```

| Builder method | Effect |
|---|---|
| `factory(holder -> value)` / `factory(supplier)` | How the component is built. Required. |
| `persistent(serializer)` | Save it, and build a fresh one from the saved tree on load. |
| `saved(save, loadInto)` / `savedAs(format, save, loadInto)` | Save it, but load *into* the existing instance. Other code keeps its reference. The core attachments use this with each system's own snapshot. |
| `onAttach(hook)` / `onDetach(hook)` | Run when the component is attached or detached. |
| `copyBySaving()` | `copyTo` gives the target holder its own copy by saving and loading. Without it, attachments aren't copied. |

When an attachment is detached (`remove`, replaced by `set`, or the container `close`s), the container:

1. runs `onDetach`,
2. calls `Attachment.onDetached` if the value implements it,
3. calls `cancel()` if the value is a `Subscription`, or `close()` if it is `AutoCloseable`.

`CoreAttachments` adds the right cleanup for each system: the scheduler is `close()`d, the state machine is `stop()`ped, the bus is `close()`d, and a `SubscriptionGroup` is cancelled. One `data.close()` when the owner is removed tears everything down.

An attachment needs an owner of the right type. `get` on a container with no owner, or an owner of another type, throws `DataException` naming both.

## Holders and inheritance

### Entities

Entities already extend a vanilla class, so implement the interface:

```java
public class DemonEntity extends PathfinderMob implements DataHolder {

    private final DataContainer data = DataContainer.builder("demon")
            .owner(this)
            .registry(DemonData.REGISTRY)
            .build();

    @Override
    public DataContainer data() {
        return data;
    }

    public TickScheduler<DemonEntity> scheduler() {
        return data.get(DemonData.SCHEDULER);
    }

    public DemonBrain brain() {
        return data.get(DemonData.BRAIN);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Data", DataNbt.save(data));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        DataNbt.load(data, tag.getCompound("Data"));
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide) {
            data.close();
        }
    }
}
```

This one tag replaces the separate `Brain`, `Tasks` and `Rng` tags from the other docs. `demon.get(BOND)`, `demon.set(MOOD, ...)` and `demon.update(...)` come from `DataHolder`. The method names don't clash with NeoForge's own `getData`/`setData`.

### Systems that aren't entities

```java
public final class Battle extends AbstractDataHolder {

    public static final AttachmentKey<Battle, EventBus> EVENTS =
            CoreAttachments.bus("events", Battle.class, b -> b.worldBus.child("battle#" + b.id));
    public static final AttachmentKey<Battle, BattleMachine> MACHINE =
            CoreAttachments.stateMachine("machine", Battle.class, BattleMachine.class, BattleMachine::new);
    public static final DataKey<Integer> TURN = DataKey.persistent("turn", Integer.class, DataSerializers.INT, 0);

    public Battle(EventBus worldBus, int id) {
        super(DataContainer.builder("battle#" + id).registry(REGISTRY));
        ...
    }

    public void end() {
        closeData();
    }
}
```

`AbstractDataHolder` passes itself as the owner. Override `createData(builder)` to return your own `DataContainer` subclass. It runs inside the super constructor, so don't read subclass fields there.

### Your own container type

```java
public final class DemonDataContainer extends DataContainer {

    public DemonDataContainer(DemonEntity demon) {
        super(DataContainer.builder("demon#" + demon.getId()).owner(demon).registry(DemonData.REGISTRY));
    }

    public int bond() {
        return get(DemonData.BOND);
    }

    @Override
    protected void onChanged(DataChangedEvent<?> change) {
        if (change.is(DemonData.MOOD)) {
            owner(DemonEntity.class).ifPresent(DemonEntity::syncMood);
        }
    }

    @Override
    protected void onClosed() {
        ...
    }
}
```

`child(...)` is also overridable if children should be your type too.

### Your own key type

```java
public final class StatKey extends DataKey<Integer> {

    private final Stat stat;

    private StatKey(Builder b) {
        super(b);
        this.stat = b.stat;
    }

    public static final class Builder extends DataKey.AbstractBuilder<Integer, Builder> {
        ...
    }
}
```

Protected hooks you can override on a key: `createDefault`, `sanitize`, `encode`, `decode`, `copyFor`, `onAdded`, `onRemoved`, `storesDefault`. `AttachmentKey` itself is built this way, and `AttachmentKey.AbstractAttachmentBuilder` lets you subclass attachments the same way.

### Vanilla holders (players, levels, chunks)

A `Player` can't implement `DataHolder`, so carry a container as a NeoForge attachment:

```java
public static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<DataContainer>> TRAINER =
        ATTACHMENT_TYPES.register("trainer", () -> NeoForgeData.typeCopiedOnDeath(holder ->
                DataContainer.builder("trainer").owner(holder).registry(TrainerData.REGISTRY).build()));

DataContainer trainer = player.getData(TRAINER);
trainer.update(TrainerData.CAUGHT, n -> n + 1);
```

The NeoForge type is also called `AttachmentType`, which is why ours is `AttachmentKey`. `AttachmentKey<Player, X>` works on these containers because the owner is the player.

## Event bus

Link a container to a bus with `.bus(bus)` on the builder or `publishTo(bus)`. After that:

```java
DataEvents.onChanging(bus, DemonData.BOND, e -> {
    if (e.owner().orElse(null) instanceof DemonEntity d && d.holds(Items.FRIENDSHIP_CHARM)) {
        e.setNewValue(e.oldValue() + (e.newValue() - e.oldValue()) * 2);
    }
});

DataEvents.onChanging(bus, DemonData.NICKNAME, EventPriority.HIGH, e -> {
    if (filter.isBlocked(e.newValue())) {
        e.cancel();
    }
});

DataEvents.onChanged(bus, DemonData.BOND, e -> advancements.checkBond(e.container(), e.newValue()));
DataEvents.onAttached(bus, DemonData.STATS, e -> log(e.newValue()));

bus.listen(DataChangedEvent.TYPE)
        .filter(DataEvents.ownedBy(demon))
        .priority(EventPriority.MONITOR)
        .subscribe(debugOverlay::log);
```

- `DataChangingEvent` is posted only for plain keys, and only when the bus has a listener for it. `setNewValue` goes through the key's sanitizer and validator again. If the result is invalid, the change is dropped and reported.
- `DataChangedEvent` is posted after the value is stored, for every kind of change. `TYPE` is the wildcard class constant, as in `StateTransitionEvent`.
- Container listeners (`data.addListener`, `data.listen(key, ...)`) don't need a bus. They return `Subscription`s, so they work with `SubscriptionGroup`, `ctx.bind` and `scheduler.bind`.

## Conditions

`DataConditions` gives `Condition<DataHolder>`. Because entities and containers are both `DataHolder`s, the same condition works as a transition guard, an event filter, a scheduler `when`, a roll-table entry or an evolution rule:

```java
public static final Condition<DataHolder> BONDED = DataConditions.atLeast(DemonData.BOND, 80);
public static final Condition<DataHolder> ANGRY = DataConditions.equalTo(DemonData.MOOD, Mood.ANGRY);

b.transition(IDLE, EVOLVING).guard(BONDED).guard(DemonConditions.HAS_TRAINER);
scheduler.task().when(ANGRY.negate()).run(ctx -> ctx.owner().purr());
bus.listen(DemonHurtEvent.class).filter(ANGRY.adapt(DemonHurtEvent::demon)).subscribe(...);
```

A rejection reads `IDLE->EVOLVING blocked by bond>=80`. The conditions never create attachments or store defaults when they check a value.

## Using it with the state machine

```java
b.state(GUARD).onEnter(ctx -> StateData.setWhileActive(ctx, ctx.owner().data(), DEFENSE_BONUS, 2));

b.state(IDLE).onEnter(ctx -> StateData.requestOnChange(ctx, ctx.owner().data(), MOOD,
        Condition.of("angry", m -> m == Mood.ANGRY), HOSTILE));

StateData.forwardChanges(brain, demon.data(), BOND);
b.transition(IDLE, HAPPY).on(DataChangedEvent.TYPE, (ctx, e) -> e.is(BOND) && (Integer) e.newValue() >= 80);
```

- `setWhileActive` sets the value now and puts back the previous value (or removes it) when the state exits.
- `listenWhileActive`, `requestOnChange` and `fireChangesWhileActive` bind their subscriptions to the `StateContext`, so they end when the state exits. Stale-requester protection applies as usual.
- `forwardChanges` fires `DataChangedEvent`s into the machine for its whole life. It returns a `SubscriptionGroup`.
- `StateDataKey` is still the tool for scratch data that must die with a state. `DataKey` is for data that belongs to the owner.

## Saving and loading

```java
tag.put("Data", DataNbt.save(data));
DataNbt.load(data, tag.getCompound("Data"));
```

- Only persistent keys and attachments are saved. Transient ones are skipped.
- Load resolves each saved name through the keys already in the container, then the registry.
- **Unknown names are kept, not dropped.** If a key was renamed, or an addon's key isn't loaded, its saved data stays in the container and is written back on the next save. `retained()` shows it. `load` returns `false` when this happens, or when any entry failed to load. The rest still load.
- Attachments saved with `saved(...)` load into the instance that's already there. If there's none yet, one is built with the factory first.
- Load doesn't post events or run vetoes. Attachments still get their attach hooks. Load clears the dirty set.
- `load` returns `false`, and changes nothing, when the tag is empty.
- NBT mapping: `Boolean` becomes a byte and comes back as a `Byte`. Serializers read numbers through `DataSerializers.asNumber` / `asBoolean`, so they don't care about the exact numeric type. Lists must hold one kind of value.

`copyTo(target)` copies every value whose key allows it (including retained unknown data) and posts `COPIED`. Use it for evolution, storing a demon in a party slot, or cloning. `dirtyKeys()` / `clearDirty()` track what changed since the last sync or save, including removals.

## Guard rails

- **Validation on every path.** Sanitizers and validators run on `set`, on bus rewrites, on `copyTo` and on load.
- **Loop cap.** More than `maxDepth` (16) changes nested inside listeners is logged as a loop and dropped, instead of hanging the server.
- **Listener isolation.** An exception in a listener, `onChanged`, an attach/detach hook, or while saving one key goes to the `DataErrorHandler` (logged by default). The change itself still completes.
- **All-or-nothing vetoes.** A vetoed write changes nothing and notifies no one.
- **Thread confinement.** A container binds to the first thread that uses it. Turn this off with `threadConfined(false)`.
- **Closing.** `close()` detaches every attachment and drops listeners and bus links. After that, writes throw. Plain values stay readable, so the last known values can still be shown.

## Guidelines

- Keys, attachment keys, registries and conditions go in `static final` fields, like graphs and task keys.
- One container per owner. For a demon, the container replaces separate fields for the scheduler, brain and RNG. Use `data.get(KEY)` in accessors.
- Use `persistent` for anything that must survive a save. Keep caches and render-only state transient.
- Close the container when the owner goes away: entity `remove` on the server, `Battle.end()`, `ServerStoppedEvent` for server-scoped holders.
- Use `DataChangingEvent` for "other systems may modify this" (held items, abilities, events). Use plain `set` for everything else, like `Rolls` vs `rng.chance`.
- Syncing to the client is in `core.network`. List the keys in a `SyncRegistry` and implement `SyncedDataHolder`, or use `DataSync` for attachments and custom holders. It keeps its own change tracking, so `dirtyKeys()` stays free for saving. See [network.md](network.md).
