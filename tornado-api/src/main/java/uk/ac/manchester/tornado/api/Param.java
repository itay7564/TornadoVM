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
    
    /**
     * Returns true if this is a placeholder that needs runtime binding.
     */
    default boolean isPlaceholder() {
        return this instanceof Placeholder;
    }
    
    /**
     * Returns the concrete value if this is a DefaultParam, otherwise null.
     */
    default T getValue() {
        return null;
    }

    static <T> Param<T> in(String name, Class<T> type) {
        return new Placeholder<>(name, type, Access.READ_ONLY);
    }

    static <T> Param<T> out(String name, Class<T> type) {
        return new Placeholder<>(name, type, Access.WRITE_ONLY);
    }

    static <T> Param<T> inOut(String name, Class<T> type) {
        return new Placeholder<>(name, type, Access.READ_WRITE);
    }
    
    /**
     * Wraps a concrete value as a Param for use in task definitions.
     * This is used internally to maintain API compatibility.
     */
    @SuppressWarnings("unchecked")
    static <T> Param<T> defaultParam(T value) {
        return new DefaultParam<>((Class<T>) value.getClass(), value);
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
    
    /**
     * DefaultParam wraps a concrete value as a Param.
     * Used internally to maintain API compatibility when concrete values are passed to task().
     */
    final class DefaultParam<T> implements Param<T> {
        private final Class<T> type;
        private final T value;
        
        private DefaultParam(Class<T> type, T value) {
            if (type == null) {
                throw new IllegalArgumentException("DefaultParam type must be non-null");
            }
            this.type = type;
            this.value = value;
        }
        
        @Override
        public String name() {
            return null; // DefaultParam doesn't have a name
        }
        
        @Override
        public Class<T> type() {
            return type;
        }
        
        @Override
        public Access access() {
            return Access.READ_WRITE; // Default access for concrete values
        }
        
        @Override
        public T getValue() {
            return value;
        }
        
        @Override
        public boolean isPlaceholder() {
            return false;
        }
        
        @Override
        public String toString() {
            return "DefaultParam[" + type.getSimpleName() + "=" + value + "]";
        }
    }
}