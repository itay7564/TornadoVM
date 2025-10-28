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

package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.Param;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.PlaceholderRef;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test placeholder schema capture at TaskGraph snapshot time (Phase 2).
 * 
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestPlaceholderSchemaCapture
 * </code>
 * </p>
 */
public class TestPlaceholderSchemaCapture extends TornadoTestBase {

    // Simple kernel for testing
    public static void vectorAdd(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    @Test
    public void testTaskParameterPlaceholder() {
        // Create a TaskGraph with placeholder parameters
        TaskGraph taskGraph = new TaskGraph("tg0") //
                .task("t0", TestPlaceholderSchemaCapture::vectorAdd, //
                        Param.in("arrayA", int[].class), //
                        Param.in("arrayB", int[].class), //
                        Param.out("arrayC", int[].class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("arrayC", int[].class));

        // Take snapshot
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Verify placeholder registry
        Map<String, List<PlaceholderRef>> registry = immutableTaskGraph.getPlaceholderRegistry();
        assertNotNull("Placeholder registry should not be null", registry);

        // Check task placeholders
        assertTrue("Registry should contain task t0", registry.containsKey("t0"));
        List<PlaceholderRef> taskPlaceholders = registry.get("t0");
        assertEquals("Task t0 should have 3 placeholders", 3, taskPlaceholders.size());

        // Verify first placeholder
        PlaceholderRef placeholder0 = taskPlaceholders.get(0);
        assertEquals("First placeholder name should be arrayA", "arrayA", placeholder0.getName());
        assertEquals("First placeholder type should be int[]", int[].class, placeholder0.getType());
        assertEquals("First placeholder index should be 0", 0, placeholder0.getIndex());
        assertEquals("First placeholder role should be task", "task", placeholder0.getRole());

        // Verify second placeholder
        PlaceholderRef placeholder1 = taskPlaceholders.get(1);
        assertEquals("Second placeholder name should be arrayB", "arrayB", placeholder1.getName());
        assertEquals("Second placeholder type should be int[]", int[].class, placeholder1.getType());
        assertEquals("Second placeholder index should be 1", 1, placeholder1.getIndex());
        assertEquals("Second placeholder role should be task", "task", placeholder1.getRole());

        // Verify third placeholder
        PlaceholderRef placeholder2 = taskPlaceholders.get(2);
        assertEquals("Third placeholder name should be arrayC", "arrayC", placeholder2.getName());
        assertEquals("Third placeholder type should be int[]", int[].class, placeholder2.getType());
        assertEquals("Third placeholder index should be 2", 2, placeholder2.getIndex());
        assertEquals("Third placeholder role should be task", "task", placeholder2.getRole());
    }

    @Test
    public void testTransferPlaceholder() {
        // Create a TaskGraph with transfer placeholders
        TaskGraph taskGraph = new TaskGraph("tg1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, //
                        Param.in("inputData", int[].class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, //
                        Param.out("outputData", int[].class));

        // Take snapshot
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Verify placeholder registry
        Map<String, List<PlaceholderRef>> registry = immutableTaskGraph.getPlaceholderRegistry();
        assertNotNull("Placeholder registry should not be null", registry);

        // Check transfer placeholders
        assertTrue("Registry should contain transferIn_inputData", registry.containsKey("transferIn_inputData"));
        assertTrue("Registry should contain transferOut_outputData", registry.containsKey("transferOut_outputData"));

        // Verify transferIn placeholder
        List<PlaceholderRef> transferInPlaceholders = registry.get("transferIn_inputData");
        assertEquals("TransferIn should have 1 placeholder", 1, transferInPlaceholders.size());
        PlaceholderRef inPlaceholder = transferInPlaceholders.get(0);
        assertEquals("TransferIn placeholder name should be inputData", "inputData", inPlaceholder.getName());
        assertEquals("TransferIn placeholder role should be transferIn", "transferIn", inPlaceholder.getRole());

        // Verify transferOut placeholder
        List<PlaceholderRef> transferOutPlaceholders = registry.get("transferOut_outputData");
        assertEquals("TransferOut should have 1 placeholder", 1, transferOutPlaceholders.size());
        PlaceholderRef outPlaceholder = transferOutPlaceholders.get(0);
        assertEquals("TransferOut placeholder name should be outputData", "outputData", outPlaceholder.getName());
        assertEquals("TransferOut placeholder role should be transferOut", "transferOut", outPlaceholder.getRole());
    }

    @Test
    public void testMixedConcreteAndPlaceholder() {
        // Create arrays for concrete parameters
        int[] concreteA = new int[10];
        int[] concreteC = new int[10];

        // Create a TaskGraph with mixed concrete and placeholder parameters
        TaskGraph taskGraph = new TaskGraph("tg2") //
                .task("t0", TestPlaceholderSchemaCapture::vectorAdd, //
                        concreteA, // concrete parameter at index 0
                        Param.in("arrayB", int[].class), // placeholder at index 1
                        concreteC); // concrete parameter at index 2

        // Take snapshot
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Verify placeholder registry
        Map<String, List<PlaceholderRef>> registry = immutableTaskGraph.getPlaceholderRegistry();
        assertNotNull("Placeholder registry should not be null", registry);

        // Check task placeholders
        assertTrue("Registry should contain task t0", registry.containsKey("t0"));
        List<PlaceholderRef> taskPlaceholders = registry.get("t0");
        assertEquals("Task t0 should have 1 placeholder (arrayB)", 1, taskPlaceholders.size());

        // Verify the placeholder
        PlaceholderRef placeholder = taskPlaceholders.get(0);
        assertEquals("Placeholder name should be arrayB", "arrayB", placeholder.getName());
        assertEquals("Placeholder type should be int[]", int[].class, placeholder.getType());
        assertEquals("Placeholder index should be 1", 1, placeholder.getIndex());
        assertEquals("Placeholder role should be task", "task", placeholder.getRole());
    }

    @Test
    public void testEmptyRegistry() {
        // Create a TaskGraph with no placeholders
        int[] a = new int[10];
        int[] b = new int[10];
        int[] c = new int[10];

        TaskGraph taskGraph = new TaskGraph("tg3") //
                .task("t0", TestPlaceholderSchemaCapture::vectorAdd, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // Take snapshot
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Verify placeholder registry is empty
        Map<String, List<PlaceholderRef>> registry = immutableTaskGraph.getPlaceholderRegistry();
        assertNotNull("Placeholder registry should not be null", registry);
        assertTrue("Registry should be empty when no placeholders are used", registry.isEmpty());
    }

    @Test
    public void testConsumePlaceholder() {
        // Create a TaskGraph with consume directive
        TaskGraph taskGraph = new TaskGraph("tg4") //
                .consumeFromDevice(Param.inOut("persistedData", int[].class));

        // Take snapshot
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Verify placeholder registry
        Map<String, List<PlaceholderRef>> registry = immutableTaskGraph.getPlaceholderRegistry();
        assertNotNull("Placeholder registry should not be null", registry);

        // Check consume placeholder
        assertTrue("Registry should contain consume_persistedData", registry.containsKey("consume_persistedData"));
        List<PlaceholderRef> consumePlaceholders = registry.get("consume_persistedData");
        assertEquals("Consume should have 1 placeholder", 1, consumePlaceholders.size());
        PlaceholderRef placeholder = consumePlaceholders.get(0);
        assertEquals("Consume placeholder name should be persistedData", "persistedData", placeholder.getName());
        assertEquals("Consume placeholder role should be consume", "consume", placeholder.getRole());
    }

    @Test
    public void testPersistPlaceholder() {
        // Create a TaskGraph with persist directive
        TaskGraph taskGraph = new TaskGraph("tg5") //
                .persistOnDevice(Param.inOut("deviceData", int[].class));

        // Take snapshot
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Verify placeholder registry
        Map<String, List<PlaceholderRef>> registry = immutableTaskGraph.getPlaceholderRegistry();
        assertNotNull("Placeholder registry should not be null", registry);

        // Check persist placeholder
        assertTrue("Registry should contain persist_deviceData", registry.containsKey("persist_deviceData"));
        List<PlaceholderRef> persistPlaceholders = registry.get("persist_deviceData");
        assertEquals("Persist should have 1 placeholder", 1, persistPlaceholders.size());
        PlaceholderRef placeholder = persistPlaceholders.get(0);
        assertEquals("Persist placeholder name should be deviceData", "deviceData", placeholder.getName());
        assertEquals("Persist placeholder role should be persist", "persist", placeholder.getRole());
    }
}
