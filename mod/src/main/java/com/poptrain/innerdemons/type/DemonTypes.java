package com.poptrain.innerdemons.type;

import com.poptrain.innerdemons.species.DemonCodecs;
import com.poptrain.innerdemons.species.DemonRegistries;

import net.minecraft.resources.ResourceKey;

public final class DemonTypes {

    public static final ResourceKey<DemonType> AIR = key("air");
    public static final ResourceKey<DemonType> ARCHAIC = key("archaic");
    public static final ResourceKey<DemonType> BRAWL = key("brawl");
    public static final ResourceKey<DemonType> BUG = key("bug");
    public static final ResourceKey<DemonType> CRYSTAL = key("crystal");
    public static final ResourceKey<DemonType> DARK = key("dark");
    public static final ResourceKey<DemonType> EARTH = key("earth");
    public static final ResourceKey<DemonType> ELECTRIC = key("electric");
    public static final ResourceKey<DemonType> FIRE = key("fire");
    public static final ResourceKey<DemonType> LIGHT = key("light");
    public static final ResourceKey<DemonType> METAL = key("metal");
    public static final ResourceKey<DemonType> MIND = key("mind");
    public static final ResourceKey<DemonType> PLANT = key("plant");
    public static final ResourceKey<DemonType> SIMPLE = key("simple");
    public static final ResourceKey<DemonType> SPIRIT = key("spirit");
    public static final ResourceKey<DemonType> TOXIC = key("toxic");
    public static final ResourceKey<DemonType> WATER = key("water");

    private DemonTypes() {
    }

    public static ResourceKey<DemonType> key(String path) {
        return ResourceKey.create(DemonRegistries.TYPE, DemonCodecs.id(path));
    }
}
