package com.poptrain.innerdemons.core.gltf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class GltfAnimationController {

    public enum Playback {
        LOOP,
        HOLD_LAST_FRAME
    }

    public static final float DEFAULT_FADE_SECONDS = 0.2f;

    private record Layer(String animation, Playback playback, float startTime, float speed) {

        float elapsed(float now) {
            return (now - startTime) * speed;
        }
    }

    private static final class Overlay {
        final String slot;
        final Layer layer;
        final float fadeInStart;
        final float fadeIn;
        float fadeOutStart = Float.NaN;
        float fadeOut;

        Overlay(String slot, Layer layer, float fadeInStart, float fadeIn) {
            this.slot = slot;
            this.layer = layer;
            this.fadeInStart = fadeInStart;
            this.fadeIn = fadeIn;
        }

        boolean stopping() {
            return !Float.isNaN(fadeOutStart);
        }

        float weight(float now) {
            float in = fadeIn <= 0f ? 1f : smooth((now - fadeInStart) / fadeIn);
            if (!stopping()) {
                return in;
            }
            float out = fadeOut <= 0f ? 0f : 1f - smooth((now - fadeOutStart) / fadeOut);
            return Math.min(in, out);
        }

        boolean done(float now) {
            return stopping() && (fadeOut <= 0f || now - fadeOutStart >= fadeOut);
        }
    }

    private final List<Overlay> overlays = new ArrayList<>();
    private GltfPose overlayScratch;
    private Layer current;
    private Layer previous;
    private float fadeStart;
    private float fadeDuration;
    private float now;
    private GltfPose scratch;

    public void setTime(float seconds) {
        this.now = seconds;
    }

    public float time() {
        return now;
    }

    public void play(String animation) {
        play(animation, Playback.LOOP, DEFAULT_FADE_SECONDS, 1f);
    }

    public void playOnce(String animation) {
        play(animation, Playback.HOLD_LAST_FRAME, DEFAULT_FADE_SECONDS, 1f);
    }

    public void play(String animation, Playback playback, float fadeSeconds, float speed) {
        Objects.requireNonNull(animation, "animation");
        if (current != null && current.animation.equals(animation) && current.playback == playback) {
            if (current.speed != speed) {
                float elapsed = current.elapsed(now);
                current = new Layer(animation, playback, speed == 0f ? now : now - elapsed / speed, speed);
            }
            return;
        }
        start(animation, playback, fadeSeconds, speed);
    }

    public void restart(String animation, Playback playback, float fadeSeconds, float speed) {
        Objects.requireNonNull(animation, "animation");
        start(animation, playback, fadeSeconds, speed);
    }

    public void stop(float fadeSeconds) {
        if (current == null) {
            return;
        }
        previous = fadeSeconds > 0f ? current : null;
        fadeStart = now;
        fadeDuration = fadeSeconds;
        current = null;
    }

    private void start(String animation, Playback playback, float fadeSeconds, float speed) {
        if (current != null && fadeSeconds > 0f) {
            previous = current;
            fadeStart = now;
            fadeDuration = fadeSeconds;
        } else {
            previous = null;
        }
        current = new Layer(animation, playback, now, speed);
    }

    public void playOverlay(String slot, String animation) {
        playOverlay(slot, animation, Playback.LOOP, DEFAULT_FADE_SECONDS, 1f);
    }

    public void playOverlay(String slot, String animation, Playback playback, float fadeSeconds, float speed) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(animation, "animation");
        Overlay active = activeOverlay(slot);
        if (active != null && active.layer.animation.equals(animation) && active.layer.playback == playback && active.layer.speed == speed) {
            return;
        }
        startOverlay(slot, active, animation, playback, fadeSeconds, speed);
    }

    public void restartOverlay(String slot, String animation, Playback playback, float fadeSeconds, float speed) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(animation, "animation");
        startOverlay(slot, activeOverlay(slot), animation, playback, fadeSeconds, speed);
    }

    public void stopOverlay(String slot, float fadeSeconds) {
        Overlay active = activeOverlay(slot);
        if (active != null) {
            active.fadeOutStart = now;
            active.fadeOut = fadeSeconds;
        }
    }

    public String overlayAnimation(String slot) {
        Overlay active = activeOverlay(slot);
        return active == null ? null : active.layer.animation;
    }

    public boolean isOverlayFinished(String slot, GltfModel model) {
        Overlay active = activeOverlay(slot);
        if (active == null) {
            return true;
        }
        if (active.layer.playback == Playback.LOOP) {
            return false;
        }
        return model.animation(active.layer.animation)
                .map(anim -> active.layer.elapsed(now) >= anim.duration())
                .orElse(true);
    }

    private void startOverlay(String slot, Overlay active, String animation, Playback playback, float fadeSeconds, float speed) {
        if (active != null) {
            active.fadeOutStart = now;
            active.fadeOut = fadeSeconds;
        }
        overlays.add(new Overlay(slot, new Layer(animation, playback, now, speed), now, fadeSeconds));
    }

    private Overlay activeOverlay(String slot) {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            Overlay overlay = overlays.get(i);
            if (overlay.slot.equals(slot) && !overlay.stopping()) {
                return overlay;
            }
        }
        return null;
    }

    public String currentAnimation() {
        return current == null ? null : current.animation;
    }

    public boolean isPlaying(String animation) {
        return current != null && current.animation.equals(animation);
    }

    public float elapsed() {
        return current == null ? 0f : current.elapsed(now);
    }

    public boolean isFinished(GltfModel model) {
        if (current == null) {
            return true;
        }
        if (current.playback == Playback.LOOP) {
            return false;
        }
        return model.animation(current.animation)
                .map(anim -> current.elapsed(now) >= anim.duration())
                .orElse(true);
    }

    public void apply(GltfModel model, GltfPose pose) {
        pose.resetToRest(model);
        float fadeWeight = fadeWeight();
        if (previous != null && fadeWeight < 1f) {
            if (scratch == null || !scratch.fits(model)) {
                scratch = new GltfPose(model);
            }
            scratch.resetToRest(model);
            sampleLayer(model, previous, scratch);
            pose.copyFrom(scratch);
            scratch.resetToRest(model);
            if (current != null) {
                sampleLayer(model, current, scratch);
            }
            pose.blendTowards(scratch, fadeWeight);
        } else {
            previous = null;
            if (current != null) {
                sampleLayer(model, current, pose);
            }
        }
        applyOverlays(model, pose);
    }

    private void applyOverlays(GltfModel model, GltfPose pose) {
        if (overlays.isEmpty()) {
            return;
        }
        if (overlayScratch == null || !overlayScratch.fits(model)) {
            overlayScratch = new GltfPose(model);
        }
        Iterator<Overlay> it = overlays.iterator();
        while (it.hasNext()) {
            Overlay overlay = it.next();
            if (overlay.done(now)) {
                it.remove();
                continue;
            }
            float weight = overlay.weight(now);
            if (weight <= 0f) {
                continue;
            }
            overlayScratch.copyFrom(pose);
            sampleLayer(model, overlay.layer, overlayScratch);
            pose.blendTowards(overlayScratch, weight);
        }
    }

    private float fadeWeight() {
        if (previous == null || fadeDuration <= 0f) {
            return 1f;
        }
        return smooth((now - fadeStart) / fadeDuration);
    }

    private static float smooth(float w) {
        if (w <= 0f) {
            return 0f;
        }
        if (w >= 1f) {
            return 1f;
        }
        return w * w * (3f - 2f * w);
    }

    private void sampleLayer(GltfModel model, Layer layer, GltfPose target) {
        model.animation(layer.animation).ifPresent(anim ->
                anim.apply(anim.localTime(layer.elapsed(now), layer.playback == Playback.LOOP), target));
    }
}
