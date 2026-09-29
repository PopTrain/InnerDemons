package com.poptrain.innerdemons.core.gltf;

import java.util.List;

public final class GltfAnimation {

    private final String name;
    private final List<GltfChannel> channels;
    private final float duration;

    GltfAnimation(String name, List<GltfChannel> channels) {
        this.name = name;
        this.channels = List.copyOf(channels);
        float end = 0f;
        for (GltfChannel channel : this.channels) {
            end = Math.max(end, channel.endTime());
        }
        this.duration = end;
    }

    public String name() {
        return name;
    }

    public List<GltfChannel> channels() {
        return channels;
    }

    public float duration() {
        return duration;
    }

    public void apply(float time, GltfPose pose) {
        float[] data = pose.data();
        float[] morph = pose.morphData();
        for (GltfChannel channel : channels) {
            int node = channel.node();
            if (node < 0 || node >= pose.nodeCount()) {
                continue;
            }
            if (channel.path() == GltfChannel.Path.WEIGHTS) {
                int offset = channel.weightOffset();
                if (offset >= 0 && offset + channel.components() <= morph.length) {
                    channel.sample(time, morph, offset);
                }
            } else {
                channel.sample(time, data, node * GltfPose.STRIDE + channel.path().poseOffset());
            }
        }
    }

    public float localTime(float elapsed, boolean loop) {
        if (duration <= 0f) {
            return 0f;
        }
        if (loop) {
            float t = elapsed % duration;
            return t < 0f ? t + duration : t;
        }
        return Math.max(0f, Math.min(elapsed, duration));
    }
}
