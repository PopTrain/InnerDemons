package com.poptrain.innerdemons.core.network;

import java.util.Objects;
import java.util.Optional;

import com.poptrain.innerdemons.core.data.DataContainer;
import com.poptrain.innerdemons.core.data.DataKey;
import com.poptrain.innerdemons.core.data.DataNbt;
import com.poptrain.innerdemons.core.data.DataSerializer;

import net.minecraft.nbt.Tag;

public final class SyncedKey<T> {

    private final DataKey<T> key;
    private final DataSerializer<T> serializer;
    private final SyncScope scope;

    SyncedKey(DataKey<T> key, DataSerializer<T> serializer, SyncScope scope) {
        this.key = Objects.requireNonNull(key, "key");
        if (key.isAttachment()) {
            throw new IllegalArgumentException("Attachment keys can't be synced: " + key.name()
                    + ". Mirror the part the client needs into a plain key instead.");
        }
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    public DataKey<T> key() {
        return key;
    }

    public String name() {
        return key.name();
    }

    public DataSerializer<T> serializer() {
        return serializer;
    }

    public SyncScope scope() {
        return scope;
    }

    public Optional<Tag> encode(DataContainer container) {
        Optional<T> value = container.find(key);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DataNbt.toTag(serializer.encode(value.get())));
    }

    public T decode(Tag tag) {
        return serializer.decode(DataNbt.fromTag(tag));
    }

    public boolean apply(DataContainer container, Tag tag) {
        return container.set(key, decode(tag));
    }

    public boolean clear(DataContainer container) {
        return container.remove(key);
    }

    @Override
    public String toString() {
        return "SyncedKey[" + key.name() + ", " + scope + "]";
    }
}
