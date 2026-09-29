package com.poptrain.innerdemons.core.network;

import com.poptrain.innerdemons.core.data.DataHolder;

public interface SyncedDataHolder extends DataHolder {

    SyncRegistry syncRegistry();
}
