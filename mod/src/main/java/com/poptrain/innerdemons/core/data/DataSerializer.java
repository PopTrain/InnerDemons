package com.poptrain.innerdemons.core.data;

import java.util.Objects;
import java.util.function.Function;

public interface DataSerializer<T> {

    Object encode(T value);

    T decode(Object tree);

    static <T> DataSerializer<T> of(Function<? super T, Object> encoder, Function<Object, ? extends T> decoder) {
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(decoder, "decoder");
        return new DataSerializer<>() {
            @Override
            public Object encode(T value) {
                return encoder.apply(value);
            }

            @Override
            public T decode(Object tree) {
                return decoder.apply(tree);
            }
        };
    }

    default <U> DataSerializer<U> xmap(Function<? super U, ? extends T> to, Function<? super T, ? extends U> from) {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(from, "from");
        DataSerializer<T> self = this;
        return new DataSerializer<>() {
            @Override
            public Object encode(U value) {
                return self.encode(to.apply(value));
            }

            @Override
            public U decode(Object tree) {
                return from.apply(self.decode(tree));
            }
        };
    }
}
