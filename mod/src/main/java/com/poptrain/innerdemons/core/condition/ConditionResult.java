package com.poptrain.innerdemons.core.condition;

import java.util.Objects;
import java.util.Optional;

public record ConditionResult(boolean passed, String reason) {

    private static final ConditionResult PASS = new ConditionResult(true, null);

    public ConditionResult {
        if (!passed) {
            Objects.requireNonNull(reason, "reason");
        }
    }

    public static ConditionResult pass() {
        return PASS;
    }

    public static ConditionResult fail(String reason) {
        return new ConditionResult(false, reason);
    }

    public static ConditionResult of(boolean passed, String reasonIfFailed) {
        return passed ? PASS : fail(reasonIfFailed);
    }

    public boolean failed() {
        return !passed;
    }

    public Optional<String> failure() {
        return passed ? Optional.empty() : Optional.of(reason);
    }
}
