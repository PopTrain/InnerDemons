package com.poptrain.innerdemons.core.random;

import java.util.Objects;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public final class MinecraftRng {

    private MinecraftRng() {
    }

    public static Rng wrap(RandomSource source) {
        Objects.requireNonNull(source, "source");
        if (source instanceof AsRandomSource adapter) {
            return adapter.rng;
        }
        return new FromRandomSource(source);
    }

    public static RandomSource asRandomSource(Rng rng) {
        Objects.requireNonNull(rng, "rng");
        if (rng instanceof FromRandomSource adapter) {
            return adapter.source;
        }
        return new AsRandomSource(rng);
    }

    private static final class FromRandomSource implements Rng {

        private final RandomSource source;

        private FromRandomSource(RandomSource source) {
            this.source = source;
        }

        @Override
        public long nextLong() {
            return source.nextLong();
        }

        @Override
        public Rng split() {
            return new FromRandomSource(source.fork());
        }

        @Override
        public String toString() {
            return "MinecraftRng[" + source + "]";
        }
    }

    private static final class AsRandomSource implements RandomSource {

        private final Rng rng;

        private AsRandomSource(Rng rng) {
            this.rng = rng;
        }

        @Override
        public RandomSource fork() {
            return new AsRandomSource(rng.split());
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            return new XoroshiroRandomSource(rng.nextLong(), rng.nextLong()).forkPositional();
        }

        @Override
        public void setSeed(long seed) {
            if (rng instanceof SeededRng seeded) {
                seeded.reseed(seed);
                return;
            }
            throw new UnsupportedOperationException("setSeed is only supported for SeededRng, not " + rng);
        }

        @Override
        public int nextInt() {
            return rng.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            return rng.nextInt(bound);
        }

        @Override
        public long nextLong() {
            return rng.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            return rng.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return rng.nextFloat();
        }

        @Override
        public double nextDouble() {
            return rng.nextDouble();
        }

        @Override
        public double nextGaussian() {
            return rng.nextGaussian();
        }

        @Override
        public String toString() {
            return "RandomSource[" + rng + "]";
        }
    }
}
