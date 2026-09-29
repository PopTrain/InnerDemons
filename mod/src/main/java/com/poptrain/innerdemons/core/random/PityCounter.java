package com.poptrain.innerdemons.core.random;

import java.util.Objects;

public final class PityCounter {

    private final PityRule rule;
    private int failures;

    public PityCounter(PityRule rule) {
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    public PityRule rule() {
        return rule;
    }

    public int failures() {
        return failures;
    }

    public void setFailures(int value) {
        failures = Math.max(0, value);
    }

    public double currentChance() {
        return rule.chanceAfter(failures);
    }

    public boolean roll(Rng rng) {
        boolean success = rng.chance(currentChance());
        record(success);
        return success;
    }

    public void record(boolean success) {
        if (success) {
            failures = 0;
        } else if (failures < Integer.MAX_VALUE) {
            failures++;
        }
    }

    public void reset() {
        failures = 0;
    }

    @Override
    public String toString() {
        return "PityCounter[failures=" + failures + ", chance=" + currentChance() + "]";
    }
}
