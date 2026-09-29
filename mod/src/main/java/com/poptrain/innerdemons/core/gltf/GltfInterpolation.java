package com.poptrain.innerdemons.core.gltf;

public enum GltfInterpolation {
    LINEAR,
    STEP,
    CUBICSPLINE;

    static GltfInterpolation fromJson(String value) {
        if (value == null) {
            return LINEAR;
        }
        return switch (value) {
            case "LINEAR" -> LINEAR;
            case "STEP" -> STEP;
            case "CUBICSPLINE" -> CUBICSPLINE;
            default -> throw new GltfException("Unknown interpolation: " + value);
        };
    }
}
