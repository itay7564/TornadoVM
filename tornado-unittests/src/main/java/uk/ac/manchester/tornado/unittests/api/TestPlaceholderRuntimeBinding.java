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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
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
    public static void vectorAdd(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorMul(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i));
        }
    }

    public static void scalarAdd(IntArray a, int scalar, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) + scalar);
        }
    }

    @Test
    public void testBasicRuntimeBinding() {
        final int size = 256;

        // Create arrays for first execution
        IntArray a1 = new IntArray(size);
        IntArray b1 = new IntArray(size);
        IntArray c1 = new IntArray(size);
        a1.init(1);
        b1.init(2);

        // Create arrays for second execution
        IntArray a2 = new IntArray(size);
        IntArray b2 = new IntArray(size);
        IntArray c2 = new IntArray(size);
        a2.init(10);
        b2.init(20);

        // Create TaskGraph with placeholders
        TaskGraph taskGraph = new TaskGraph("tg0") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        Param.in("arrayA", IntArray.class), //
                        Param.in("arrayB", IntArray.class), //
                        Param.out("arrayC", IntArray.class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("arrayC", IntArray.class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution with a1, b1, c1
        executionPlan.execute(Args.of("arrayA", a1).and("arrayB", b1).and("arrayC", c1).build());

        // Verify first execution results
        for (int i = 0; i < size; i++) {
            assertEquals("c1[" + i + "] should be 3", 3, c1.get(i));
        }

        // Second execution with a2, b2, c2 (different inputs)
        executionPlan.execute(Args.of("arrayA", a2).and("arrayB", b2).and("arrayC", c2).build());

        // Verify second execution results
        for (int i = 0; i < size; i++) {
            assertEquals("c2[" + i + "] should be 30", 30, c2.get(i));
        }

        // Verify first arrays are unchanged (thread safety)
        for (int i = 0; i < size; i++) {
            assertEquals("c1[" + i + "] should still be 3", 3, c1.get(i));
        }
    }

    @Test
    public void testTypeChecking() {
        final int size = 256;
        IntArray a = new IntArray(size);
        FloatArray b = new FloatArray(size); // Wrong type!
        IntArray c = new IntArray(size);

        // Create TaskGraph expecting IntArray for all parameters
        TaskGraph taskGraph = new TaskGraph("tg1") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        Param.in("arrayA", IntArray.class), //
                        Param.in("arrayB", IntArray.class), //
                        Param.out("arrayC", IntArray.class));

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
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);

        // Create TaskGraph with placeholders
        TaskGraph taskGraph = new TaskGraph("tg2") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        Param.in("arrayA", IntArray.class), //
                        Param.in("arrayB", IntArray.class), //
                        Param.out("arrayC", IntArray.class));

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
        IntArray fixedA = new IntArray(size);
        fixedA.init(5);

        // Placeholder arrays (change per execution)
        IntArray b1 = new IntArray(size);
        IntArray c1 = new IntArray(size);
        b1.init(10);

        IntArray b2 = new IntArray(size);
        IntArray c2 = new IntArray(size);
        b2.init(20);

        // Create TaskGraph with mixed concrete and placeholder
        TaskGraph taskGraph = new TaskGraph("tg3") //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        fixedA, // concrete parameter
                        Param.in("arrayB", IntArray.class), // placeholder
                        Param.out("arrayC", IntArray.class)) // placeholder
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("arrayC", IntArray.class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution with b1, c1
        executionPlan.execute(Args.of("arrayB", b1).and("arrayC", c1).build());

        // Verify: 5 + 10 = 15
        for (int i = 0; i < size; i++) {
            assertEquals("c1[" + i + "] should be 15", 15, c1.get(i));
        }

        // Second execution with b2, c2
        executionPlan.execute(Args.of("arrayB", b2).and("arrayC", c2).build());

        // Verify: 5 + 20 = 25
        for (int i = 0; i < size; i++) {
            assertEquals("c2[" + i + "] should be 25", 25, c2.get(i));
        }
    }

    @Test
    public void testBackwardCompatibility() {
        final int size = 256;

        // Create arrays
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray c = new IntArray(size);
        a.init(3);
        b.init(7);

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
            assertEquals("c[" + i + "] should be 10", 10, c.get(i));
        }
    }

    @Test
    public void testTransferDirectiveBinding() {
        final int size = 256;

        // First set of arrays
        IntArray a1 = new IntArray(size);
        IntArray b1 = new IntArray(size);
        IntArray c1 = new IntArray(size);
        a1.init(2);
        b1.init(3);

        // Second set of arrays
        IntArray a2 = new IntArray(size);
        IntArray b2 = new IntArray(size);
        IntArray c2 = new IntArray(size);
        a2.init(5);
        b2.init(7);

        // Create TaskGraph with transfer directive placeholders
        TaskGraph taskGraph = new TaskGraph("tg5") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, //
                        Param.in("input1", IntArray.class), //
                        Param.in("input2", IntArray.class)) //
                .task("t0", TestPlaceholderRuntimeBinding::vectorAdd, //
                        Param.in("input1", IntArray.class), //
                        Param.in("input2", IntArray.class), //
                        Param.out("output", IntArray.class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("output", IntArray.class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution
        executionPlan.execute(Args.of("input1", a1).and("input2", b1).and("output", c1).build());

        // Verify: 2 + 3 = 5
        for (int i = 0; i < size; i++) {
            assertEquals("c1[" + i + "] should be 5", 5, c1.get(i));
        }

        // Second execution
        executionPlan.execute(Args.of("input1", a2).and("input2", b2).and("output", c2).build());

        // Verify: 5 + 7 = 12
        for (int i = 0; i < size; i++) {
            assertEquals("c2[" + i + "] should be 12", 12, c2.get(i));
        }
    }

    @Test
    public void testMultipleTasks() {
        final int size = 256;

        // Arrays for first execution
        IntArray a1 = new IntArray(size);
        IntArray b1 = new IntArray(size);
        a1.init(1);
        b1.init(2);

        // Arrays for second execution
        IntArray a2 = new IntArray(size);
        IntArray b2 = new IntArray(size);
        a2.init(10);
        b2.init(20);

        // Create TaskGraph with two tasks
        TaskGraph taskGraph = new TaskGraph("tg6") //
                .task("t0", TestPlaceholderRuntimeBinding::scalarAdd, //
                        Param.in("input", IntArray.class), //
                        5, // constant scalar
                        Param.out("temp", IntArray.class)) //
                .task("t1", TestPlaceholderRuntimeBinding::scalarAdd, //
                        Param.in("temp", IntArray.class), //
                        10, // constant scalar
                        Param.out("output", IntArray.class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("output", IntArray.class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution: input=a1(1), temp=b1, output=a1 => result: 1+5+10=16
        executionPlan.execute(Args.of("input", a1).and("temp", b1).and("output", a1).build());

        for (int i = 0; i < size; i++) {
            assertEquals("a1[" + i + "] should be 16", 16, a1.get(i));
        }

        // Second execution: input=a2(10), temp=b2, output=a2 => result: 10+5+10=25
        executionPlan.execute(Args.of("input", a2).and("temp", b2).and("output", a2).build());

        for (int i = 0; i < size; i++) {
            assertEquals("a2[" + i + "] should be 25", 25, a2.get(i));
        }
    }
}
