package com.poptrain.innerdemons.core.random;

public record PityRule(double baseChance, int softPityAfter, double softPityStep, int hardPityAt) {

    public PityRule {
        if (!Double.isFinite(baseChance) || baseChance < 0 || baseChance > 1) {
            throw new IllegalArgumentException("baseChance must be in [0, 1] but was " + baseChance);
        }
        if (softPityAfter < 0) {
            throw new IllegalArgumentException("softPityAfter must be >= 0 but was " + softPityAfter);
        }
        if (!Double.isFinite(softPityStep) || softPityStep < 0) {
            throw new IllegalArgumentException("softPityStep must be finite and >= 0 but was " + softPityStep);
        }
        if (hardPityAt < 0) {
            throw new IllegalArgumentException("hardPityAt must be >= 0 but was " + hardPityAt);
        }
    }

    public static PityRule none(double baseChance) {
        return new PityRule(baseChance, 0, 0, 0);
    }

    public static PityRule hard(double baseChance, int hardPityAt) {
        return new PityRule(baseChance, 0, 0, hardPityAt);
    }

    public static PityRule soft(double baseChance, int softPityAfter, double softPityStep) {
        return new PityRule(baseChance, softPityAfter, softPityStep, 0);
    }

    public double chanceAfter(int failures) {
        if (hardPityAt > 0 && failures + 1 >= hardPityAt) {
            return 1.0;
        }
        double chance = baseChance;
        if (softPityStep > 0 && failures >= softPityAfter) {
            chance += (failures - softPityAfter + 1) * softPityStep;
        }
        return Math.min(1.0, chance);
    }
}
