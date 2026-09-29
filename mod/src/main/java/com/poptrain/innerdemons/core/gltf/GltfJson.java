package com.poptrain.innerdemons.core.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class GltfJson {

    private GltfJson() {
    }

    static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsInt();
    }

    static String getString(JsonObject obj, String key, String fallback) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsString();
    }

    static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsBoolean();
    }

    static JsonArray getArray(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    static float[] getFloats(JsonObject obj, String key, float[] fallback) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonArray()) {
            return fallback.clone();
        }
        JsonArray arr = e.getAsJsonArray();
        if (arr.size() != fallback.length) {
            throw new GltfException("Expected " + fallback.length + " numbers for '" + key + "', got " + arr.size());
        }
        float[] out = new float[arr.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = arr.get(i).getAsFloat();
        }
        return out;
    }

    static int[] getInts(JsonObject obj, String key) {
        JsonArray arr = getArray(obj, key);
        int[] out = new int[arr.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = arr.get(i).getAsInt();
        }
        return out;
    }
}
