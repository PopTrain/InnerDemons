package com.poptrain.innerdemons.core.random;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

public final class Seeds {

    static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;
    private static final SecureRandom ENTROPY = new SecureRandom();

    private Seeds() {
    }

    public static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long combine(long first, long second) {
        return mix(first * GOLDEN_GAMMA ^ mix(second + GOLDEN_GAMMA));
    }

    public static long combine(long first, long... rest) {
        long result = mix(first);
        for (long value : rest) {
            result = combine(result, value);
        }
        return result;
    }

    public static long of(String label) {
        Objects.requireNonNull(label, "label");
        long hash = FNV_OFFSET;
        for (byte b : label.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= FNV_PRIME;
        }
        return mix(hash);
    }

    public static long of(UUID id) {
        Objects.requireNonNull(id, "id");
        return combine(id.getMostSignificantBits(), id.getLeastSignificantBits());
    }

    public static long of(long base, String label) {
        return combine(base, of(label));
    }

    public static long random() {
        return mix(ENTROPY.nextLong() ^ System.nanoTime());
    }
}
