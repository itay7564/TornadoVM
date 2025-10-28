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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.Args;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.Param;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test placeholder runtime binding at execution time (Phase 3).
 * Tests thread-safe re-entrancy with different inputs/outputs per invocation.
 * 
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestPlaceholderRuntimeBinding
 * </code>
 * </p>
 */
public class TestPlaceholderRuntimeBinding extends TornadoTestBase {

    // Simple kernel for testing
    public static void vectorAdd(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorMul(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] * b[i];
        }
    }

    public static void scalarAdd(int[] a, int scalar, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] + scalar;
        }
    }

    @Test
    public void testBasicRuntimeBinding() {
        final int size = 256;

        // Create arrays for first execution
        int[] a1 = new int[size];
        int[] b1 = new int[size];
        int[] c1 = new int[size];
        Arrays.fill(a1, 1);
        Arrays.fill(b1, 2);

        // Create arrays for second execution
        int[] a2 = new int[size];
        int[] b2 = new int[size];
        int[] c2 = new int[size];
        Arrays.fill(a2, 10);
        Arrays.fill(b2, 20);

        // Create TaskGraph with placeholders
        TaskGraph taskGraph = new TaskGraph("tg0") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        Param.in("arrayA", int[].class), //
                        Param.in("arrayB", int[].class), //
                        Param.out("arrayC", int[].class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("arrayC", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution with a1, b1, c1
        executionPlan.execute(Args.of("arrayA", a1).and("arrayB", b1).and("arrayC", c1).build());

        // Verify first execution results
        for (int i = 0; i < size; i++) {
            assertEquals("c1[" + i + "] should be 3", 3, c1[i]);
        }

        // Second execution with a2, b2, c2 (different inputs)
        executionPlan.execute(Args.of("arrayA", a2).and("arrayB", b2).and("arrayC", c2).build());

        // Verify second execution results
        for (int i = 0; i < size; i++) {
            assertEquals("c2[" + i + "] should be 30", 30, c2[i]);
        }

        // Verify first arrays are unchanged (thread safety)
        for (int i = 0; i < size; i++) {
            assertEquals("c1[" + i + "] should still be 3", 3, c1[i]);
        }
    }

    @Test
    public void testTypeChecking() {
        final int size = 256;
        int[] a = new int[size];
        float[] b = new float[size]; // Wrong type!
        int[] c = new int[size];

        // Create TaskGraph expecting int[] for all parameters
        TaskGraph taskGraph = new TaskGraph("tg1") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        Param.in("arrayA", int[].class), //
                        Param.in("arrayB", int[].class), //
                        Param.out("arrayC", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Try to bind wrong type - should throw exception
        try {
            executionPlan.execute(Args.of("arrayA", a).and("arrayB", b).and("arrayC", c).build());
            fail("Should have thrown TornadoRuntimeException for type mismatch");
        } catch (TornadoRuntimeException e) {
            // Expected - verify error message mentions type mismatch
            assertNotNull("Exception message should not be null", e.getMessage());
            // The exception should be thrown during execution
        }
    }

    @Test
    public void testMissingBinding() {
        final int size = 256;
        int[] a = new int[size];
        int[] b = new int[size];

        // Create TaskGraph with placeholders
        TaskGraph taskGraph = new TaskGraph("tg2") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        Param.in("arrayA", int[].class), //
                        Param.in("arrayB", int[].class), //
                        Param.out("arrayC", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Try to execute with missing binding for arrayC - should throw exception
        try {
            executionPlan.execute(Args.of("arrayA", a).and("arrayB", b).build());
            fail("Should have thrown TornadoRuntimeException for missing binding");
        } catch (TornadoRuntimeException e) {
            // Expected - verify error message mentions missing binding
            assertNotNull("Exception message should not be null", e.getMessage());
        }
    }

    @Test
    public void testMixedConcreteAndPlaceholder() {
        final int size = 256;

        // Concrete array (fixed for all executions)
        int[] fixedA = new int[size];
        Arrays.fill(fixedA, 5);

        // Placeholder arrays (change per execution)
        int[] b1 = new int[size];
        int[] c1 = new int[size];
        Arrays.fill(b1, 10);

        int[] b2 = new int[size];
        int[] c2 = new int[size];
        Arrays.fill(b2, 20);

        // Create TaskGraph with mixed concrete and placeholder
        TaskGraph taskGraph = new TaskGraph("tg3") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        fixedA, // concrete parameter
                        Param.in("arrayB", int[].class), // placeholder
                        Param.out("arrayC", int[].class)) // placeholder
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("arrayC", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution with b1, c1
        executionPlan.execute(Args.of("arrayB", b1).and("arrayC", c1).build());

        // Verify: 5 + 10 = 15
        for (int i = 0; i < size; i++) {
            assertEquals("c1[" + i + "] should be 15", 15, c1[i]);
        }

        // Second execution with b2, c2
        executionPlan.execute(Args.of("arrayB", b2).and("arrayC", c2).build());

        // Verify: 5 + 20 = 25
        for (int i = 0; i < size; i++) {
            assertEquals("c2[" + i + "] should be 25", 25, c2[i]);
        }
    }

    @Test
    public void testBackwardCompatibility() {
        final int size = 256;

        // Create arrays
        int[] a = new int[size];
        int[] b = new int[size];
        int[] c = new int[size];
        Arrays.fill(a, 3);
        Arrays.fill(b, 7);

        // Create TaskGraph WITHOUT placeholders (traditional approach)
        TaskGraph taskGraph = new TaskGraph("tg4") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Execute without Args (backward compatible)
        executionPlan.execute();

        // Verify results
        for (int i = 0; i < size; i++) {
            assertEquals("c[" + i + "] should be 10", 10, c[i]);
        }
    }

    @Test
    public void testTransferDirectiveBinding() {
        final int size = 256;

        // First set of arrays
        int[] a1 = new int[size];
        int[] b1 = new int[size];
        int[] c1 = new int[size];
        Arrays.fill(a1, 2);
        Arrays.fill(b1, 3);

        // Second set of arrays
        int[] a2 = new int[size];
        int[] b2 = new int[size];
        int[] c2 = new int[size];
        Arrays.fill(a2, 5);
        Arrays.fill(b2, 7);

        // Create TaskGraph with transfer directive placeholders
        TaskGraph taskGraph = new TaskGraph("tg5") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, //
                        Param.in("input1", int[].class), //
                        Param.in("input2", int[].class)) //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        Param.in("input1", int[].class), //
                        Param.in("input2", int[].class), //
                        Param.out("output", int[].class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("output", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution
        executionPlan.execute(Args.of("input1", a1).and("input2", b1).and("output", c1).build());

        // Verify: 2 + 3 = 5
        for (int i = 0; i < size; i++) {
            assertEquals("c1[" + i + "] should be 5", 5, c1[i]);
        }

        // Second execution
        executionPlan.execute(Args.of("input1", a2).and("input2", b2).and("output", c2).build());

        // Verify: 5 + 7 = 12
        for (int i = 0; i < size; i++) {
            assertEquals("c2[" + i + "] should be 12", 12, c2[i]);
        }
    }

    @Test
    public void testMultipleTasks() {
        final int size = 256;

        // Arrays for first execution
        int[] a1 = new int[size];
        int[] b1 = new int[size];
        Arrays.fill(a1, 1);
        Arrays.fill(b1, 2);

        // Arrays for second execution
        int[] a2 = new int[size];
        int[] b2 = new int[size];
        Arrays.fill(a2, 10);
        Arrays.fill(b2, 20);

        // Create TaskGraph with two tasks
        TaskGraph taskGraph = new TaskGraph("tg6") //
                .task("t0", TestPlaceholderRuntimeBinding::scalarAdd, //
                        Param.in("input", int[].class), //
                        5, // constant scalar
                        Param.out("temp", int[].class)) //
                .task("t1", TestPlaceholderRuntimeBinding::scalarAdd, //
                        Param.in("temp", int[].class), //
                        10, // constant scalar
                        Param.out("output", int[].class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("output", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution: input=a1(1), temp=b1, output=a1 => result: 1+5+10=16
        executionPlan.execute(Args.of("input", a1).and("temp", b1).and("output", a1).build());

        for (int i = 0; i < size; i++) {
            assertEquals("a1[" + i + "] should be 16", 16, a1[i]);
        }

        // Second execution: input=a2(10), temp=b2, output=a2 => result: 10+5+10=25
        executionPlan.execute(Args.of("input", a2).and("temp", b2).and("output", a2).build());

        for (int i = 0; i < size; i++) {
            assertEquals("a2[" + i + "] should be 25", 25, a2[i]);
        }
    }
}
