package com.poptrain.innerdemons.core.event;

import java.util.Objects;

public interface Subscription {

    void cancel();

    boolean isActive();

    static Subscription of(Runnable onCancel) {
        Objects.requireNonNull(onCancel, "onCancel");
        return new Subscription() {
            private boolean active = true;

            @Override
            public void cancel() {
                if (active) {
                    active = false;
                    onCancel.run();
                }
            }

            @Override
            public boolean isActive() {
                return active;
            }
        };
    }

    static Subscription cancelled() {
        return Cancelled.INSTANCE;
    }

    enum Cancelled implements Subscription {
        INSTANCE;

        @Override
        public void cancel() {
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
