package com.poptrain.innerdemons.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SubscriptionGroup implements Subscription {

    private final List<Subscription> members = new ArrayList<>();
    private boolean active = true;

    public <T extends Subscription> T add(T subscription) {
        Objects.requireNonNull(subscription, "subscription");
        if (!active) {
            subscription.cancel();
            return subscription;
        }
        members.removeIf(s -> !s.isActive());
        members.add(subscription);
        return subscription;
    }

    public int size() {
        members.removeIf(s -> !s.isActive());
        return members.size();
    }

    public void clear() {
        List<Subscription> snapshot = new ArrayList<>(members);
        members.clear();
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            snapshot.get(i).cancel();
        }
    }

    @Override
    public void cancel() {
        if (!active) {
            return;
        }
        active = false;
        clear();
    }

    @Override
    public boolean isActive() {
        return active;
    }
}
