# Network Sync

Packages: `com.poptrain.innerdemons.core.network` (common), `com.poptrain.innerdemons.client.network` (client only)

Server-to-client syncing for `DataContainer`s, plus typed messages in both directions that arrive on the core `EventBus`. It's built on NeoForge's payload system (`RegisterPayloadHandlersEvent`, `PacketDistributor`) and registers itself through `@EventBusSubscriber`, so nothing has to be added to the mod class to turn it on.

The server is always the authority. Synced data flows server → client only. Anything the client wants changed goes to the server as a message, the server validates it and makes the change, and the change syncs back.

It follows the same rules as the other core systems: keys and registries are `static final`, handles are `Subscription`s, checks are `Condition`s, received things are posted on an `EventBus`, and errors never interrupt a tick.

## Pieces

| Type | Role |
|---|---|
| `SyncRegistry` | The `DataKey`s a holder syncs, each with a `SyncScope`. Has a `ResourceLocation` id that goes over the wire. |
| `SyncScope` | `TRACKING`: every player that can see the holder. `OWNER`: only the owning player. |
| `SyncedKey<T>` | One entry in a registry: key, serializer, scope. |
| `SyncedDataHolder` | `DataHolder` + `syncRegistry()`. An entity that implements it is synced automatically. |
| `DataSync` | Server side. Creates `SyncHandle`s, follows entity tracking, flushes changes once per server tick. |
| `SyncHandle` | One synced container on the server. A `Subscription`. `resync`, `sendFull`, `addViewer`, `ownedBy`. |
| `SyncTarget` | How the client finds the container: an entity, a NeoForge attachment on an entity, or a custom id. |
| `DataSyncPayload` | The packet: target, registry id, changed values as NBT, removed names. |
| `DataSyncedEvent` | Posted on the client bus after a payload is applied. |
| `ClientDataSync` | Client side. Applies payloads, waits for entities that haven't spawned yet, resolves custom targets. |
| `StateSync` | Mirrors a `StateMachine`'s current state (or active path) into a `DataKey`, so it can be synced. |
| `MessageType<M>` | A typed message: id, class, `StreamCodec`, direction, optional server-side validator. |
| `Network` | Mod id, protocol version, message registration and the `sendTo...` helpers. |
| `MessageReceivedEvent<M>` | Posted on the receiving side's bus for every message. |
| `NetworkEvents` | `onReceived`, `forward` (message → state machine), `onSynced`, and the matching `Condition`s. |
| `NetworkBuses` | The server-scoped and client-scoped `EventBus`es that received messages and sync events are posted on. |

## Syncing data

### Declare what syncs

```java
public final class DemonData {

    public static final DataKey<Integer> BOND = ...;
    public static final DataKey<Mood> MOOD = ...;
    public static final DataKey<String> NICKNAME = ...;
    public static final DataKey<DemonState> ANIM_STATE = StateSync.stateKey("anim_state", DemonState.class);

    public static final SyncRegistry SYNC = SyncRegistry.builder("demon")
            .key(MOOD)
            .key(NICKNAME)
            .key(BOND, SyncScope.OWNER)
            .key(ANIM_STATE, StateSync.stateSerializer(DemonState.class))
            .build();
}
```

| Builder method | Effect |
|---|---|
| `key(key)` / `key(key, scope)` | Sync with the key's own persistent serializer. Default scope is `TRACKING`. |
| `key(key, serializer)` / `key(key, serializer, scope)` | Sync a transient key, or send a different form than the saved one. |
| `include(other)` | Merge another registry, e.g. shared entity keys plus species keys. |
| `pollEvery(ticks)` | How often every key is re-checked, even without a change event. Defaults to 20. `0` turns it off. |

- Only plain keys sync. Attachment keys (schedulers, brains, RNG streams) are rejected at build time. Mirror what the client needs into a plain key instead, as `StateSync` does for machines.
- A key without a persistent serializer needs one passed to `key(...)`.
- Registry ids must be unique. `SyncRegistry.builder("demon")` becomes `innerdemons:demon`.
- The client looks registries up by id, so the class that holds one must be loaded on the client before its first payload arrives. Call `DataSync.register(DemonData.SYNC, TrainerData.SYNC)` from the mod constructor. For `SyncedDataHolder` entities it also works without that, since the client asks the entity for its registry.

### Entities

Implement `SyncedDataHolder` instead of `DataHolder`:

```java
public class DemonEntity extends PathfinderMob implements SyncedDataHolder {

    private final DataContainer data = DataContainer.builder("demon")
            .owner(this)
            .registry(DemonData.REGISTRY)
            .build();

    @Override
    public DataContainer data() {
        return data;
    }

    @Override
    public SyncRegistry syncRegistry() {
        return DemonData.SYNC;
    }
}
```

That's all. When the entity joins a server level, `DataSync` creates a handle for it. When a player starts tracking the entity, that player gets a full snapshot. After that, changes go out as deltas at the end of each server tick. When the entity leaves the level, or its container is closed, the handle ends.

Reading on the client is the same code as on the server:

```java
demon.get(DemonData.MOOD);
demon.data().listen(DemonData.ANIM_STATE, e -> animator.play(e.newValue()));
```

The client applies values with a normal `set`, so client-side listeners, `onChanged` overrides and `DataChangedEvent`s on a client bus all fire as usual. Sanitizers and validators run too.

### Players and other NeoForge attachments

A container carried as a NeoForge attachment (see [data.md](data.md)) is synced by declaring it once in the mod constructor:

```java
DataSync.syncPlayerAttachment(TRAINER, TrainerData.SYNC);
DataSync.syncEntityAttachment(DEMON_TAGS, DemonEntity.class, DemonTagData.SYNC);
```

Handles are created whenever a matching entity joins a level. For players, that covers login, respawn and changing dimension, each of which sends a full snapshot to the player. Other players who track that player get the `TRACKING` keys, so a trainer title can be visible to everyone while caught counts stay `OWNER`-only.

### Anything else (battles, parties, storage boxes)

Holders that aren't entities use a custom target. The server says who can see it. The client says where the container lives.

```java
public static final ResourceLocation BATTLE_TARGET = Network.id("battle");

SyncHandle handle = DataSync.trackCustom(BATTLE_TARGET, "battle#" + id, battle.data(), BattleData.SYNC);
handle.addViewer(playerA);
handle.addViewer(playerB);
lifetime.add(handle);

ClientDataSync.registerResolver(BATTLE_TARGET, id -> ClientBattles.find(id).map(ClientBattle::data));
```

`addViewer` sends that player a full snapshot straight away. Players who log out are dropped. Cancel the handle (directly, through a `SubscriptionGroup`, or with `scheduler.bind(...)`) when the battle ends. Register client resolvers from client-only setup code, such as `FMLClientSetupEvent`.

### Scopes and owners

- `TRACKING` keys go to every player that can see the holder: players tracking the entity, the entity itself if it's a player, or the viewers of a custom handle.
- `OWNER` keys go only to the owner, and only while the owner can see the holder. The owner is the player itself for player attachments, `OwnableEntity.getOwner()` for tamed demons, or whatever `handle.ownedBy(...)` returns.
- When the owner changes (a demon is traded, a player respawns), the new owner gets a full snapshot of the `OWNER` keys.

### When things are sent

- A handle listens to its container. A change to a synced key marks it dirty. At the end of the server tick, dirty keys are encoded and compared with what was last sent. Only real differences go out.
- Several changes to one key in the same tick send one value. All changes for one holder in one tick go in one packet per scope.
- Every `pollEvery` ticks all synced keys are compared anyway. This catches changes that don't post events: `DataNbt.load`, in-place edits to mutable values, and values read through a parent container. For an immediate send in those cases, call `DataSync.resync(container)`.
- Removing a value sends a removal. On the client the key falls back to its default.
- Defaults are never sent. The client has the same key and the same default.

### On the client

- Payloads run on the main client thread.
- If the target can't be found yet (the entity hasn't spawned, the level is still loading), the payload waits for up to `ClientDataSync.PENDING_TICKS` (100) ticks and is retried every tick. Later payloads for the same target wait behind it, so order is kept.
- After a payload is applied, a `DataSyncedEvent` is posted on `NetworkBuses.client()` with the names that changed and were removed. Use it to refresh a screen once per packet instead of once per key:

```java
NetworkEvents.onSynced(NetworkBuses.client(), TrainerData.CAUGHT, e -> partyScreen.refresh());
```

## Syncing state machines

The brain runs on the server only. To drive animations on the client, mirror its state into a key and sync that key. The machine's constructor is a good place:

```java
public DemonBrain(DemonEntity demon) {
    super(GRAPH, demon);
    if (!demon.level().isClientSide()) {
        StateSync.mirror(this, demon.data(), DemonData.ANIM_STATE);
    }
}
```

With `CoreAttachments.stateMachine(...)` the brain is still built on first use and stopped when the container closes, and the mirror comes with it.

- `mirror` writes `currentState()` (the deepest active state) on every transition, including the restore after a load. `mirrorAs(machine, data, key, mapping)` writes something else, like an animation name.
- `mirrorPath` writes `activeStates()`, for when the client needs to know it's in `COMBAT` as well as `STRIKE`.
- `StateSync.stateKey(...)` makes a transient, non-copied key. Don't make the mirror key persistent: the machine saves itself, and the mirror is rebuilt on restore.
- It returns a `Subscription` that removes the listener.

This replaces the `SynchedEntityData` approach mentioned in [gltf-models.md](gltf-models.md). On the client, `GltfEntityRenderer.animate` can read `demon.get(DemonData.ANIM_STATE)`.

## Messages

Messages are for one-off things that aren't state: "open the party screen", "the player picked move 2", "play this capture effect".

### Defining them

```java
public record SelectMove(int battleId, int slot) {
    public static final StreamCodec<ByteBuf, SelectMove> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectMove::battleId,
            ByteBufCodecs.VAR_INT, SelectMove::slot,
            SelectMove::new);
}

public final class BattleMessages {

    public static final MessageType<SelectMove> SELECT_MOVE = MessageType.builder("select_move", SelectMove.class, SelectMove.CODEC)
            .toServer()
            .validate(Condition.of("slot", m -> m.slot() >= 0 && m.slot() < 4))
            .build();

    public static final MessageType<OpenBattleScreen> OPEN_SCREEN =
            MessageType.toClient("open_battle_screen", OpenBattleScreen.class, OpenBattleScreen.CODEC);
}
```

Register them from the mod constructor, before NeoForge collects payloads:

```java
Network.register(BattleMessages.SELECT_MOVE, BattleMessages.OPEN_SCREEN);
```

| Builder method | Effect |
|---|---|
| `toClient()` / `toServer()` / `bothWays()` | Direction. Sending the wrong way throws. |
| `validate(condition)` | Checked on the server for every message from a client. A failure is logged with the check that failed and the message is dropped. Calling it more than once combines with AND. |

### Sending

```java
Network.sendToPlayer(player, OPEN_SCREEN, new OpenBattleScreen(id));
Network.sendToTracking(demon, CAPTURE_FX, new CaptureFx(demon.getId()));
Network.sendToTrackingAndSelf(player, ...);
Network.sendToPlayers(battle.viewers(), ...);
Network.sendToLevel(level, ...);
Network.sendToAll(...);
Network.sendToServer(SELECT_MOVE, new SelectMove(id, 2));
```

### Receiving

Every received message is posted as a `MessageReceivedEvent` on the receiving side's bus: `NetworkBuses.server()` or `NetworkBuses.client()`. `NetworkEvents.onReceived` filters by type and gives you the typed event:

```java
NetworkEvents.onReceived(NetworkBuses.server(), BattleMessages.SELECT_MOVE, e -> {
    ServerPlayer player = e.sender().orElseThrow();
    Battles.find(e.message().battleId())
            .filter(b -> b.isParticipant(player))
            .ifPresent(b -> b.selectMove(player, e.message().slot()));
});
```

Because they're ordinary bus events, everything from [event-bus.md](event-bus.md) applies: priorities, filters, `once()`, `SubscriptionGroup`s, `ctx.bind(...)`.

### Messages drive state machines

`forward` fires the message itself into a machine, so it goes through `.on(SelectMove.class, ...)` transitions like any other event:

```java
lifetime.add(NetworkEvents.forward(NetworkBuses.server(), BattleMessages.SELECT_MOVE, battle.machine(),
        Condition.of("participant", m -> m.battleId() == battle.id())));

b.transition(SELECT, RESOLVE).on(SelectMove.class, (ctx, m) -> ctx.owner().allMovesChosen());
```

Or only while a state is active:

```java
b.state(SELECT).onEnter(ctx ->
        ctx.bind(NetworkEvents.forward(NetworkBuses.server(), BattleMessages.SELECT_MOVE, ctx.machine())));
```

Forwarding subscribes at `LOW`, so normal listeners see the message first.

## Buses

`NetworkBuses` holds the two scoped buses the event bus guidelines ask for:

- `server()` is created in `ServerAboutToStartEvent` and closed in `ServerStoppedEvent`. It throws while no server is running. `serverIfRunning()` returns an `Optional`.
- `client()` is created on first use on the client and closed when the player logs out.
- `of(level)` picks the right one, so a container built in an entity constructor can publish to the matching side: `DataContainer.builder("demon").bus(NetworkBuses.of(level))`.

In singleplayer, both exist at once on different threads, as they should.

## Guard rails

- **Server authority.** Data only syncs server → client. Clients can't write to server containers. Client messages are validated before they are posted.
- **Thread safety.** Payload handlers run on the main thread of each side. Server sync state is only touched on the server thread, client state only on the client thread.
- **Error isolation.** A key that fails to encode is logged once and skipped until it encodes again. A value that fails to apply on the client is logged and the rest of the payload still applies. A validator that throws drops the message.
- **Lifetime.** Handles end when their entity leaves its level, their container closes, or they are cancelled. Everything is cleared on `ServerStoppedEvent` and client logout, so an integrated server restart starts clean.
- **Build-time checks.** Attachment keys, keys without serializers, duplicate names, duplicate registry ids and duplicate message ids all throw when they are declared.
- **Late registration.** `Network.register` after payload registration throws instead of silently doing nothing.
- **Protocol version.** `Network.VERSION` is sent during the handshake. Bump it when a payload's format changes, so mismatched clients are refused with a clear message.

## Guidelines

- Sync what the client shows or animates. Keep AI internals, RNG streams and scheduler state on the server.
- Prefer `OWNER` for anything private: exact stats, IVs, bond, inventory.
- Keep synced values small and immutable. Big lists that change often are better as a message with just the change.
- Use messages for requests and one-off effects, synced data for state. A client request should change server data and let the sync carry the result back, rather than the server replying with a second message.
- Always check the sender on the server. `validate(...)` checks the message's shape. Whether *this* player may do it is for the listener to check.
- Registries, message types and target ids go in `static final` fields, like graphs, task keys and data keys.
