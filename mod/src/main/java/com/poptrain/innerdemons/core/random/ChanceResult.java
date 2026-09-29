package com.poptrain.innerdemons.core.random;

public record ChanceResult(boolean success, double baseChance, double chance, double roll, boolean forced) {

    public boolean failed() {
        return !success;
    }

    public boolean modified() {
        return forced || chance != ChanceRollEvent.clamp(baseChance);
    }
}
