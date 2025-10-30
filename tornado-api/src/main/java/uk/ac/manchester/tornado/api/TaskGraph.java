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
package uk.ac.manchester.tornado.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.manchester.tornado.api.common.PlaceholderRef;
import uk.ac.manchester.tornado.api.common.PrebuiltTaskPackage;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task11;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task12;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task13;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task14;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoTaskRuntimeException;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;
import uk.ac.manchester.tornado.api.runtime.TornadoAPIProvider;

/**
 * Tornado Task Graph API.
 * <p>
 * Task-based parallel API to express methods to be accelerated on any OpenCL,
 * PTX and/or SPIR-V compatible device.
 * </p>
 *
 * @since v0.15
 */
public class TaskGraph implements TaskGraphInterface {

    private static final String ERROR_TASK_NAME_DUPLICATION = //
            "[TornadoVM ERROR]. There are more than 1 tasks with the same task-name. Use different a different task name for each task within " + "a TaskGraph.";

    private final String taskGraphName;
    protected TornadoTaskGraphInterface taskGraphImpl;
    protected HashSet<String> taskNames;
    private final Map<String, List<PlaceholderRef>> placeholderRegistry;

    public TaskGraph(String name) {
        this.taskGraphName = name;
        taskGraphImpl = TornadoAPIProvider.loadScheduleRuntime(name);
        taskNames = new HashSet<>();
        placeholderRegistry = new HashMap<>();
    }

    /**
     * Registers a placeholder parameter reference for a given task or transfer context.
     *
     * @param contextKey The context identifier (e.g., task ID or transfer type)
     * @param param      The Param<?> placeholder instance
     * @param index      The positional index of the parameter
     * @param role       The role/context (e.g., "task", "transferIn", "transferOut")
     */
    private void registerPlaceholder(String contextKey, Param<?> param, int index, String role) {
        PlaceholderRef ref = new PlaceholderRef(param.name(), param.type(), index, role);
        placeholderRegistry.computeIfAbsent(contextKey, k -> new ArrayList<>()).add(ref);
    }

    /**
     * Scans task arguments for Param<?> instances and registers them.
     *
     * @param taskId The task identifier
     * @param args   The task arguments (excluding the code reference)
     */
    private void registerTaskPlaceholders(String taskId, Object... args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Param) {
                registerPlaceholder(taskId, (Param<?>) args[i], i, "task");
            }
        }
    }

    /**
     * Scans transfer directive arguments for Param<?> instances and registers them.
     *
     * @param role    The transfer role (e.g., "transferIn", "transferOut", "consume", "persist")
     * @param objects The objects to transfer
     */
    private void registerTransferPlaceholders(String role, Object... objects) {
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] instanceof Param) {
                String contextKey = role + "_" + ((Param<?>) objects[i]).name();
                registerPlaceholder(contextKey, (Param<?>) objects[i], i, role);
            }
        }
    }

    /**
     * Returns an immutable view of the placeholder registry.
     * The registry maps context keys to lists of placeholder references.
     *
     * @return Immutable map of placeholder references
     */
    Map<String, List<PlaceholderRef>> getPlaceholderRegistry() {
        Map<String, List<PlaceholderRef>> immutableRegistry = new HashMap<>();
        for (Map.Entry<String, List<PlaceholderRef>> entry : placeholderRegistry.entrySet()) {
            immutableRegistry.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutableRegistry);
    }

    /**
     * It adds a task by using a {@link TaskPackage}.
     *
     * @param taskPackage
     *     {@link uk.ac.manchester.tornado.api.common.TaskPackage}
     * @return {@link @TaskGraph}
     */
    @Override
    public TaskGraph addTask(TaskPackage taskPackage) {
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with no parameter.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with one argument
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph task(String id, Task code) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with one parameter.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with one argument
     * @param arg
     *     Argument to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1> TaskGraph task(String id, Task1<T1> code, T1 arg) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with one parameter (placeholder version).
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with one argument
     * @param arg
     *     Placeholder argument to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1> TaskGraph task(String id, Task1<T1> code, Param<T1> arg) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with two parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with two arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2> TaskGraph task(String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with two parameters (placeholder version).
     * Supports any combination of concrete values and Param placeholders.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with two arguments
     * @param arg1
     *     Argument 1 to the method (Param or concrete)
     * @param arg2
     *     Argument 2 to the method (Param or concrete)
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2> TaskGraph task(String id, Task2<T1, T2> code, Object arg1, Object arg2) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Add task with three parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with three arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3> TaskGraph task(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Add task with three parameters (placeholder version).
     * Supports any combination of concrete values and Param placeholders.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with three arguments
     * @param arg1
     *     Argument 1 to the method (Param or concrete)
     * @param arg2
     *     Argument 2 to the method (Param or concrete)
     * @param arg3
     *     Argument 3 to the method (Param or concrete)
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3> TaskGraph task(String id, Task3<T1, T2, T3> code, Object arg1, Object arg2, Object arg3) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with four parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with four arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4> TaskGraph task(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with four parameters (placeholder version).
     */
    @Override
    public <T1, T2, T3, T4> TaskGraph task(String id, Task4<T1, T2, T3, T4> code, Object arg1, Object arg2, Object arg3, Object arg4) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with five parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with five arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5> TaskGraph task(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with six parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with six arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6> TaskGraph task(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with seven parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with seven arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7> TaskGraph task(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with eight parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with eight arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> TaskGraph task(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with nine parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with nine arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskGraph task(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with 10 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskGraph task(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7,
            T8 arg8, T9 arg9, T10 arg10) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * It creates a task with 11 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> TaskGraph task(String id, Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6,
            T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * It creates a task with 12 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @param arg12
     *     Argument 12 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> TaskGraph task(String id, Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * It creates a task with 13 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @param arg12
     *     Argument 12 to the method
     * @param arg13
     *     Argument 13 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> TaskGraph task(String id, Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * It creates a task with 14 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @param arg12
     *     Argument 12 to the method
     * @param arg13
     *     Argument 13 to the method
     * @param arg14
     *     Argument 14 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> TaskGraph task(String id, Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * It creates a task with 15 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 15 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @param arg12
     *     Argument 12 to the method
     * @param arg13
     *     Argument 13 to the method
     * @param arg14
     *     Argument 14 to the method
     * @param arg15
     *     Argument 15 to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskGraph task(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     *
     * @param id
     *     String that represents the task-id.
     * @param entryPoint
     *     Name of the kernel to be launched on the target device.
     * @param filename
     *     String that represents the path to the native source (e.g., the OpenCL C kernel).
     * @param accessorParameters
     *     {@link AccessorParameters} with the type of accessor for each of the input parameters to the low-level kernel.
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph prebuiltTask(String id, String entryPoint, String filename, AccessorParameters accessorParameters) {
        checkTaskName(id);
        TaskPackage prebuiltTask = TaskPackage.createPrebuiltTask(id, entryPoint, filename, accessorParameters);
        taskGraphImpl.addPrebuiltTask(prebuiltTask);
        return this;
    }

    /**
     * Add a pre-built OpenCL task into a task-schedule with atomics region.
     *
     * @param id
     *     Task-id
     * @param entryPoint
     *     Kernel's name of the entry point
     * @param filename
     *     Input OpenCL C Kernel
     * @param accessorParameters
     *     {@link AccessorParameters} with the type of accessor for each of the input parameters to the low-level kernel.
     * @param atomics
     *     Atomics region.
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph prebuiltTask(String id, String entryPoint, String filename, AccessorParameters accessorParameters, int[] atomics) {
        checkTaskName(id);
        PrebuiltTaskPackage prebuiltTask = TaskPackage.createPrebuiltTask(id, entryPoint, filename, accessorParameters);
        prebuiltTask.withAtomics(atomics);
        taskGraphImpl.addPrebuiltTask(prebuiltTask);
        return this;
    }

    /**
     * Obtains the task-schedule name that was assigned.
     *
     * @return {@link String}
     */
    @Override
    public String getTaskGraphName() {
        return taskGraphName;
    }

    /**
     * Tag a set of objects (Java objects) to be transferred to the device. There
     * are three modes:
     *
     * <p>
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#FIRST_EXECUTION}:
     * it transfers data only the first execution of the task-graph (READ ONLY)
     * </p>
     *
     * </p>
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#EVERY_EXECUTION}:
     * it transfers data for every execution of the task-graph (READ/WRITE)
     * </p>
     *
     * @param mode
     *     A mode from
     *     {@link uk.ac.manchester.tornado.api.enums.DataTransferMode}
     * @param objects
     *     List of Java objects (usually arrays) to be transferred to the
     *     device.
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph transferToDevice(final int mode, Object... objects) {
        registerTransferPlaceholders("transferIn", objects);
        taskGraphImpl.transferToDevice(mode, objects);
        return this;
    }

    /**
     * Tag a set of objects to be used directly from the device. It requires
     * objects to be tagged as persisted objects from a previous taskgraph.
     * This method ensures that the specified objects are consumed from the device
     * and are not copied from the host.
     *
     * @param uniqueTaskGraphName
     *     A unique identifier for the task graph.
     * @param objects
     *     List of Java objects (usually arrays) to be consumed from the device.
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph consumeFromDevice(String uniqueTaskGraphName, Object... objects) {
        registerTransferPlaceholders("consume", objects);
        taskGraphImpl.consumeFromDevice(uniqueTaskGraphName, objects);
        return this;
    }

    @Override
    public TaskGraph consumeFromDevice(Object... objects) {
        registerTransferPlaceholders("consume", objects);
        taskGraphImpl.consumeFromDevice(this.taskGraphName, objects);
        return this;
    }

    /**
     * Tag a set of objects (Java objects) to be transferred from the device to the
     * host after the execution completes. There are two modes:
     *
     * <p>
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#EVERY_EXECUTION}:
     * transfers data for every execution of the task-graph (WRITE only)
     * </p>
     *
     * </p>
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#UNDER_DEMAND}: it
     * transfers data only under demand. Data are not transferred unless the
     * execution-plan, an {@link TornadoExecutionPlan} object, invokes the
     * `transferToHost` function. This is used for optimization of data transfers.
     * </p>
     *
     * @param mode
     *     A mode from
     *     {@link uk.ac.manchester.tornado.api.enums.DataTransferMode}
     * @param objects
     *     List of Java objects (usually arrays) to be transferred to the
     *     device.
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph transferToHost(final int mode, Object... objects) {
        registerTransferPlaceholders("transferOut", objects);
        taskGraphImpl.transferToHost(mode, objects);
        return this;
    }

    /**
     * Tags a set of objects to persist on the device without transferring them
     * back to the host after execution.
     *
     * <p>
     * This method marks the objects as available on the device for future taskGraph
     * executions without redundant data transfers. Data is not transferred to
     * the host unless explicitly requested via the execution plan.
     * </p>
     *
     * @param objects
     *     List of Java objects (usually arrays) to persist on the device.
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph persistOnDevice(Object... objects) {
        registerTransferPlaceholders("persist", objects);
        taskGraphImpl.transferToHost(DataTransferMode.UNDER_DEMAND, objects);
        return this;
    }

    /**
     * Function that closes a task-graph definition and creates an immutable
     * task-graph ready for execution.
     *
     * @return {@link ImmutableTaskGraph}
     */
    @Override
    public ImmutableTaskGraph snapshot() {
        TaskGraph cloneTaskGraph = new TaskGraph(this.getTaskGraphName());
        cloneTaskGraph.taskGraphImpl = this.taskGraphImpl.createImmutableTaskGraph();
        cloneTaskGraph.taskNames = this.taskNames;
        // Copy the placeholder registry to the cloned TaskGraph
        cloneTaskGraph.placeholderRegistry.putAll(this.placeholderRegistry);
        
        // Phase 3: Transfer placeholder schema to runtime TornadoTaskGraph
        if (!this.placeholderRegistry.isEmpty()) {
            cloneTaskGraph.taskGraphImpl.setPlaceholderSchema(this.getPlaceholderRegistry());
        }
        
        return new ImmutableTaskGraph(cloneTaskGraph);
    }

    void withoutMemoryLimit() {
        taskGraphImpl.withoutMemoryLimit();
    }

    private void checkTaskName(String id) {
        if (taskNames.contains(id)) {
            throw new TornadoTaskRuntimeException(ERROR_TASK_NAME_DUPLICATION);
        }
        taskNames.add(id);
    }

    void withDevice(TornadoDevice device) {
        taskGraphImpl.setDevice(device);
    }

    void withDevice(String taskName, TornadoDevice device) {
        taskGraphImpl.setDevice(taskName, device);
    }

    void batch(String batchSize) {
        taskGraphImpl.withBatch(batchSize);
    }

    void withMemoryLimit(String memoryLimit) {
        taskGraphImpl.withMemoryLimit(memoryLimit);
    }

    void execute(ExecutorFrame executionPackage) {
        taskGraphImpl.execute(executionPackage).waitOn();
    }

    void withPreCompilation(ExecutorFrame executionPackage) {
        taskGraphImpl.withPreCompilation(executionPackage);
    }

    void dumpProfiles() {
        taskGraphImpl.dumpProfiles();
    }

    void clearProfiles() {
        taskGraphImpl.clearProfiles();
    }

    void freeDeviceMemory() {
        taskGraphImpl.freeDeviceMemory();
    }

    void syncRuntimeTransferToHost(Object... objects) {
        taskGraphImpl.syncRuntimeTransferToHost(objects);
    }

    void syncRuntimeTransferToHost(Object object, long offset, long partialCopySize) {
        taskGraphImpl.syncRuntimeTransferToHost(object, offset, partialCopySize);
    }

    TornadoDevice getDevice() {
        return taskGraphImpl.getDevice();
    }

    void useDefaultThreadScheduler(boolean use) {
        taskGraphImpl.useDefaultThreadScheduler(use);
    }

    boolean isFinished() {
        return taskGraphImpl.isFinished();
    }

    public Set<Object> getArgumentsLookup() {
        return taskGraphImpl.getArgumentsLookup();
    }

    // *************************************************
    // Profiler Interface
    // *************************************************
    long getTotalTime() {
        return taskGraphImpl.getTotalTime();
    }

    long getCompileTime() {
        return taskGraphImpl.getCompileTime();
    }

    long getTornadoCompilerTime() {
        return taskGraphImpl.getTornadoCompilerTime();
    }

    long getDriverInstallTime() {
        return taskGraphImpl.getDriverInstallTime();
    }

    long getDataTransfersTime() {
        return taskGraphImpl.getDataTransfersTime();
    }

    long getWriteTime() {
        return taskGraphImpl.getDeviceWriteTime();
    }

    long getReadTime() {
        return taskGraphImpl.getDeviceReadTime();
    }

    long getDataTransferDispatchTime() {
        return taskGraphImpl.getDataTransferDispatchTime();
    }

    long getKernelDispatchTime() {
        return taskGraphImpl.getKernelDispatchTime();
    }

    long getDeviceKernelTime() {
        return taskGraphImpl.getDeviceKernelTime();
    }

    long getTotalBytesCopyIn() {
        return taskGraphImpl.getTotalBytesCopyIn();
    }

    long getTotalBytesCopyOut() {
        return taskGraphImpl.getTotalBytesCopyOut();
    }

    protected String getProfileLog() {
        return taskGraphImpl.getProfileLog();
    }

    void enableProfiler(ProfilerMode profilerMode) {
        taskGraphImpl.enableProfiler(profilerMode);
    }

    void withConcurrentDevices() {
        taskGraphImpl.withConcurrentDevices();
    }

    void withoutConcurrentDevices() {
        taskGraphImpl.withoutConcurrentDevices();
    }

    void withThreadInfo() {
        taskGraphImpl.withThreadInfo();
    }

    void withoutThreadInfo() {
        taskGraphImpl.withoutThreadInfo();
    }

    void withPrintKernel() {
        taskGraphImpl.withPrintKernel();
    }

    void withoutPrintKernel() {
        taskGraphImpl.withoutPrintKernel();
    }

    void withCompilerFlags(TornadoVMBackendType backendType, String compilerFlags) {
        taskGraphImpl.withCompilerFlags(backendType, compilerFlags);
    }

    void withGridScheduler(GridScheduler gridScheduler) {
        taskGraphImpl.withGridScheduler(gridScheduler);
    }

    long getTotalBytesTransferred() {
        return taskGraphImpl.getTotalBytesTransferred();
    }

    long getTotalDeviceMemoryUsage() {
        return taskGraphImpl.getTotalDeviceMemoryUsage();
    }

    long getCurrentDeviceMemoryUsage() {
        return taskGraphImpl.getCurrentDeviceMemoryUsage();
    }

    void mapOnDeviceMemoryRegion(Object destArray, Object srcArray, long offset, TornadoTaskGraphInterface taskGraphSrc) {
        taskGraphImpl.mapOnDeviceMemoryRegion(destArray, srcArray, offset, taskGraphSrc);
    }

    void setLastExecutedTaskGraph(TornadoTaskGraphInterface lastExecutedTaskGraph) {
        taskGraphImpl.setLastExecutedTaskGraph(lastExecutedTaskGraph);
    }

    public Collection<?> getOutputs() {
        return taskGraphImpl.getOutputs();
    }

    TornadoTaskGraphInterface getTaskGraphImpl() {
        return taskGraphImpl;
    }

    public boolean isGridRegistered() {
        return taskGraphImpl.isGridRegistered();
    }

    // ========== Placeholder-supporting overloads for Task5-Task15 ==========

    /** Adds task with five parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5> TaskGraph task(String id, Task5<T1, T2, T3, T4, T5> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with six parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6> TaskGraph task(String id, Task6<T1, T2, T3, T4, T5, T6> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with seven parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7> TaskGraph task(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with eight parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> TaskGraph task(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with nine parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskGraph task(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with ten parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskGraph task(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with eleven parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> TaskGraph task(String id, Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with twelve parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> TaskGraph task(String id, Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with thirteen parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> TaskGraph task(String id, Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with fourteen parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> TaskGraph task(String id, Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /** Adds task with fifteen parameters (placeholder version). */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskGraph task(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15) {
        checkTaskName(id);
        registerTaskPlaceholders(id, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

}
