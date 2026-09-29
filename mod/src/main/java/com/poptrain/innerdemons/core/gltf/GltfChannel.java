package com.poptrain.innerdemons.core.gltf;

public final class GltfChannel {

    public enum Path {
        TRANSLATION(3, GltfPose.TRANSLATION),
        ROTATION(4, GltfPose.ROTATION),
        SCALE(3, GltfPose.SCALE),
        WEIGHTS(-1, -1);

        private final int components;
        private final int poseOffset;

        Path(int components, int poseOffset) {
            this.components = components;
            this.poseOffset = poseOffset;
        }

        public int components() {
            return components;
        }

        int poseOffset() {
            return poseOffset;
        }
    }

    private final int node;
    private final Path path;
    private final GltfInterpolation interpolation;
    private final float[] times;
    private final float[] values;
    private final int comps;
    private final int weightOffset;

    GltfChannel(int node, Path path, GltfInterpolation interpolation, float[] times, float[] values) {
        this(node, path, interpolation, times, values, path.components, -1);
    }

    GltfChannel(int node, Path path, GltfInterpolation interpolation, float[] times, float[] values, int comps, int weightOffset) {
        if (comps <= 0) {
            throw new GltfException("Animation channel for path " + path + " has no components");
        }
        int stride = comps * (interpolation == GltfInterpolation.CUBICSPLINE ? 3 : 1);
        if (times.length == 0) {
            throw new GltfException("Animation sampler has no keyframes");
        }
        if (values.length < times.length * stride) {
            throw new GltfException("Animation sampler output is shorter than its input for path " + path);
        }
        this.node = node;
        this.path = path;
        this.interpolation = interpolation;
        this.times = times;
        this.values = values;
        this.comps = comps;
        this.weightOffset = weightOffset;
    }

    public int components() {
        return comps;
    }

    int weightOffset() {
        return weightOffset;
    }

    public int node() {
        return node;
    }

    public Path path() {
        return path;
    }

    public GltfInterpolation interpolation() {
        return interpolation;
    }

    public float startTime() {
        return times[0];
    }

    public float endTime() {
        return times[times.length - 1];
    }

    public int keyframeCount() {
        return times.length;
    }

    void sample(float time, float[] out, int outOffset) {
        int last = times.length - 1;
        if (time <= times[0] || last == 0) {
            copyKey(0, out, outOffset);
            return;
        }
        if (time >= times[last]) {
            copyKey(last, out, outOffset);
            return;
        }
        int k = findKey(time);
        float t0 = times[k];
        float t1 = times[k + 1];
        float span = t1 - t0;
        float t = span <= 0f ? 0f : (time - t0) / span;
        switch (interpolation) {
            case STEP -> copyKey(k, out, outOffset);
            case LINEAR -> {
                if (path == Path.ROTATION) {
                    slerp(values, k * 4, values, (k + 1) * 4, t, out, outOffset);
                } else {
                    for (int c = 0; c < comps; c++) {
                        float a = values[k * comps + c];
                        float b = values[(k + 1) * comps + c];
                        out[outOffset + c] = a + (b - a) * t;
                    }
                }
            }
            case CUBICSPLINE -> {
                float t2 = t * t;
                float t3 = t2 * t;
                float h00 = 2f * t3 - 3f * t2 + 1f;
                float h10 = t3 - 2f * t2 + t;
                float h01 = -2f * t3 + 3f * t2;
                float h11 = t3 - t2;
                int stride = comps * 3;
                int base0 = k * stride;
                int base1 = (k + 1) * stride;
                for (int c = 0; c < comps; c++) {
                    float v0 = values[base0 + comps + c];
                    float outTangent0 = values[base0 + 2 * comps + c];
                    float inTangent1 = values[base1 + c];
                    float v1 = values[base1 + comps + c];
                    out[outOffset + c] = h00 * v0 + h10 * span * outTangent0 + h01 * v1 + h11 * span * inTangent1;
                }
                if (path == Path.ROTATION) {
                    normalizeQuat(out, outOffset);
                }
            }
        }
    }

    private void copyKey(int key, float[] out, int outOffset) {
        int base = interpolation == GltfInterpolation.CUBICSPLINE ? key * comps * 3 + comps : key * comps;
        System.arraycopy(values, base, out, outOffset, comps);
        if (path == Path.ROTATION) {
            normalizeQuat(out, outOffset);
        }
    }

    private int findKey(float time) {
        int lo = 0;
        int hi = times.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= time) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    static void slerp(float[] a, int ao, float[] b, int bo, float t, float[] out, int oo) {
        float ax = a[ao], ay = a[ao + 1], az = a[ao + 2], aw = a[ao + 3];
        float bx = b[bo], by = b[bo + 1], bz = b[bo + 2], bw = b[bo + 3];
        float dot = ax * bx + ay * by + az * bz + aw * bw;
        if (dot < 0f) {
            dot = -dot;
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }
        float s0;
        float s1;
        if (dot > 0.9995f) {
            s0 = 1f - t;
            s1 = t;
        } else {
            float theta = (float) Math.acos(dot);
            float sin = (float) Math.sin(theta);
            s0 = (float) Math.sin((1f - t) * theta) / sin;
            s1 = (float) Math.sin(t * theta) / sin;
        }
        out[oo] = ax * s0 + bx * s1;
        out[oo + 1] = ay * s0 + by * s1;
        out[oo + 2] = az * s0 + bz * s1;
        out[oo + 3] = aw * s0 + bw * s1;
        normalizeQuat(out, oo);
    }

    static void normalizeQuat(float[] q, int o) {
        float len = (float) Math.sqrt(q[o] * q[o] + q[o + 1] * q[o + 1] + q[o + 2] * q[o + 2] + q[o + 3] * q[o + 3]);
        if (len < 1.0e-8f) {
            q[o] = 0f;
            q[o + 1] = 0f;
            q[o + 2] = 0f;
            q[o + 3] = 1f;
            return;
        }
        float inv = 1f / len;
        q[o] *= inv;
        q[o + 1] *= inv;
        q[o + 2] *= inv;
        q[o + 3] *= inv;
    }
}
