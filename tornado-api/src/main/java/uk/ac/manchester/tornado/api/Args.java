package uk.ac.manchester.tornado.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-execution bindings mapping placeholder names to concrete objects.
 * Use Args.of(...).and(...).build() to construct immutable bindings.
 */
public final class Args {
    private final Map<String, Object> bindings;

    private Args(Map<String, Object> bindings) {
        this.bindings = bindings;
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(bindings);
    }

    public static Builder of(String name, Object value) {
        return new Builder().and(name, value);
    }

    public static final class Builder {
        private final Map<String, Object> map = new HashMap<>();

        public Builder and(String name, Object value) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("binding name must be non-empty");
            }
            map.put(name, value);
            return this;
        }

        public Args build() {
            return new Args(new HashMap<>(map));
        }

        @Override
        public String toString() {
            return "Args" + map;
        }
    }
}