package com.poptrain.innerdemons.core.network;

import java.util.Optional;

import com.poptrain.innerdemons.core.event.EventBus;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = Network.MODID)
public final class NetworkBuses {

    private static volatile EventBus server;
    private static volatile EventBus client;

    private NetworkBuses() {
    }

    public static EventBus server() {
        EventBus bus = server;
        if (bus == null || bus.isClosed()) {
            throw new IllegalStateException("The server bus only exists while a server is running");
        }
        return bus;
    }

    public static Optional<EventBus> serverIfRunning() {
        EventBus bus = server;
        return bus == null || bus.isClosed() ? Optional.empty() : Optional.of(bus);
    }

    public static EventBus client() {
        EventBus bus = client;
        if (bus == null || bus.isClosed()) {
            bus = EventBus.create("client");
            client = bus;
        }
        return bus;
    }

    public static EventBus of(Level level) {
        return level.isClientSide() ? client() : server();
    }

    public static EventBus of(boolean clientSide) {
        return clientSide ? client() : server();
    }

    public static void closeClient() {
        EventBus bus = client;
        client = null;
        if (bus != null) {
            bus.close();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        EventBus previous = server;
        if (previous != null) {
            previous.close();
        }
        server = EventBus.create("server");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStopped(ServerStoppedEvent event) {
        EventBus bus = server;
        server = null;
        if (bus != null) {
            bus.close();
        }
    }
}
