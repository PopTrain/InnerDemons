package com.poptrain.innerdemons.core.data;

import java.util.Objects;

public abstract class AbstractDataHolder implements DataHolder {

    private final DataContainer data;

    protected AbstractDataHolder(String name) {
        this(DataContainer.builder(name));
    }

    protected AbstractDataHolder(DataContainer.Builder builder) {
        Objects.requireNonNull(builder, "builder");
        this.data = Objects.requireNonNull(createData(builder.owner(this)), "createData returned null");
    }

    protected DataContainer createData(DataContainer.Builder builder) {
        return builder.build();
    }

    @Override
    public final DataContainer data() {
        return data;
    }

    protected void closeData() {
        data.close();
    }
}
