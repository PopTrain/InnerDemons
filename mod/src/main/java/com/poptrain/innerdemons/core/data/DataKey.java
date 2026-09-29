package com.poptrain.innerdemons.core.data;

import com.poptrain.innerdemons.core.condition.Condition;
import com.poptrain.innerdemons.core.condition.ConditionResult;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class DataKey<T> {

    private final String name;
    private final Class<? super T> type;
    private final Supplier<? extends T> defaultValue;
    private final DataSerializer<T> serializer;
    private final UnaryOperator<T> copier;
    private final UnaryOperator<T> sanitizer;
    private final Condition<? super T> validator;
    private final boolean inherited;

    protected DataKey(AbstractBuilder<T, ?> builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.defaultValue = builder.defaultValue;
        this.serializer = builder.serializer;
        this.copier = builder.copier;
        this.sanitizer = builder.sanitizer;
        this.validator = builder.validator;
        this.inherited = builder.inherited;
    }

    public static <T> Builder<T> builder(String name, Class<? super T> type) {
        return new Builder<>(name, type);
    }

    public static <T> DataKey<T> of(String name, Class<? super T> type) {
        return DataKey.<T>builder(name, type).build();
    }

    public static <T> DataKey<T> of(String name, Class<? super T> type, T defaultValue) {
        return DataKey.<T>builder(name, type).defaultValue(defaultValue).build();
    }

    public static <T> DataKey<T> persistent(String name, Class<? super T> type, DataSerializer<T> serializer) {
        return DataKey.<T>builder(name, type).persistent(serializer).build();
    }

    public static <T> DataKey<T> persistent(String name, Class<? super T> type, DataSerializer<T> serializer, T defaultValue) {
        return DataKey.<T>builder(name, type).persistent(serializer).defaultValue(defaultValue).build();
    }

    public final String name() {
        return name;
    }

    public final Class<? super T> type() {
        return type;
    }

    public final boolean isPersistent() {
        return serializer != null || hasCustomPersistence();
    }

    public final boolean isInherited() {
        return inherited;
    }

    public final Optional<DataSerializer<T>> serializer() {
        return Optional.ofNullable(serializer);
    }

    public final Optional<Condition<? super T>> validator() {
        return Optional.ofNullable(validator);
    }

    public final boolean hasDefault() {
        return defaultValue != null;
    }

    public boolean isAttachment() {
        return false;
    }

    @SuppressWarnings("unchecked")
    public final T cast(Object value) {
        if (!type.isInstance(value)) {
            throw new DataException("Value " + value + " is not a " + type.getSimpleName() + " for key '" + name + "'");
        }
        return (T) value;
    }

    public final ConditionResult check(T value) {
        return validator == null ? ConditionResult.pass() : validator.evaluate(value);
    }

    protected boolean hasCustomPersistence() {
        return false;
    }

    protected boolean storesDefault() {
        return false;
    }

    protected T createDefault(DataContainer container) {
        return defaultValue == null ? null : defaultValue.get();
    }

    protected T sanitize(T value) {
        return sanitizer == null ? value : sanitizer.apply(value);
    }

    protected Object encode(T value) {
        if (serializer == null) {
            throw new DataException("Key '" + name + "' is not persistent");
        }
        return serializer.encode(value);
    }

    protected T decode(DataContainer container, T existing, Object tree) {
        if (serializer == null) {
            throw new DataException("Key '" + name + "' is not persistent");
        }
        return serializer.decode(tree);
    }

    protected T copyFor(DataContainer target, T value) {
        return copier == null ? null : copier.apply(value);
    }

    protected void onAdded(DataContainer container, T value) {
    }

    protected void onRemoved(DataContainer container, T value) {
    }

    @Override
    public String toString() {
        return "DataKey[" + name + ":" + type.getSimpleName() + "]";
    }

    public abstract static class AbstractBuilder<T, B extends AbstractBuilder<T, B>> {

        protected final String name;
        protected final Class<? super T> type;
        protected Supplier<? extends T> defaultValue;
        protected DataSerializer<T> serializer;
        protected UnaryOperator<T> copier;
        protected UnaryOperator<T> sanitizer;
        protected Condition<? super T> validator;
        protected boolean inherited;
        protected boolean copySet;

        protected AbstractBuilder(String name, Class<? super T> type) {
            this.name = DataRegistry.checkName(name);
            this.type = Objects.requireNonNull(type, "type");
            this.inherited = true;
        }

        @SuppressWarnings("unchecked")
        protected final B self() {
            return (B) this;
        }

        public B defaultValue(T value) {
            Objects.requireNonNull(value, "defaultValue");
            this.defaultValue = () -> value;
            return self();
        }

        public B defaultValue(Supplier<? extends T> supplier) {
            this.defaultValue = Objects.requireNonNull(supplier, "defaultValue");
            return self();
        }

        public B persistent(DataSerializer<T> serializer) {
            this.serializer = Objects.requireNonNull(serializer, "serializer");
            if (!copySet) {
                this.copier = UnaryOperator.identity();
            }
            return self();
        }

        public B copy(UnaryOperator<T> copier) {
            this.copier = Objects.requireNonNull(copier, "copier");
            this.copySet = true;
            return self();
        }

        public B copyAsIs() {
            return copy(UnaryOperator.identity());
        }

        public B noCopy() {
            this.copier = null;
            this.copySet = true;
            return self();
        }

        public B sanitize(UnaryOperator<T> sanitizer) {
            UnaryOperator<T> previous = this.sanitizer;
            Objects.requireNonNull(sanitizer, "sanitizer");
            this.sanitizer = previous == null ? sanitizer : v -> sanitizer.apply(previous.apply(v));
            return self();
        }

        public B validate(Condition<? super T> condition) {
            Objects.requireNonNull(condition, "condition");
            if (validator == null) {
                this.validator = condition;
            } else {
                Condition<T> combined = Condition.<T>allOf(validator, condition);
                this.validator = combined;
            }
            return self();
        }

        public B validate(String name, Predicate<? super T> predicate) {
            return validate(Condition.of(name, predicate));
        }

        public B inherited(boolean inherited) {
            this.inherited = inherited;
            return self();
        }
    }

    public static final class Builder<T> extends AbstractBuilder<T, Builder<T>> {

        private Builder(String name, Class<? super T> type) {
            super(name, type);
        }

        public DataKey<T> build() {
            return new DataKey<>(this);
        }
    }
}
