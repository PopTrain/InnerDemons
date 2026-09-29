package com.poptrain.innerdemons.core.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import com.poptrain.innerdemons.core.data.DataContainer;
import com.poptrain.innerdemons.core.data.DataHolder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = Network.MODID)
public final class DataSync {

    private static final List<AttachmentSync> ENTITY_ATTACHMENTS = new CopyOnWriteArrayList<>();

    private static final List<SyncHandle> HANDLES = new ArrayList<>();
    private static final Map<Entity, List<SyncHandle>> BY_ENTITY = new IdentityHashMap<>();
    private static final Map<DataContainer, List<SyncHandle>> BY_CONTAINER = new IdentityHashMap<>();
    private static final Map<Entity, Set<ServerPlayer>> WATCHERS = new IdentityHashMap<>();

    private DataSync() {
    }

    public static void register(SyncRegistry... registries) {
        for (SyncRegistry registry : registries) {
            Objects.requireNonNull(registry, "registry");
        }
    }

    public static <C extends DataContainer> void syncEntityAttachment(Supplier<AttachmentType<C>> type,
            Class<? extends Entity> entityType, SyncRegistry registry) {
        ENTITY_ATTACHMENTS.add(new AttachmentSync(type, entityType, registry));
    }

    public static <C extends DataContainer> void syncPlayerAttachment(Supplier<AttachmentType<C>> type, SyncRegistry registry) {
        syncEntityAttachment(type, ServerPlayer.class, registry);
    }

    public static <E extends Entity & DataHolder> SyncHandle trackEntity(E entity, SyncRegistry registry) {
        requireServer(entity);
        return track(entity.data(), SyncTarget.entity(entity.getId()), registry, entity);
    }

    public static <C extends DataContainer> SyncHandle trackEntityAttachment(Entity entity, AttachmentType<C> type,
            SyncRegistry registry) {
        requireServer(entity);
        ResourceLocation id = NeoForgeRegistries.ATTACHMENT_TYPES.getKey(type);
        if (id == null) {
            throw new IllegalArgumentException("Attachment type " + type + " is not registered");
        }
        return track(entity.getData(type), SyncTarget.entityAttachment(entity.getId(), id), registry, entity);
    }

    public static SyncHandle trackCustom(ResourceLocation kind, String id, DataContainer container, SyncRegistry registry) {
        return track(container, SyncTarget.custom(kind, id), registry, null);
    }

    public static List<SyncHandle> handles(DataContainer container) {
        List<SyncHandle> list = BY_CONTAINER.get(container);
        return list == null ? List.of() : List.copyOf(list);
    }

    public static List<SyncHandle> handles(Entity entity) {
        List<SyncHandle> list = BY_ENTITY.get(entity);
        return list == null ? List.of() : List.copyOf(list);
    }

    public static Optional<SyncHandle> find(DataContainer container, SyncRegistry registry) {
        List<SyncHandle> list = BY_CONTAINER.get(container);
        if (list == null) {
            return Optional.empty();
        }
        return list.stream().filter(h -> h.registry() == registry).findFirst();
    }

    public static void resync(DataContainer container) {
        handles(container).forEach(SyncHandle::resync);
    }

    public static Collection<ServerPlayer> watchers(Entity entity) {
        Set<ServerPlayer> set = WATCHERS.get(entity);
        return set == null ? List.of() : Collections.unmodifiableSet(set);
    }

    private static SyncHandle track(DataContainer container, SyncTarget target, SyncRegistry registry, Entity entity) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(registry, "registry");
        if (container.isClosed()) {
            throw new IllegalStateException("Can't sync closed container " + container.name());
        }
        List<SyncHandle> existing = BY_CONTAINER.get(container);
        if (existing != null) {
            for (SyncHandle handle : existing) {
                if (handle.registry() == registry && handle.target().equals(target) && handle.isActive()) {
                    return handle;
                }
            }
        }
        SyncHandle handle = new SyncHandle(container, target, registry, entity);
        HANDLES.add(handle);
        BY_CONTAINER.computeIfAbsent(container, c -> new ArrayList<>()).add(handle);
        if (entity != null) {
            BY_ENTITY.computeIfAbsent(entity, e -> new ArrayList<>()).add(handle);
        }
        handle.initialize();
        return handle;
    }

    static void forget(SyncHandle handle) {
        HANDLES.remove(handle);
        removeFrom(BY_CONTAINER, handle.container(), handle);
        handle.entity().ifPresent(e -> removeFrom(BY_ENTITY, e, handle));
    }

    private static <K> void removeFrom(Map<K, List<SyncHandle>> map, K key, SyncHandle handle) {
        List<SyncHandle> list = map.get(key);
        if (list != null) {
            list.remove(handle);
            if (list.isEmpty()) {
                map.remove(key);
            }
        }
    }

    private static void requireServer(Entity entity) {
        if (entity.level().isClientSide()) {
            throw new IllegalStateException("Data sync handles are server side only: " + entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (entity instanceof SyncedDataHolder holder) {
            track(holder.data(), SyncTarget.entity(entity.getId()), holder.syncRegistry(), entity);
        }
        for (AttachmentSync sync : ENTITY_ATTACHMENTS) {
            if (sync.entityType().isInstance(entity)) {
                trackEntityAttachment(entity, sync.type().get(), sync.registry());
            }
        }
    }

    @SubscribeEvent
    static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        handles(entity).forEach(SyncHandle::cancel);
        WATCHERS.remove(entity);
    }

    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity target = event.getTarget();
        WATCHERS.computeIfAbsent(target, e -> new LinkedHashSet<>()).add(player);
        for (SyncHandle handle : handles(target)) {
            handle.sendFull(player);
        }
    }

    @SubscribeEvent
    static void onStopTracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Set<ServerPlayer> set = WATCHERS.get(event.getTarget());
        if (set != null) {
            set.remove(player);
            if (set.isEmpty()) {
                WATCHERS.remove(event.getTarget());
            }
        }
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        WATCHERS.values().forEach(set -> set.remove(player));
        WATCHERS.values().removeIf(Set::isEmpty);
        for (SyncHandle handle : List.copyOf(HANDLES)) {
            handle.removeViewer(player);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        for (SyncHandle handle : List.copyOf(HANDLES)) {
            handle.flush();
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        for (SyncHandle handle : List.copyOf(HANDLES)) {
            handle.cancel();
        }
        HANDLES.clear();
        BY_ENTITY.clear();
        BY_CONTAINER.clear();
        WATCHERS.clear();
    }

    private record AttachmentSync(Supplier<? extends AttachmentType<? extends DataContainer>> type,
            Class<? extends Entity> entityType, SyncRegistry registry) {
    }
}
