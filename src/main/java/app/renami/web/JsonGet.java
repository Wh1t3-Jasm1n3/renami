package app.renami.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Map;

/** GET-only seam over {@link HttpJson} so JSON transports can be stubbed in tests. */
@FunctionalInterface
public interface JsonGet {
    JsonObject get(String url, Map<String, String> headers) throws IOException;

    /**
     * Same request, answered as a raw JSON tree: some providers (TVmaze) return a
     * bare array rather than an object, which {@link #get} cannot represent.
     */
    default JsonElement getTree(String url, Map<String, String> headers) throws IOException {
        throw new UnsupportedOperationException("array responses not supported by this transport");
    }
}
