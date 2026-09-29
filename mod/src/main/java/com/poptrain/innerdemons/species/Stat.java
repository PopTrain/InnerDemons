package com.poptrain.innerdemons.species;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum Stat implements StringRepresentable {
    HP,
    STAMINA,
    MELEE_ATTACK,
    MELEE_DEFENSE,
    RANGED_ATTACK,
    RANGED_DEFENSE,
    SPEED;

    public static final Codec<Stat> CODEC = StringRepresentable.fromEnum(Stat::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
