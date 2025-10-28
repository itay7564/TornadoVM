/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.common;

/**
 * Data transfer object representing a placeholder parameter reference captured at TaskGraph snapshot time.
 * Contains metadata about a {@link uk.ac.manchester.tornado.api.Param} used in task definitions or transfer directives.
 */
public final class PlaceholderRef {
    private final String name;
    private final Class<?> type;
    private final int index;
    private final String role;

    /**
     * Creates a new PlaceholderRef.
     *
     * @param name  The parameter name
     * @param type  The parameter type
     * @param index The positional index of the parameter (0-based)
     * @param role  The role/context (e.g., "task", "transferIn", "transferOut", "consume", "persist")
     */
    public PlaceholderRef(String name, Class<?> type, int index, String role) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must be non-empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must be non-null");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (role == null || role.isEmpty()) {
            throw new IllegalArgumentException("role must be non-empty");
        }
        this.name = name;
        this.type = type;
        this.index = index;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "PlaceholderRef{name='" + name + "', type=" + type.getSimpleName() + ", index=" + index + ", role='" + role + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaceholderRef that = (PlaceholderRef) o;
        return index == that.index && name.equals(that.name) && type.equals(that.type) && role.equals(that.role);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + index;
        result = 31 * result + role.hashCode();
        return result;
    }
}
