package com.poptrain.innerdemons.core.gltf;

import java.io.IOException;

@FunctionalInterface
public interface GltfBufferResolver {

    GltfBufferResolver NONE = uri -> {
        throw new IOException("External resources are not available: " + uri);
    };

    byte[] resolve(String relativeUri) throws IOException;
}
