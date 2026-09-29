package com.poptrain.innerdemons.core.data;

import com.poptrain.innerdemons.core.event.Subscription;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class AttachmentKey<H, T> extends DataKey<T> {

    private final Class<? super H> holderType;
    private final Function<? super H, ? extends T> factory;
    private final Function<? super T, Object> saver;
    private final BiConsumer<? super T, Object> loader;
    private final BiConsumer<? super H, ? super T> onAttach;
    private final BiConsumer<? super H, ? super T> onDetach;
    private final boolean copyBySaving;

    protected AttachmentKey(AbstractAttachmentBuilder<H, T, ?> builder) {
        super(builder);
        this.holderType = builder.holderType;
        this.factory = Objects.requireNonNull(builder.factory, "Attachment '" + builder.name + "' has no factory");
        this.saver = builder.saver;
        this.loader = builder.loader;
        this.onAttach = builder.onAttach;
        this.onDetach = builder.onDetach;
        this.copyBySaving = builder.copyBySaving;
    }

    public static <H, T> Builder<H, T> builder(String name, Class<? super H> holderType, Class<? super T> type) {
        return new Builder<>(name, holderType, type);
    }

    public static <H, T> AttachmentKey<H, T> of(String name, Class<? super H> holderType, Class<? super T> type,
                                                 Function<? super H, ? extends T> factory) {
        return AttachmentKey.<H, T>builder(name, holderType, type).factory(factory).build();
    }

    public final Class<? super H> holderType() {
        return holderType;
    }

    @Override
    public final boolean isAttachment() {
        return true;
    }

    public final boolean accepts(Object holder) {
        return holderType.isInstance(holder);
    }

    @SuppressWarnings("unchecked")
    public final H holderOf(DataContainer container) {
        Object owner = container.owner().orElseThrow(() -> new DataException(
                "Attachment '" + name() + "' needs an owner of type " + holderType.getSimpleName() + ", but " + container.name() + " has none"));
        if (!holderType.isInstance(owner)) {
            throw new DataException("Attachment '" + name() + "' needs an owner of type " + holderType.getSimpleName()
                    + ", but " + container.name() + " is owned by " + owner.getClass().getSimpleName());
        }
        return (H) owner;
    }

    public T create(H holder) {
        return Objects.requireNonNull(factory.apply(holder), "Attachment '" + name() + "' factory returned null");
    }

    @Override
    protected boolean hasCustomPersistence() {
        return saver != null;
    }

    @Override
    protected final boolean storesDefault() {
        return true;
    }

    @Override
    protected T createDefault(DataContainer container) {
        return create(holderOf(container));
    }

    @Override
    protected Object encode(T value) {
        if (saver != null) {
            return saver.apply(value);
        }
        return super.encode(value);
    }

    @Override
    protected T decode(DataContainer container, T existing, Object tree) {
        if (loader != null) {
            T target = existing != null ? existing : create(holderOf(container));
            loader.accept(target, tree);
            return target;
        }
        return super.decode(container, existing, tree);
    }

    @Override
    protected T copyFor(DataContainer target, T value) {
        if (copyBySaving && isPersistent()) {
            return decode(target, null, encode(value));
        }
        return super.copyFor(target, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onAdded(DataContainer container, T value) {
        H holder = holderOf(container);
        if (value instanceof Attachment<?> attachment) {
            ((Attachment<H>) attachment).onAttached(holder);
        }
        if (onAttach != null) {
            onAttach.accept(holder, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onRemoved(DataContainer container, T value) {
        H holder = holderOf(container);
        if (onDetach != null) {
            onDetach.accept(holder, value);
        }
        if (value instanceof Attachment<?> attachment) {
            ((Attachment<H>) attachment).onDetached(holder);
        }
        if (value instanceof Subscription subscription) {
            subscription.cancel();
        } else if (value instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new DataException("Closing attachment '" + name() + "' failed", e);
            }
        }
    }

    @Override
    public String toString() {
        return "AttachmentKey[" + name() + ":" + holderType.getSimpleName() + "->" + type().getSimpleName() + "]";
    }

    public abstract static class AbstractAttachmentBuilder<H, T, B extends AbstractAttachmentBuilder<H, T, B>>
            extends DataKey.AbstractBuilder<T, B> {

        protected final Class<? super H> holderType;
        protected Function<? super H, ? extends T> factory;
        protected Function<? super T, Object> saver;
        protected BiConsumer<? super T, Object> loader;
        protected BiConsumer<? super H, ? super T> onAttach;
        protected BiConsumer<? super H, ? super T> onDetach;
        protected boolean copyBySaving;

        protected AbstractAttachmentBuilder(String name, Class<? super H> holderType, Class<? super T> type) {
            super(name, type);
            this.holderType = Objects.requireNonNull(holderType, "holderType");
            this.inherited = false;
            this.copier = null;
            this.copySet = true;
        }

        public B factory(Function<? super H, ? extends T> factory) {
            this.factory = Objects.requireNonNull(factory, "factory");
            return self();
        }

        public B factory(Supplier<? extends T> factory) {
            Objects.requireNonNull(factory, "factory");
            this.factory = h -> factory.get();
            return self();
        }

        @Override
        public B defaultValue(Supplier<? extends T> supplier) {
            return factory(supplier);
        }

        @Override
        public B defaultValue(T value) {
            throw new UnsupportedOperationException("Attachments are created per holder; use factory(...)");
        }

        @Override
        public B inherited(boolean inherited) {
            if (inherited) {
                throw new UnsupportedOperationException("Attachments belong to their own holder and are never inherited");
            }
            return super.inherited(false);
        }

        public B saved(Function<? super T, Object> saver, BiConsumer<? super T, Object> loader) {
            this.saver = Objects.requireNonNull(saver, "saver");
            this.loader = Objects.requireNonNull(loader, "loader");
            return self();
        }

        public <S> B savedAs(DataSerializer<S> format, Function<? super T, ? extends S> save, BiConsumer<? super T, ? super S> load) {
            Objects.requireNonNull(format, "format");
            return saved(v -> format.encode(save.apply(v)), (v, tree) -> load.accept(v, format.decode(tree)));
        }

        public B onAttach(BiConsumer<? super H, ? super T> hook) {
            this.onAttach = Objects.requireNonNull(hook, "onAttach");
            return self();
        }

        public B onDetach(BiConsumer<? super H, ? super T> hook) {
            this.onDetach = Objects.requireNonNull(hook, "onDetach");
            return self();
        }

        public B copyBySaving() {
            this.copyBySaving = true;
            return self();
        }
    }

    public static final class Builder<H, T> extends AbstractAttachmentBuilder<H, T, Builder<H, T>> {

        private Builder(String name, Class<? super H> holderType, Class<? super T> type) {
            super(name, holderType, type);
        }

        public AttachmentKey<H, T> build() {
            return new AttachmentKey<>(this);
        }
    }
}
