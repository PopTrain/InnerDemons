package com.poptrain.innerdemons.core.network;

import java.util.Optional;
import java.util.Set;

import com.poptrain.innerdemons.core.data.DataContainer;
import com.poptrain.innerdemons.core.data.DataKey;

public record DataSyncedEvent(DataContainer container, SyncTarget target, SyncRegistry registry,
        Set<String> changed, Set<String> removed) {

    public DataSyncedEvent {
        changed = Set.copyOf(changed);
        removed = Set.copyOf(removed);
    }

    public boolean touched(DataKey<?> key) {
        return changed.contains(key.name()) || removed.contains(key.name());
    }

    public Optional<Object> owner() {
        return container.owner();
    }
}
