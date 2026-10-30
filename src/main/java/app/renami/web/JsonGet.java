package app.renami.web;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Map;

/** GET-only seam over {@link HttpJson} so JSON transports can be stubbed in tests. */
@FunctionalInterface
public interface JsonGet {
    JsonObject get(String url, Map<String, String> headers) throws IOException;
}
