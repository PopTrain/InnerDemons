package com.poptrain.innerdemons.core.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class CompositeConditions {

    private static final Condition<Object> ALWAYS = new Constant(true);
    private static final Condition<Object> NEVER = new Constant(false);

    private CompositeConditions() {
    }

    @SuppressWarnings("unchecked")
    static <T> Condition<T> always() {
        return (Condition<T>) ALWAYS;
    }

    @SuppressWarnings("unchecked")
    static <T> Condition<T> never() {
        return (Condition<T>) NEVER;
    }

    static <T> Condition<T> all(List<? extends Condition<? super T>> conditions) {
        List<Condition<? super T>> flat = flatten(conditions, All.class);
        if (flat.isEmpty()) {
            return always();
        }
        return new All<>(flat);
    }

    static <T> Condition<T> any(List<? extends Condition<? super T>> conditions) {
        List<Condition<? super T>> flat = flatten(conditions, Any.class);
        if (flat.isEmpty()) {
            return never();
        }
        return new Any<>(flat);
    }

    static <T> Condition<T> atLeast(int required, List<? extends Condition<? super T>> conditions) {
        List<Condition<? super T>> copy = copy(conditions);
        if (required > copy.size()) {
            throw new IllegalArgumentException("atLeast(" + required + ") needs at least " + required
                    + " conditions but got " + copy.size());
        }
        if (required <= 0) {
            return always();
        }
        return new AtLeast<>(required, copy);
    }

    private static <T> List<Condition<? super T>> copy(List<? extends Condition<? super T>> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        List<Condition<? super T>> result = new ArrayList<>(conditions.size());
        for (Condition<? super T> c : conditions) {
            result.add(Objects.requireNonNull(c, "condition"));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<Condition<? super T>> flatten(List<? extends Condition<? super T>> conditions,
                                                        Class<?> groupType) {
        List<Condition<? super T>> result = new ArrayList<>();
        for (Condition<? super T> c : copy(conditions)) {
            if (c == ALWAYS && groupType == All.class || c == NEVER && groupType == Any.class) {
                continue;
            }
            if (groupType.isInstance(c)) {
                for (Condition<?> child : ((Group<?>) c).children()) {
                    result.add((Condition<? super T>) child);
                }
            } else {
                result.add(c);
            }
        }
        return List.copyOf(result);
    }

    private static String join(String label, List<? extends Condition<?>> children) {
        return children.stream().map(Condition::describe).collect(Collectors.joining(", ", label + "(", ")"));
    }

    private interface Group<T> {

        List<Condition<? super T>> children();
    }

    private record Constant(boolean value) implements Condition<Object> {

        @Override
        public ConditionResult evaluate(Object context) {
            return ConditionResult.of(value, "never");
        }

        @Override
        public String describe() {
            return value ? "always" : "never";
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    record Simple<T>(String name, Predicate<? super T> test) implements Condition<T> {

        @Override
        public ConditionResult evaluate(T context) {
            return ConditionResult.of(test.test(context), name);
        }

        @Override
        public String describe() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    record All<T>(List<Condition<? super T>> children) implements Condition<T>, Group<T> {

        @Override
        public ConditionResult evaluate(T context) {
            for (Condition<? super T> child : children) {
                ConditionResult result = child.evaluate(context);
                if (result.failed()) {
                    return result;
                }
            }
            return ConditionResult.pass();
        }

        @Override
        public String describe() {
            return join("all", children);
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    record Any<T>(List<Condition<? super T>> children) implements Condition<T>, Group<T> {

        @Override
        public ConditionResult evaluate(T context) {
            List<String> failures = new ArrayList<>(children.size());
            for (Condition<? super T> child : children) {
                ConditionResult result = child.evaluate(context);
                if (result.passed()) {
                    return result;
                }
                failures.add(result.reason());
            }
            return ConditionResult.fail("none of [" + String.join(" | ", failures) + "]");
        }

        @Override
        public String describe() {
            return join("any", children);
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    record AtLeast<T>(int required, List<Condition<? super T>> children) implements Condition<T> {

        @Override
        public ConditionResult evaluate(T context) {
            int passed = 0;
            int remaining = children.size();
            List<String> failures = new ArrayList<>();
            for (Condition<? super T> child : children) {
                remaining--;
                ConditionResult result = child.evaluate(context);
                if (result.passed()) {
                    if (++passed >= required) {
                        return ConditionResult.pass();
                    }
                } else {
                    failures.add(result.reason());
                    if (passed + remaining < required) {
                        break;
                    }
                }
            }
            return ConditionResult.fail("needed " + required + " of " + children.size() + " but failed ["
                    + String.join(" | ", failures) + "]");
        }

        @Override
        public String describe() {
            return join("atLeast" + required, children);
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    record Not<T>(Condition<T> inner) implements Condition<T> {

        @Override
        public ConditionResult evaluate(T context) {
            return ConditionResult.of(inner.evaluate(context).failed(), describe());
        }

        @Override
        public String describe() {
            return "not " + inner.describe();
        }

        @Override
        public Condition<T> negate() {
            return inner;
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    record Named<T>(String name, Condition<T> inner) implements Condition<T> {

        @Override
        public ConditionResult evaluate(T context) {
            ConditionResult result = inner.evaluate(context);
            if (result.passed()) {
                return result;
            }
            return ConditionResult.fail(result.reason().equals(name) ? name : name + ": " + result.reason());
        }

        @Override
        public String describe() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    record Adapted<U, T>(Condition<T> inner, Function<? super U, ? extends T> mapper) implements Condition<U> {

        @Override
        public ConditionResult evaluate(U context) {
            return inner.evaluate(mapper.apply(context));
        }

        @Override
        public String describe() {
            return inner.describe();
        }

        @Override
        public String toString() {
            return describe();
        }
    }
}
