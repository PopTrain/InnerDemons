package com.poptrain.innerdemons.species;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

public record BaseStats(
        int hp,
        int stamina,
        int meleeAttack,
        int meleeDefense,
        int rangedAttack,
        int rangedDefense,
        int speed) {

    public static final Codec<BaseStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("hp").forGetter(BaseStats::hp),
            ExtraCodecs.POSITIVE_INT.fieldOf("stamina").forGetter(BaseStats::stamina),
            ExtraCodecs.POSITIVE_INT.fieldOf("melee_attack").forGetter(BaseStats::meleeAttack),
            ExtraCodecs.POSITIVE_INT.fieldOf("melee_defense").forGetter(BaseStats::meleeDefense),
            ExtraCodecs.POSITIVE_INT.fieldOf("ranged_attack").forGetter(BaseStats::rangedAttack),
            ExtraCodecs.POSITIVE_INT.fieldOf("ranged_defense").forGetter(BaseStats::rangedDefense),
            ExtraCodecs.POSITIVE_INT.fieldOf("speed").forGetter(BaseStats::speed)
    ).apply(instance, BaseStats::new));

    public int get(Stat stat) {
        return switch (stat) {
            case HP -> hp;
            case STAMINA -> stamina;
            case MELEE_ATTACK -> meleeAttack;
            case MELEE_DEFENSE -> meleeDefense;
            case RANGED_ATTACK -> rangedAttack;
            case RANGED_DEFENSE -> rangedDefense;
            case SPEED -> speed;
        };
    }

    public int total() {
        return hp + stamina + meleeAttack + meleeDefense + rangedAttack + rangedDefense + speed;
    }
}
