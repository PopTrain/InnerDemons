package com.poptrain.innerdemons.species;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum Rank implements StringRepresentable {
    KILO,
    MEGA,
    GIGA,
    TERA,
    PETA,
    EXA;

    public static final Codec<Rank> CODEC = StringRepresentable.fromEnum(Rank::values);

    public boolean isAtLeast(Rank other) {
        return compareTo(other) >= 0;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
