package com.poptrain.innerdemons.type;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.poptrain.innerdemons.species.DemonCodecs;
import com.poptrain.innerdemons.species.DemonRegistries;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public record DemonType(
        List<ResourceKey<DemonType>> strengths,
        List<ResourceKey<DemonType>> weaknesses,
        List<ResourceKey<DemonType>> resistances,
        List<ResourceKey<DemonType>> immunities) {

    public static final Codec<ResourceKey<DemonType>> KEY_CODEC = DemonCodecs.key(DemonRegistries.TYPE);

    public static final Codec<List<ResourceKey<DemonType>>> KEY_LIST_CODEC = KEY_CODEC.listOf();

    public static final Codec<DemonType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            KEY_LIST_CODEC.optionalFieldOf("strengths", List.of()).forGetter(DemonType::strengths),
            KEY_LIST_CODEC.optionalFieldOf("weaknesses", List.of()).forGetter(DemonType::weaknesses),
            KEY_LIST_CODEC.optionalFieldOf("resistances", List.of()).forGetter(DemonType::resistances),
            KEY_LIST_CODEC.optionalFieldOf("immunities", List.of()).forGetter(DemonType::immunities)
    ).apply(instance, DemonType::new));

    public DemonType {
        strengths = List.copyOf(strengths);
        weaknesses = List.copyOf(weaknesses);
        resistances = List.copyOf(resistances);
        immunities = List.copyOf(immunities);
    }

    public boolean isStrongAgainst(ResourceKey<DemonType> defender) {
        return strengths.contains(defender);
    }

    public boolean isWeakTo(ResourceKey<DemonType> attacker) {
        return weaknesses.contains(attacker);
    }

    public boolean resists(ResourceKey<DemonType> attacker) {
        return resistances.contains(attacker);
    }

    public boolean isImmuneTo(ResourceKey<DemonType> attacker) {
        return immunities.contains(attacker);
    }

    public static String translationKey(ResourceLocation id) {
        return "demon_type." + id.getNamespace() + "." + id.getPath();
    }

    public static String translationKey(ResourceKey<DemonType> key) {
        return translationKey(key.location());
    }
}
