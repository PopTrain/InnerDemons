package com.poptrain.innerdemons.core.data;

import com.poptrain.innerdemons.core.random.PityCounter;
import com.poptrain.innerdemons.core.random.RngState;
import com.poptrain.innerdemons.core.random.SeededRng;
import com.poptrain.innerdemons.core.schedule.SchedulerSnapshot;
import com.poptrain.innerdemons.core.schedule.TaskSnapshot;
import com.poptrain.innerdemons.core.statemachine.StateSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class DataSerializers {

    public static final DataSerializer<Boolean> BOOL = DataSerializer.of(v -> v, DataSerializers::asBoolean);
    public static final DataSerializer<Integer> INT = DataSerializer.of(v -> v, t -> asNumber(t).intValue());
    public static final DataSerializer<Long> LONG = DataSerializer.of(v -> v, t -> asNumber(t).longValue());
    public static final DataSerializer<Float> FLOAT = DataSerializer.of(v -> v, t -> asNumber(t).floatValue());
    public static final DataSerializer<Double> DOUBLE = DataSerializer.of(v -> v, t -> asNumber(t).doubleValue());
    public static final DataSerializer<String> STRING = DataSerializer.of(v -> v, DataSerializers::asString);
    public static final DataSerializer<UUID> UUID_STRING = STRING.xmap(UUID::toString, UUID::fromString);
    public static final DataSerializer<int[]> INT_ARRAY = DataSerializer.of(int[]::clone, t -> ((int[]) t).clone());
    public static final DataSerializer<long[]> LONG_ARRAY = DataSerializer.of(long[]::clone, t -> ((long[]) t).clone());

    public static final DataSerializer<RngState> RNG_STATE = DataSerializer.of(
            s -> map("Seed", s.seed(), "Lo", s.lo(), "Hi", s.hi()),
            t -> {
                Map<String, Object> m = asMap(t);
                return new RngState(asNumber(m.get("Seed")).longValue(), asNumber(m.get("Lo")).longValue(), asNumber(m.get("Hi")).longValue());
            });

    public static final DataSerializer<SeededRng> SEEDED_RNG = RNG_STATE.xmap(SeededRng::state, SeededRng::from);

    public static final DataSerializer<StateSnapshot> STATE_SNAPSHOT = DataSerializer.of(
            s -> map("Path", new ArrayList<Object>(s.path()), "Ticks", new ArrayList<Object>(s.ticks()), "History", new LinkedHashMap<String, Object>(s.history())),
            t -> {
                Map<String, Object> m = asMap(t);
                List<String> path = new ArrayList<>();
                for (Object o : asList(m.getOrDefault("Path", List.of()))) {
                    path.add(asString(o));
                }
                List<Integer> ticks = new ArrayList<>();
                for (Object o : asList(m.getOrDefault("Ticks", List.of()))) {
                    ticks.add(asNumber(o).intValue());
                }
                Map<String, String> history = new LinkedHashMap<>();
                asMap(m.getOrDefault("History", Map.of())).forEach((k, v) -> history.put(k, asString(v)));
                return new StateSnapshot(path, ticks, history);
            });

    public static final DataSerializer<TaskSnapshot> TASK_SNAPSHOT = DataSerializer.of(
            s -> {
                Map<String, Object> m = map("Key", s.key(), "Remaining", s.remaining(), "Timeout", s.timeout(),
                        "PeriodMin", s.periodMin(), "PeriodMax", s.periodMax(), "RunsLeft", s.runsLeft(),
                        "Runs", s.runCount(), "Priority", s.priority());
                if (s.name() != null) {
                    m.put("Name", s.name());
                }
                return m;
            },
            t -> {
                Map<String, Object> m = asMap(t);
                return new TaskSnapshot(asString(m.get("Key")), m.containsKey("Name") ? asString(m.get("Name")) : null,
                        asNumber(m.get("Remaining")).intValue(), asNumber(m.get("Timeout")).intValue(),
                        asNumber(m.get("PeriodMin")).intValue(), asNumber(m.get("PeriodMax")).intValue(),
                        asNumber(m.get("RunsLeft")).intValue(), asNumber(m.get("Runs")).intValue(),
                        asString(m.get("Priority")));
            });

    public static final DataSerializer<SchedulerSnapshot> SCHEDULER_SNAPSHOT = DataSerializer.of(
            s -> map("Clock", s.clock(), "Tasks", listOf(TASK_SNAPSHOT).encode(s.tasks())),
            t -> {
                Map<String, Object> m = asMap(t);
                return new SchedulerSnapshot(asNumber(m.getOrDefault("Clock", 0L)).longValue(),
                        listOf(TASK_SNAPSHOT).decode(m.getOrDefault("Tasks", List.of())));
            });

    private DataSerializers() {
    }

    public static <E extends Enum<E>> DataSerializer<E> enumOf(Class<E> type) {
        Objects.requireNonNull(type, "type");
        return DataSerializer.of(Enum::name, t -> {
            String name = asString(t);
            try {
                return Enum.valueOf(type, name);
            } catch (IllegalArgumentException e) {
                return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
            }
        });
    }

    public static <T> DataSerializer<List<T>> listOf(DataSerializer<T> element) {
        Objects.requireNonNull(element, "element");
        return DataSerializer.of(list -> {
            List<Object> out = new ArrayList<>(list.size());
            for (T value : list) {
                out.add(element.encode(value));
            }
            return out;
        }, t -> {
            List<Object> in = asList(t);
            List<T> out = new ArrayList<>(in.size());
            for (Object o : in) {
                out.add(element.decode(o));
            }
            return Collections.unmodifiableList(out);
        });
    }

    public static <T> DataSerializer<Set<T>> setOf(DataSerializer<T> element) {
        return listOf(element).xmap(ArrayList::new, list -> Collections.unmodifiableSet(new LinkedHashSet<>(list)));
    }

    public static <T> DataSerializer<Map<String, T>> mapOf(DataSerializer<T> value) {
        Objects.requireNonNull(value, "value");
        return DataSerializer.of(map -> {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(k, value.encode(v)));
            return out;
        }, t -> {
            Map<String, T> out = new LinkedHashMap<>();
            asMap(t).forEach((k, v) -> out.put(k, value.decode(v)));
            return Collections.unmodifiableMap(out);
        });
    }

    public static <T> DataSerializer<Optional<T>> optionalOf(DataSerializer<T> value) {
        Objects.requireNonNull(value, "value");
        return DataSerializer.of(
                opt -> opt.<Object>map(v -> map("Value", value.encode(v))).orElseGet(LinkedHashMap::new),
                t -> {
                    Map<String, Object> m = asMap(t);
                    return m.containsKey("Value") ? Optional.of(value.decode(m.get("Value"))) : Optional.empty();
                });
    }

    public static DataSerializer<PityCounter> pityCounter(Supplier<PityCounter> factory) {
        Objects.requireNonNull(factory, "factory");
        return DataSerializer.of(p -> p.failures(), t -> {
            PityCounter counter = factory.get();
            counter.setFailures(asNumber(t).intValue());
            return counter;
        });
    }

    public static Map<String, Object> map(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("map(...) needs key/value pairs");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null) {
                out.put((String) keysAndValues[i], value);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object tree) {
        if (tree instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new DataException("Expected a map but found " + describe(tree));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object tree) {
        if (tree instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new DataException("Expected a list but found " + describe(tree));
    }

    public static Number asNumber(Object tree) {
        if (tree instanceof Number number) {
            return number;
        }
        if (tree instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        throw new DataException("Expected a number but found " + describe(tree));
    }

    public static boolean asBoolean(Object tree) {
        if (tree instanceof Boolean bool) {
            return bool;
        }
        if (tree instanceof Number number) {
            return number.intValue() != 0;
        }
        throw new DataException("Expected a boolean but found " + describe(tree));
    }

    public static String asString(Object tree) {
        if (tree instanceof String string) {
            return string;
        }
        throw new DataException("Expected a string but found " + describe(tree));
    }

    private static String describe(Object tree) {
        return tree == null ? "nothing" : tree.getClass().getSimpleName() + " " + tree;
    }
}
