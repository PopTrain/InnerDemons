package com.poptrain.innerdemons.client.network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.poptrain.innerdemons.core.data.DataContainer;
import com.poptrain.innerdemons.core.data.DataHolder;
import com.poptrain.innerdemons.core.network.DataSyncPayload;
import com.poptrain.innerdemons.core.network.DataSyncedEvent;
import com.poptrain.innerdemons.core.network.Network;
import com.poptrain.innerdemons.core.network.NetworkBuses;
import com.poptrain.innerdemons.core.network.SyncRegistry;
import com.poptrain.innerdemons.core.network.SyncTarget;
import com.poptrain.innerdemons.core.network.SyncedDataHolder;
import com.poptrain.innerdemons.core.network.SyncedKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = Network.MODID, value = Dist.CLIENT)
public final class ClientDataSync {

    public static final int PENDING_TICKS = 100;
    public static final int MAX_PENDING = 4096;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, Function<String, Optional<DataContainer>>> RESOLVERS = new ConcurrentHashMap<>();
    private static final Deque<Pending> PENDING = new ArrayDeque<>();
    private static final Set<SyncTarget> WAITING = new LinkedHashSet<>();
    private static int clock;

    private ClientDataSync() {
    }

    public static void registerResolver(ResourceLocation kind, Function<String, Optional<DataContainer>> resolver) {
        if (RESOLVERS.putIfAbsent(kind, resolver) != null) {
            throw new IllegalArgumentException("A client sync resolver for " + kind + " already exists");
        }
    }

    public static Optional<DataContainer> resolve(SyncTarget target) {
        ClientLevel level = Minecraft.getInstance().level;
        return switch (target) {
            case SyncTarget.OfEntity e -> level == null ? Optional.empty() : entity(level, e.entityId())
                    .filter(DataHolder.class::isInstance)
                    .map(x -> ((DataHolder) x).data());
            case SyncTarget.OfEntityAttachment a -> level == null ? Optional.empty() : entity(level, a.entityId())
                    .flatMap(x -> attachment(x, a.attachment()));
            case SyncTarget.Custom c -> {
                Function<String, Optional<DataContainer>> resolver = RESOLVERS.get(c.kind());
                yield resolver == null ? Optional.empty() : resolver.apply(c.id());
            }
        };
    }

    public static void handle(DataSyncPayload payload) {
        if (WAITING.contains(payload.target()) || !apply(payload)) {
            defer(payload);
        }
    }

    private static boolean apply(DataSyncPayload payload) {
        Optional<DataContainer> resolved = resolve(payload.target());
        if (resolved.isEmpty()) {
            return false;
        }
        DataContainer container = resolved.get();
        if (container.isClosed()) {
            return true;
        }
        Optional<SyncRegistry> registry = registry(payload.registry(), container);
        if (registry.isEmpty()) {
            LOGGER.error("Unknown sync registry {} for {}. Pass it to DataSync.register(...) in the mod constructor",
                    payload.registry(), payload.target());
            return true;
        }
        Set<String> changed = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        for (String name : payload.values().getAllKeys()) {
            Optional<SyncedKey<?>> key = registry.get().find(name);
            if (key.isEmpty()) {
                LOGGER.warn("Synced key {} isn't in {} on the client", name, payload.registry());
                continue;
            }
            try {
                if (key.get().apply(container, payload.values().get(name))) {
                    changed.add(name);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Failed to apply synced key {} to {}", name, container.name(), e);
            }
        }
        for (String name : payload.removed()) {
            Optional<SyncedKey<?>> key = registry.get().find(name);
            if (key.isEmpty()) {
                continue;
            }
            try {
                if (key.get().clear(container)) {
                    removed.add(name);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Failed to remove synced key {} from {}", name, container.name(), e);
            }
        }
        if (!changed.isEmpty() || !removed.isEmpty()) {
            try {
                NetworkBuses.client().post(new DataSyncedEvent(container, payload.target(), registry.get(), changed, removed));
            } catch (RuntimeException e) {
                LOGGER.error("Failed to post DataSyncedEvent for {}", container.name(), e);
            }
        }
        return true;
    }

    private static Optional<SyncRegistry> registry(ResourceLocation id, DataContainer container) {
        Optional<SyncRegistry> registry = SyncRegistry.byId(id);
        if (registry.isEmpty() && container.owner().orElse(null) instanceof SyncedDataHolder holder) {
            holder.syncRegistry();
            registry = SyncRegistry.byId(id);
        }
        return registry;
    }

    private static Optional<Entity> entity(ClientLevel level, int id) {
        return Optional.ofNullable(level.getEntity(id));
    }

    private static Optional<DataContainer> attachment(Entity entity, ResourceLocation id) {
        AttachmentType<?> type = NeoForgeRegistries.ATTACHMENT_TYPES.get(id);
        if (type == null) {
            LOGGER.error("Unknown attachment type {} in a sync payload", id);
            return Optional.empty();
        }
        Object value = entity.getData(type);
        return value instanceof DataContainer container ? Optional.of(container) : Optional.empty();
    }

    private static void defer(DataSyncPayload payload) {
        if (PENDING.size() >= MAX_PENDING) {
            Pending dropped = PENDING.pollFirst();
            LOGGER.warn("Too many pending sync payloads, dropped one for {}", dropped.payload().target());
            rebuildWaiting();
        }
        PENDING.addLast(new Pending(payload, clock + PENDING_TICKS));
        WAITING.add(payload.target());
    }

    private static void retry() {
        if (PENDING.isEmpty()) {
            return;
        }
        Set<SyncTarget> blocked = new LinkedHashSet<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            SyncTarget target = pending.payload().target();
            if (blocked.contains(target)) {
                continue;
            }
            if (apply(pending.payload())) {
                it.remove();
            } else if (clock >= pending.expiresAt()) {
                it.remove();
            } else {
                blocked.add(target);
            }
        }
        rebuildWaiting();
    }

    private static void rebuildWaiting() {
        WAITING.clear();
        for (Pending pending : PENDING) {
            WAITING.add(pending.payload().target());
        }
    }

    private static void reset() {
        PENDING.clear();
        WAITING.clear();
        clock = 0;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        clock++;
        retry();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        NetworkBuses.closeClient();
    }

    private record Pending(DataSyncPayload payload, int expiresAt) {
    }
}
