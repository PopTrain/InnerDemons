package com.poptrain.innerdemons.core.data;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface DataHolder {

    DataContainer data();

    default <T> T get(DataKey<T> key) {
        return data().get(key);
    }

    default <T> Optional<T> find(DataKey<T> key) {
        return data().find(key);
    }

    default <T> T getOrDefault(DataKey<T> key, T fallback) {
        return data().getOrDefault(key, fallback);
    }

    default boolean has(DataKey<?> key) {
        return data().has(key);
    }

    default <T> boolean set(DataKey<T> key, T value) {
        return data().set(key, value);
    }

    default <T> T update(DataKey<T> key, UnaryOperator<T> operator) {
        return data().update(key, operator);
    }
}
