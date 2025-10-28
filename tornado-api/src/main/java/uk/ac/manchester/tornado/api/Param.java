package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.Access;

/**
 * Typed, named placeholders used to declare kernel parameters and data transfer roles
 * at TaskGraph construction time. They are bound to concrete objects at execution time.
 *
 * This API is backward compatible: existing code that passes concrete objects to
 * TaskGraph.task(...) and transferToDevice/transferToHost(...) continues to work.
 */
public interface Param<T> {
    String name();
    Class<T> type();
    Access access();

    static <T> Param<T> in(String name, Class<T> type) {
        return new Placeholder<>(name, type, Access.READ_ONLY);
    }

    static <T> Param<T> out(String name, Class<T> type) {
        return new Placeholder<>(name, type, Access.WRITE_ONLY);
    }

    static <T> Param<T> inOut(String name, Class<T> type) {
        return new Placeholder<>(name, type, Access.READ_WRITE);
    }

    final class Placeholder<T> implements Param<T> {
        private final String name;
        private final Class<T> type;
        private final Access access;

        private Placeholder(String name, Class<T> type, Access access) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Param name must be non-empty");
            }
            if (type == null) {
                throw new IllegalArgumentException("Param type must be non-null");
            }
            this.name = name;
            this.type = type;
            this.access = access;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public Access access() {
            return access;
        }

        @Override
        public String toString() {
            return "Param[" + name + ":" + type.getSimpleName() + "," + access + "]";
        }
    }
}