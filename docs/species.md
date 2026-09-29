# Demon Species

Package: `com.poptrain.innerdemons.species`

Each species is a JSON file in a datapack registry, `innerdemons:demon`. The file's location is the species id: `data/innerdemons/innerdemons/demon/rookeen.json` becomes `innerdemons:rookeen`. Addons and datapacks add species with `data/<their_namespace>/innerdemons/demon/<name>.json`. The registry syncs to clients with the full codec, so the client can read every field.

## Pieces

| Type | Role |
|---|---|
| `DemonSpecies` | Root record plus `CODEC`. |
| `DemonRegistries` | `SPECIES` and `EVOLUTION_METHOD` registry keys, `register(modBus)`, `species(access)` lookups. |
| `DemonCodecs` | `ID` (namespace defaults to `innerdemons`), `key(registry)`, `registryEntry(registry)`. |
| `BaseStats` / `Stat` | Seven positive stats. `get(Stat)`, `total()`. |
| `Training` / `GrowthRate` | `tp_yield`, `catch_rate` (0-255, 0 = uncatchable), `base_friendship` (0-255), `base_exp`, `growth_rate`. |
| `GenderRatio` | Weights, not percentages. `maleChance()`, `isGenderless()`. Absent = genderless. |
| `Behavior` / `Flavor` | `flavor_preference` (enum), `favorite_habitat` and `social_tendency` (ids). |
| `Rank` | `kilo`, `mega`, `giga`, `tera`, `peta`, `exa`. Ordered, with `isAtLeast`. |
| `Movepool` | `level_up` (`level` + `move`), `mm` and `ranch` (`move`). All optional. |
| `evolution.Evolution` | `method` + method fields + `target` (a `ResourceKey<DemonSpecies>`). |
| `evolution.EvolutionMethod` / `EvolutionMethods` | Static registry `innerdemons:evolution_method` of `MapCodec`s, dispatched on `method`. |
| `DemonSpeciesValidator` | After every server data load, logs evolutions whose target is missing or the species itself. |

## Ids

Types, moves, habitats, social tendencies, evolution methods and evolution targets are ids. A bare name such as `"tackle"` means `innerdemons:tackle`. Use `"otherns:name"` for another namespace. Encoding writes `innerdemons` ids back as bare names.

Types and moves are plain ids until they get registries of their own. When they do, swap `DemonCodecs.ID` for `DemonCodecs.registryEntry(...)` or `key(...)`.

## Evolution methods

| `method` | Fields |
|---|---|
| `level_up` | `level` (required, >= 1) |
| `low_friendship` | `max_friendship` (optional, 0-255; absent = the game's default threshold) |

Add one:

```java
public record HoldItemCondition(ResourceLocation item) implements EvolutionCondition {
    public static final MapCodec<HoldItemCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(HoldItemCondition::item)
    ).apply(i, HoldItemCondition::new));

    public EvolutionMethod<HoldItemCondition> method() {
        return EvolutionMethods.HOLD_ITEM.get();
    }
}

public static final DeferredHolder<EvolutionMethod<?>, EvolutionMethod<HoldItemCondition>> HOLD_ITEM =
        METHODS.register("hold_item", () -> new EvolutionMethod<>(HoldItemCondition.MAP_CODEC));
```

Addons use their own `DeferredRegister.create(DemonRegistries.EVOLUTION_METHOD, theirModId)`.

Conditions are data only. Deciding when an evolution fires, and which branch wins when several match (Rookeen has three at level 10), belongs to the evolution system.

## Lookup

```java
Registry<DemonSpecies> species = DemonRegistries.species(level.registryAccess());
DemonSpecies rookeen = species.get(DemonCodecs.id("rookeen"));
Component name = Component.translatable(DemonSpecies.translationKey(DemonCodecs.id("rookeen")));
```

## Wiring

`InnerDemons` calls `DemonRegistries.register(modEventBus)`. That registers the evolution method registry, the datapack registry and the validator.
