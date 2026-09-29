package com.poptrain.innerdemons.core.data;

public interface Attachment<H> {

    default void onAttached(H holder) {
    }

    default void onDetached(H holder) {
    }
}
