package com.poptrain.innerdemons.species;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

public record Movepool(List<LevelUpMove> levelUp, List<MoveEntry> mm, List<MoveEntry> ranch) {

    public static final Movepool EMPTY = new Movepool(List.of(), List.of(), List.of());

    public static final Codec<Movepool> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LevelUpMove.CODEC.listOf().optionalFieldOf("level_up", List.of()).forGetter(Movepool::levelUp),
            MoveEntry.CODEC.listOf().optionalFieldOf("mm", List.of()).forGetter(Movepool::mm),
            MoveEntry.CODEC.listOf().optionalFieldOf("ranch", List.of()).forGetter(Movepool::ranch)
    ).apply(instance, Movepool::new));

    public Movepool {
        levelUp = List.copyOf(levelUp);
        mm = List.copyOf(mm);
        ranch = List.copyOf(ranch);
    }

    public List<ResourceLocation> learnedAt(int level) {
        return levelUp.stream()
                .filter(entry -> entry.level() == level)
                .map(LevelUpMove::move)
                .toList();
    }

    public List<ResourceLocation> learnedUpTo(int level) {
        return levelUp.stream()
                .filter(entry -> entry.level() <= level)
                .map(LevelUpMove::move)
                .distinct()
                .toList();
    }

    public boolean canLearn(ResourceLocation move) {
        return levelUp.stream().anyMatch(entry -> entry.move().equals(move))
                || Stream.concat(mm.stream(), ranch.stream()).anyMatch(entry -> entry.move().equals(move));
    }

    public record LevelUpMove(int level, ResourceLocation move) {

        public static final Codec<LevelUpMove> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("level").forGetter(LevelUpMove::level),
                DemonCodecs.ID.fieldOf("move").forGetter(LevelUpMove::move)
        ).apply(instance, LevelUpMove::new));
    }

    public record MoveEntry(ResourceLocation move) {

        public static final Codec<MoveEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DemonCodecs.ID.fieldOf("move").forGetter(MoveEntry::move)
        ).apply(instance, MoveEntry::new));
    }
}
