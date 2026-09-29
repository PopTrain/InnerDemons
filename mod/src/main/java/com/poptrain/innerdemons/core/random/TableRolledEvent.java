package com.poptrain.innerdemons.core.random;

import java.util.List;
import java.util.Optional;

public record TableRolledEvent<C, T>(TableKey<C, T> key, C context, List<Weighted<T>> weights, Optional<T> result,
                                     double roll, boolean forced) {

    public static final Class<TableRolledEvent<?, ?>> TYPE = wildcardType();

    public TableRolledEvent {
        weights = List.copyOf(weights);
    }

    public boolean is(TableKey<?, ?> other) {
        return key == other;
    }

    @SuppressWarnings("unchecked")
    public <C2, T2> Optional<TableRolledEvent<C2, T2>> as(TableKey<C2, T2> other) {
        return is(other) ? Optional.of((TableRolledEvent<C2, T2>) (TableRolledEvent<?, ?>) this) : Optional.empty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<TableRolledEvent<?, ?>> wildcardType() {
        return (Class) TableRolledEvent.class;
    }
}
