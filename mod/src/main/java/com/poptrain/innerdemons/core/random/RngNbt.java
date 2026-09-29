package com.poptrain.innerdemons.core.random;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class RngNbt {

    private static final String SEED = "Seed";
    private static final String LO = "Lo";
    private static final String HI = "Hi";
    private static final String FAILURES = "Failures";

    private RngNbt() {
    }

    public static CompoundTag save(SeededRng rng) {
        return write(rng.state());
    }

    public static boolean load(SeededRng rng, CompoundTag tag) {
        if (!tag.contains(SEED, Tag.TAG_LONG)) {
            return false;
        }
        rng.restore(read(tag));
        return true;
    }

    public static SeededRng loadOrCreate(CompoundTag tag, long fallbackSeed) {
        SeededRng rng = SeededRng.of(fallbackSeed);
        load(rng, tag);
        return rng;
    }

    public static CompoundTag write(RngState state) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(SEED, state.seed());
        tag.putLong(LO, state.lo());
        tag.putLong(HI, state.hi());
        return tag;
    }

    public static RngState read(CompoundTag tag) {
        return new RngState(tag.getLong(SEED), tag.getLong(LO), tag.getLong(HI));
    }

    public static CompoundTag save(PityCounter pity) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(FAILURES, pity.failures());
        return tag;
    }

    public static void load(PityCounter pity, CompoundTag tag) {
        pity.setFailures(tag.getInt(FAILURES));
    }
}
