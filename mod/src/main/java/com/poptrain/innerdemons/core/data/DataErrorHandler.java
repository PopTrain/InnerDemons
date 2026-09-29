package com.poptrain.innerdemons.core.data;

public interface DataErrorHandler {

    void onDataError(DataContainer container, String where, RuntimeException error);
}
