package com.poptrain.innerdemons.core.random;

import java.util.Objects;
import java.util.Optional;

public final class ChanceRollEvent<C> {

    public static final Class<ChanceRollEvent<?>> TYPE = wildcardType();

    private final RollKey<C> key;
    private final C context;
    private final double baseChance;
    private double chance;
    private Boolean forced;

    ChanceRollEvent(RollKey<C> key, C context, double baseChance) {
        this.key = Objects.requireNonNull(key, "key");
        this.context = context;
        this.baseChance = baseChance;
        this.chance = baseChance;
    }

    public RollKey<C> key() {
        return key;
    }

    public C context() {
        return context;
    }

    public double baseChance() {
        return baseChance;
    }

    public double chance() {
        return chance;
    }

    public double effectiveChance() {
        return clamp(chance);
    }

    public void setChance(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("chance must not be NaN");
        }
        chance = value;
    }

    public void multiply(double factor) {
        setChance(chance * factor);
    }

    public void add(double amount) {
        setChance(chance + amount);
    }

    public void force(boolean result) {
        forced = result;
    }

    public void clearForce() {
        forced = null;
    }

    public Optional<Boolean> forced() {
        return Optional.ofNullable(forced);
    }

    public boolean is(RollKey<?> other) {
        return key == other;
    }

    @SuppressWarnings("unchecked")
    public <C2> Optional<ChanceRollEvent<C2>> as(RollKey<C2> other) {
        return is(other) ? Optional.of((ChanceRollEvent<C2>) (ChanceRollEvent<?>) this) : Optional.empty();
    }

    static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<ChanceRollEvent<?>> wildcardType() {
        return (Class) ChanceRollEvent.class;
    }

    @Override
    public String toString() {
        return "ChanceRollEvent[" + key.name() + ", base=" + baseChance + ", chance=" + chance
                + (forced != null ? ", forced=" + forced : "") + "]";
    }
}
