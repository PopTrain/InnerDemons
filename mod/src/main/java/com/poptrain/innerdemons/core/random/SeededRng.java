package com.poptrain.innerdemons.core.random;

import java.util.Objects;
import java.util.UUID;

public final class SeededRng implements Rng {

    private long seed;
    private long lo;
    private long hi;

    private SeededRng(long seed) {
        reseed(seed);
    }

    private SeededRng(RngState state) {
        restore(state);
    }

    public static SeededRng of(long seed) {
        return new SeededRng(seed);
    }

    public static SeededRng of(UUID id) {
        return new SeededRng(Seeds.of(id));
    }

    public static SeededRng of(long seed, String label) {
        return new SeededRng(Seeds.of(seed, label));
    }

    public static SeededRng from(RngState state) {
        return new SeededRng(state);
    }

    public long seed() {
        return seed;
    }

    public SeededRng derive(String label) {
        return new SeededRng(Seeds.of(seed, label));
    }

    public SeededRng derive(long salt) {
        return new SeededRng(Seeds.combine(seed, salt));
    }

    @Override
    public SeededRng split() {
        return new SeededRng(Seeds.combine(nextLong(), nextLong()));
    }

    public SeededRng copy() {
        return new SeededRng(state());
    }

    public void reseed(long value) {
        seed = value;
        long s = value;
        lo = Seeds.mix(s += Seeds.GOLDEN_GAMMA);
        hi = Seeds.mix(s + Seeds.GOLDEN_GAMMA);
        if ((lo | hi) == 0L) {
            hi = Seeds.GOLDEN_GAMMA;
        }
    }

    public void reset() {
        reseed(seed);
    }

    public RngState state() {
        return new RngState(seed, lo, hi);
    }

    public void restore(RngState state) {
        Objects.requireNonNull(state, "state");
        seed = state.seed();
        lo = state.lo();
        hi = state.hi();
        if ((lo | hi) == 0L) {
            reseed(seed);
        }
    }

    @Override
    public long nextLong() {
        long s0 = lo;
        long s1 = hi;
        long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        lo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        hi = Long.rotateLeft(s1, 28);
        return result;
    }

    @Override
    public String toString() {
        return "SeededRng[seed=" + Long.toHexString(seed) + "]";
    }
}
