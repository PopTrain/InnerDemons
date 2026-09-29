# Random (RNG)

Package: `com.poptrain.innerdemons.core.random`

A seedable, saveable random number system with no Minecraft dependency, except for `RngNbt` and `MinecraftRng`. It covers plain rolls (ints, doubles, chances), weighted tables, context-dependent tables built on `Condition`s, and pity counters. Rolls can also go through the event bus, so abilities, held items and field effects can change odds without the rolling code knowing about them.

The same pieces are meant for spawns, capture checks, shiny-equivalent variants, stat rolls, battle accuracy and crits, drops and evolution odds.

## Pieces

| Type | Role |
|---|---|
| `Rng` | The interface every system takes. Only `nextLong()` and `split()` are abstract. Everything else (`nextInt(bound)`, `range`, `chance`, `oneIn`, `nextGaussian`, `pick`, `shuffle`, ...) is a default method. |
| `SeededRng` | The standard implementation (xoroshiro128++, the same algorithm vanilla uses for world gen). Remembers its seed, can `derive` stable child streams, and can save and restore its exact position. |
| `Seeds` | Turns labels, UUIDs and numbers into well-mixed 64-bit seeds. `Seeds.of("shiny")`, `Seeds.of(uuid)`, `Seeds.combine(a, b, ...)`. |
| `RngState` | `(seed, lo, hi)` snapshot of a `SeededRng`. |
| `Weighted<T>` / `WeightedTable<T>` | A fixed, immutable weighted list. Build once, store in a `static final` field. |
| `RollTable<C, T>` | A weighted list whose entries can have a `Condition<? super C>` and a weight computed from the context. |
| `PityRule` / `PityCounter` | Bad-luck protection: soft pity ramps the chance up after N failures, hard pity guarantees success on the Nth try. |
| `RollKey<C>` / `TableKey<C, T>` | Typed names for a roll or table, used as the hook point on the event bus. |
| `Rolls` | Runs a chance or table roll, optionally through a bus. Also creates typed, key-filtered subscriptions (`onChance`, `onTable`, ...). |
| `ChanceRollEvent` / `ChanceRolledEvent` | Posted before and after a keyed chance roll. The first is mutable, the second is a record for observers. |
| `TableRollEvent` / `TableRolledEvent` | The same pair for table rolls. |
| `RngConditions` | Random checks as `Condition`s: `chance`, `oneIn`, and keyed `roll`. |
| `RngNbt` | Save and load a `SeededRng` or a `PityCounter`. |
| `MinecraftRng` | Two-way adapter between `Rng` and vanilla's `RandomSource`. |

## Basic use

```java
SeededRng rng = Rng.seeded(worldSeed);

int level = rng.range(5, 12);
boolean crit = rng.chance(1.0 / 16);
boolean rare = rng.oneIn(4096);
double spread = rng.triangle(1.0, 0.15);
Move move = rng.pick(demon.moves());
rng.shuffle(turnOrder);
```

`Rng.unseeded()` gives a `SeededRng` with a fresh random seed. `MinecraftRng.wrap(entity.getRandom())` lets you use `Rng` methods on a vanilla random without owning a separate stream.

## Streams and seeds

Give each system its own stream so that adding a roll in one place doesn't change the outcome of another.

```java
SeededRng world = SeededRng.of(serverLevel.getSeed());
SeededRng spawns = world.derive("spawns");
SeededRng battles = world.derive("battles");
```

| Method | Behavior |
|---|---|
| `derive(label)` / `derive(salt)` | New stream built from this stream's *seed* and the label. It doesn't advance the parent and gives the same child every time. Use it for named, per-purpose streams. |
| `split()` | New stream built from the parent's *next values*. It advances the parent and gives a different child each call. Use it for one-off sub-streams, such as one per battle. |
| `copy()` | Exact duplicate at the current position. Useful for previews ("what would this roll be?") that must not touch the real stream. |
| `reset()` / `reseed(seed)` | Rewind to the seed, or switch to a new one. |

### Per-demon traits from the UUID

Anything that should be fixed for an individual demon, such as its variant or hidden stats, can be derived from its UUID. The client and server then compute the same result, and nothing extra has to be saved or synced.

```java
SeededRng traits = SeededRng.of(demon.getUUID());
boolean variant = traits.derive("variant").oneIn(4096);
int textureIndex = traits.derive("texture").nextInt(species.textureCount());
```

## Weighted tables

```java
public static final WeightedTable<Rarity> RARITY = WeightedTable.<Rarity>builder()
        .add(Rarity.COMMON, 70)
        .add(Rarity.UNCOMMON, 24)
        .add(Rarity.RARE, 5)
        .add(Rarity.LEGENDARY, 1)
        .build();

Rarity rarity = RARITY.pick(rng);
double odds = RARITY.probabilityOf(Rarity.RARE);
```

Weights must be finite and not negative. Zero-weight entries are dropped at build time. `pick` throws on an empty table, and `roll` returns an `Optional`.

### Context-dependent tables

`RollTable` entries use the same `Condition` type as the state machine and the event bus, so spawn rules and similar checks can be shared.

```java
public static final RollTable<SpawnContext, Species> FOREST = RollTable.<SpawnContext, Species>builder("forest")
        .add(Species.SPROUTLING, 40)
        .add(Species.MOTHKIN, 25, SpawnConditions.NIGHT)
        .add(Species.BARKHOUND, ctx -> 5 + ctx.nearbyTrees(), SpawnConditions.NEAR_TREES)
        .build();

Optional<Species> species = FOREST.roll(rng, ctx);
```

Weight functions that return a negative number, zero, NaN or infinity leave that entry out for that roll. `candidates(ctx)` shows which entries are available and with what weight. `resolve(ctx)` turns that into a `WeightedTable`.

## Pity

```java
PityCounter pity = new PityCounter(PityRule.soft(1.0 / 4096, 2000, 1.0 / 2048));

if (pity.roll(rng)) {
    spawnVariant();
}
tag.put("VariantPity", RngNbt.save(pity));
```

| Factory | Behavior |
|---|---|
| `PityRule.none(p)` | Plain chance `p`, but the failure count is still tracked. |
| `PityRule.hard(p, n)` | Chance `p`, and the `n`th try always succeeds. |
| `PityRule.soft(p, after, step)` | After `after` failures, each further failure adds `step` to the chance. |
| `new PityRule(p, after, step, hard)` | Both. |

A success resets the failure count to 0.

## Rolls through the event bus

For rolls that other systems should be able to affect, give the roll a key and run it through `Rolls` with a bus.

```java
public static final RollKey<CaptureAttempt> CAPTURE = RollKey.of("capture", CaptureAttempt.class);
public static final TableKey<SpawnContext, Species> SPAWN = TableKey.of("spawn", SpawnContext.class, Species.class);

ChanceResult result = Rolls.chance(rng, bus, CAPTURE, attempt, attempt.baseChance());
if (result.success()) {
    capture(attempt);
}

Optional<Species> species = Rolls.table(rng, bus, SPAWN, FOREST, spawnContext);
```

Listeners are added with a typed builder that already filters by key. `when(...)` takes a `Condition` over the roll's context, and `filter(...)` takes one over the event.

```java
Rolls.onChance(bus, CAPTURE)
        .when(CaptureConditions.TARGET_ASLEEP)
        .named("sleepBonus")
        .subscribe(e -> e.multiply(1.5));

Rolls.onChance(bus, CAPTURE)
        .priority(EventPriority.LOWEST)
        .when("masterOrb", a -> a.orb() == Orbs.MASTER)
        .subscribe(e -> e.force(true));

Rolls.onTable(bus, SPAWN)
        .when(SpawnConditions.LURE_ACTIVE)
        .subscribe(e -> e.multiplyIf(s -> s.rarity() == Rarity.RARE, 3.0));

Rolls.onChanceRolled(bus, CAPTURE)
        .priority(EventPriority.MONITOR)
        .subscribe(e -> stats.recordCapture(e.context(), e.result()));
```

| `ChanceRollEvent` | Effect |
|---|---|
| `setChance`, `multiply`, `add` | Change the chance. It can go outside 0–1 while listeners run, and is clamped when the roll happens, so the order of `+` and `×` modifiers still matters and is set by priority. |
| `force(true/false)` / `clearForce()` | Decide the result outright. The last listener to call `force` wins. |
| `baseChance()`, `chance()`, `effectiveChance()` | The original, current and clamped chance. |

| `TableRollEvent` | Effect |
|---|---|
| `multiply(value, f)`, `multiplyIf(pred, f)` | Scale weights. |
| `setWeight(value, w)`, `add(value, w)`, `removeIf(pred)` | Replace, add or remove entries, including values that weren't in the table. |
| `force(value)` | Decide the result outright. |

`ChanceResult` records `success`, `baseChance`, the final `chance`, the `roll` value and whether it was `forced`. `modified()` tells you whether any listener changed the odds or forced the result, which is useful for battle messages like "The orb's power made the difference!".

`Rolls.chance(rng, bus, key, ctx, pityCounter)` uses the counter's current chance and records the result in it.

If the bus is `null`, closed, or has no roll listeners, no event objects are created.

### Tying into battles and states

Roll listeners are ordinary bus subscriptions, so the usual lifetime tools apply. Register them on a battle's child bus so they are removed with the battle, or bind them to a state:

```java
b.state(BattlePhase.RESOLVE).onEnter(ctx -> ctx.bind(
        Rolls.onChance(battleBus, ACCURACY).when(FieldConditions.FOG).subscribe(e -> e.multiply(0.6))));
```

## Guard rails

- **One draw per keyed roll.** `chance`, `Rolls.chance`, `WeightedTable.roll`, `RollTable.roll` and `Rolls.table` always use exactly one value from the stream. That's true when the chance is 0 or 1, when the result is forced, and when the table is empty. Listeners and conditions can change outcomes without shifting the rolls that come after, so a seeded battle stays reproducible.
- **Unbiased bounds.** `nextInt(bound)`, `nextLong(bound)` and `range(min, max)` use rejection sampling, so there's no modulo bias. `range` handles the full `int` range without overflow. These methods may use more than one draw.
- **Validation.** Negative, NaN or infinite weights and NaN chances are rejected with a message that names the entry or key.
- **Not thread-safe.** A `SeededRng` is a plain mutable object. Keep each one on the thread that owns it, like a state machine or bus. Use `split()` or `derive()` to give another thread its own stream.
- **Save format.** `RngNbt` stores `Seed`, `Lo` and `Hi` as longs. `load` returns `false` and leaves the generator unchanged if the tag has no seed. `loadOrCreate(tag, fallbackSeed)` covers the "new entity" case.

## Using it with Minecraft

```java
private SeededRng rng = Rng.unseeded();

protected void addAdditionalSaveData(CompoundTag tag) {
    tag.put("Rng", RngNbt.save(rng));
}

protected void readAdditionalSaveData(CompoundTag tag) {
    rng = RngNbt.loadOrCreate(tag.getCompound("Rng"), Seeds.of(getUUID()));
}
```

`MinecraftRng.asRandomSource(rng)` lets vanilla APIs that expect a `RandomSource` draw from your seeded stream, such as `IntProvider.sample` or loot table rolls. `setSeed` works when the wrapped `Rng` is a `SeededRng`. `forkPositional` is backed by vanilla's `XoroshiroRandomSource`. Wrapping and unwrapping the same object gives back the original.

## Conditions

```java
b.transition(IDLE, WANDER).when(RngConditions.oneIn(DemonEntity::rng, 200));
b.anyTransition(FLEE).on(HurtEvent.class, RngConditions.chance(e -> e.demon().rng(), 0.3));
Condition<CaptureAttempt> breaksFree = RngConditions.roll(CaptureAttempt::rng, bus, BREAK_FREE, 0.25);
```

These are named `chance0.3`, `chance1/200` and `roll:<key>`, so rejection logs make the randomness obvious. As [conditions.md](conditions.md) notes, a random condition is evaluated once per attempt and draws from the stream every time it's evaluated.

## Guidelines

- Store tables, rules and keys in `static final` fields. They're immutable and safe to share.
- One stream per purpose. Prefer `derive("purpose")` to sharing a single generator across systems.
- Seed battles from something you can log, such as `world.derive("battles").split()`, and record the seed. A bug report can then replay the battle exactly.
- Use `Rolls` with a key for any odds that abilities, items or effects might change. Use plain `rng.chance(...)` for odds nothing else should touch.
- Keep roll listeners free of their own randomness. If one needs it, give it a derived stream so the main stream doesn't shift.
