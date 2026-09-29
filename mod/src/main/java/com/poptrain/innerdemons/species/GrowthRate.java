package com.poptrain.innerdemons.species;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum GrowthRate implements StringRepresentable {
    ERRATIC,
    FAST,
    MEDIUM_FAST,
    MEDIUM_SLOW,
    SLOW,
    FLUCTUATING;

    public static final Codec<GrowthRate> CODEC = StringRepresentable.fromEnum(GrowthRate::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
