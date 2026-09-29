package com.poptrain.innerdemons.core.random;

import java.util.Optional;

public record ChanceRolledEvent<C>(RollKey<C> key, C context, ChanceResult result) {

    public static final Class<ChanceRolledEvent<?>> TYPE = wildcardType();

    public boolean success() {
        return result.success();
    }

    public boolean is(RollKey<?> other) {
        return key == other;
    }

    @SuppressWarnings("unchecked")
    public <C2> Optional<ChanceRolledEvent<C2>> as(RollKey<C2> other) {
        return is(other) ? Optional.of((ChanceRolledEvent<C2>) (ChanceRolledEvent<?>) this) : Optional.empty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<ChanceRolledEvent<?>> wildcardType() {
        return (Class) ChanceRolledEvent.class;
    }
}
