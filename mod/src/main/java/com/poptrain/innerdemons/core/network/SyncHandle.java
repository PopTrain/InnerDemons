package com.poptrain.innerdemons.core.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.poptrain.innerdemons.core.data.DataChangedEvent;
import com.poptrain.innerdemons.core.data.DataContainer;
import com.poptrain.innerdemons.core.event.Subscription;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SyncHandle implements Subscription {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static int serial;

    private final DataContainer container;
    private final SyncTarget target;
    private final SyncRegistry registry;
    private final Entity entity;
    private final Set<ServerPlayer> viewers = new LinkedHashSet<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private final Map<String, Tag> sent = new HashMap<>();
    private final Set<String> failing = new LinkedHashSet<>();
    private final Subscription listener;
    private Function<SyncHandle, ServerPlayer> ownerResolver;
    private ServerPlayer lastOwner;
    private int pollCountdown;
    private boolean active = true;

    SyncHandle(DataContainer container, SyncTarget target, SyncRegistry registry, Entity entity) {
        this.container = Objects.requireNonNull(container, "container");
        this.target = Objects.requireNonNull(target, "target");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.entity = entity;
        this.pollCountdown = registry.pollInterval() == 0 ? 0 : 1 + (serial++ % registry.pollInterval());
        this.listener = container.addListener(this::onChanged);
    }

    public DataContainer container() {
        return container;
    }

    public SyncTarget target() {
        return target;
    }

    public SyncRegistry registry() {
        return registry;
    }

    public Optional<Entity> entity() {
        return Optional.ofNullable(entity);
    }

    public SyncHandle ownedBy(Function<SyncHandle, ServerPlayer> resolver) {
        this.ownerResolver = resolver;
        return this;
    }

    public SyncHandle ownedBy(ServerPlayer player) {
        return ownedBy(h -> player);
    }

    public Optional<ServerPlayer> owner() {
        if (ownerResolver != null) {
            ServerPlayer player = ownerResolver.apply(this);
            return player == null || player.hasDisconnected() ? Optional.empty() : Optional.of(player);
        }
        if (entity instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        if (entity instanceof OwnableEntity ownable) {
            LivingEntity owner = ownable.getOwner();
            if (owner instanceof ServerPlayer player && !player.hasDisconnected()) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    public SyncHandle addViewer(ServerPlayer player) {
        checkActive();
        if (entity != null) {
            throw new IllegalStateException("Viewers of " + target + " follow entity tracking and can't be set by hand");
        }
        if (viewers.add(player)) {
            sendFull(player);
        }
        return this;
    }

    public boolean removeViewer(ServerPlayer player) {
        return viewers.remove(player);
    }

    public Collection<ServerPlayer> viewers() {
        if (entity == null) {
            viewers.removeIf(ServerPlayer::hasDisconnected);
            return Collections.unmodifiableSet(viewers);
        }
        Set<ServerPlayer> audience = new LinkedHashSet<>(DataSync.watchers(entity));
        if (entity instanceof ServerPlayer self && !self.hasDisconnected()) {
            audience.add(self);
        }
        return audience;
    }

    public boolean canSee(ServerPlayer player) {
        return viewers().contains(player);
    }

    public void resync() {
        dirty.addAll(registry.names());
    }

    public void sendFull(ServerPlayer player) {
        if (!active || !validate()) {
            return;
        }
        boolean isOwner = owner().filter(player::equals).isPresent();
        CompoundTag values = new CompoundTag();
        List<String> removed = new ArrayList<>();
        for (SyncedKey<?> key : registry.keys()) {
            if (key.scope() == SyncScope.OWNER && !isOwner) {
                continue;
            }
            Optional<Tag> tag = encode(key);
            if (tag == null) {
                continue;
            }
            if (tag.isPresent()) {
                values.put(key.name(), tag.get());
            } else {
                removed.add(key.name());
            }
        }
        send(player, new DataSyncPayload(target, registry.id(), values, removed));
    }

    void initialize() {
        for (SyncedKey<?> key : registry.keys()) {
            Optional<Tag> tag = encode(key);
            if (tag != null && tag.isPresent()) {
                sent.put(key.name(), tag.get());
            }
        }
        lastOwner = owner().orElse(null);
        for (ServerPlayer player : viewers()) {
            sendFull(player);
        }
    }

    void flush() {
        if (!active) {
            return;
        }
        if (!validate()) {
            cancel();
            return;
        }
        if (registry.pollInterval() > 0 && --pollCountdown <= 0) {
            pollCountdown = registry.pollInterval();
            dirty.addAll(registry.names());
        }
        ServerPlayer owner = owner().orElse(null);
        boolean ownerChanged = owner != lastOwner;
        if (dirty.isEmpty() && !ownerChanged) {
            return;
        }
        CompoundTag tracked = new CompoundTag();
        List<String> trackedRemoved = new ArrayList<>();
        CompoundTag owned = new CompoundTag();
        List<String> ownedRemoved = new ArrayList<>();
        for (String name : dirty) {
            SyncedKey<?> key = registry.find(name).orElse(null);
            if (key == null) {
                continue;
            }
            Optional<Tag> now = encode(key);
            if (now == null) {
                continue;
            }
            Tag before = sent.get(name);
            Tag current = now.orElse(null);
            if (Objects.equals(before, current)) {
                continue;
            }
            boolean ownerScope = key.scope() == SyncScope.OWNER;
            if (current == null) {
                sent.remove(name);
                (ownerScope ? ownedRemoved : trackedRemoved).add(name);
            } else {
                sent.put(name, current.copy());
                (ownerScope ? owned : tracked).put(name, current);
            }
        }
        dirty.clear();
        Collection<ServerPlayer> audience = viewers();
        if (!tracked.isEmpty() || !trackedRemoved.isEmpty()) {
            DataSyncPayload payload = new DataSyncPayload(target, registry.id(), tracked, trackedRemoved);
            for (ServerPlayer player : audience) {
                send(player, payload);
            }
        }
        if (ownerChanged) {
            lastOwner = owner;
            if (owner != null && audience.contains(owner)) {
                sendFull(owner);
            }
        } else if (owner != null && audience.contains(owner) && (!owned.isEmpty() || !ownedRemoved.isEmpty())) {
            send(owner, new DataSyncPayload(target, registry.id(), owned, ownedRemoved));
        }
    }

    private void onChanged(DataChangedEvent<?> change) {
        if (!active) {
            return;
        }
        if (change.kind() == DataChangedEvent.Kind.ATTACHED || change.kind() == DataChangedEvent.Kind.DETACHED) {
            return;
        }
        if (registry.contains(change.key())) {
            dirty.add(change.key().name());
        }
    }

    private Optional<Tag> encode(SyncedKey<?> key) {
        try {
            Optional<Tag> tag = key.encode(container);
            failing.remove(key.name());
            return tag;
        } catch (RuntimeException e) {
            if (failing.add(key.name())) {
                LOGGER.error("Failed to encode synced key {} of {} for {}", key.name(), container.name(), target, e);
            }
            return null;
        }
    }

    private boolean validate() {
        if (container.isClosed()) {
            return false;
        }
        return entity == null || !entity.isRemoved();
    }

    private void send(ServerPlayer player, DataSyncPayload payload) {
        if (player.hasDisconnected()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    private void checkActive() {
        if (!active) {
            throw new IllegalStateException("Sync handle for " + target + " has been cancelled");
        }
    }

    @Override
    public void cancel() {
        if (!active) {
            return;
        }
        active = false;
        listener.cancel();
        viewers.clear();
        dirty.clear();
        sent.clear();
        DataSync.forget(this);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return "SyncHandle[" + container.name() + " -> " + target + ", " + registry.id() + (active ? "" : ", cancelled") + "]";
    }
}
