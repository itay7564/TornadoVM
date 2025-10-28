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

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import uk.ac.manchester.tornado.api.Args;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.Param;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test concurrent execution with placeholder runtime binding (Phase 3).
 * Demonstrates thread-safe re-entrancy where multiple threads execute
 * the same ImmutableTaskGraph concurrently with different inputs/outputs.
 * 
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestPlaceholderConcurrentExecution
 * </code>
 * </p>
 */
public class TestPlaceholderConcurrentExecution extends TornadoTestBase {

    // Simple kernel for testing
    public static void vectorAdd(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    @Test
    public void testConcurrentExecutionWithDifferentInputs() throws InterruptedException {
        final int size = 256;
        final int numThreads = 4;

        // Create shared ImmutableTaskGraph with placeholders
        TaskGraph taskGraph = new TaskGraph("tg_concurrent") //
                .task("t0", TestPlaceholderConcurrentExecution::vectorAdd, //
                        Param.in("arrayA", int[].class), //
                        Param.in("arrayB", int[].class), //
                        Param.out("arrayC", int[].class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("arrayC", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Synchronization primitives
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // Create threads that will execute concurrently
        Thread[] threads = new Thread[numThreads];
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            final int valueA = threadId * 10;
            final int valueB = threadId * 20;
            final int expectedResult = valueA + valueB;

            threads[t] = new Thread(() -> {
                try {
                    // Create thread-local arrays
                    int[] a = new int[size];
                    int[] b = new int[size];
                    int[] c = new int[size];
                    Arrays.fill(a, valueA);
                    Arrays.fill(b, valueB);

                    // Wait for all threads to be ready
                    startLatch.await();

                    // Execute with thread-specific bindings
                    executionPlan.execute(
                        Args.of("arrayA", a)
                            .and("arrayB", b)
                            .and("arrayC", c)
                            .build()
                    );

                    // Verify results
                    boolean allCorrect = true;
                    for (int i = 0; i < size; i++) {
                        if (c[i] != expectedResult) {
                            System.err.println("Thread " + threadId + " failed: c[" + i + "] = " + c[i] + ", expected " + expectedResult);
                            allCorrect = false;
                            break;
                        }
                    }

                    if (allCorrect) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " threw exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
            threads[t].start();
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        doneLatch.await();

        // Verify all threads succeeded
        assertEquals("All threads should have completed successfully", numThreads, successCount.get());
    }

    @Test
    public void testSequentialExecutionsDoNotInterfere() {
        final int size = 256;

        // Create TaskGraph with placeholders
        TaskGraph taskGraph = new TaskGraph("tg_sequential") //
                .task("t0", TestPlaceholderConcurrentExecution::vectorAdd, //
                        Param.in("arrayA", int[].class), //
                        Param.in("arrayB", int[].class), //
                        Param.out("arrayC", int[].class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("arrayC", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // First execution
        int[] a1 = new int[size];
        int[] b1 = new int[size];
        int[] c1 = new int[size];
        Arrays.fill(a1, 1);
        Arrays.fill(b1, 2);

        executionPlan.execute(Args.of("arrayA", a1).and("arrayB", b1).and("arrayC", c1).build());

        // Verify first execution
        for (int i = 0; i < size; i++) {
            assertEquals("First execution: c1[" + i + "] should be 3", 3, c1[i]);
        }

        // Second execution with different inputs
        int[] a2 = new int[size];
        int[] b2 = new int[size];
        int[] c2 = new int[size];
        Arrays.fill(a2, 10);
        Arrays.fill(b2, 20);

        executionPlan.execute(Args.of("arrayA", a2).and("arrayB", b2).and("arrayC", c2).build());

        // Verify second execution
        for (int i = 0; i < size; i++) {
            assertEquals("Second execution: c2[" + i + "] should be 30", 30, c2[i]);
        }

        // Third execution - verify first execution results are unchanged (no interference)
        for (int i = 0; i < size; i++) {
            assertEquals("First execution results unchanged: c1[" + i + "] should still be 3", 3, c1[i]);
        }

        // Fourth execution with first inputs again to verify idempotency
        int[] c1_again = new int[size];
        executionPlan.execute(Args.of("arrayA", a1).and("arrayB", b1).and("arrayC", c1_again).build());

        for (int i = 0; i < size; i++) {
            assertEquals("Re-execution with same inputs: c1_again[" + i + "] should be 3", 3, c1_again[i]);
        }
    }

    @Test
    public void testRapidSequentialExecutions() {
        final int size = 128;
        final int numExecutions = 100;

        // Create TaskGraph with placeholders
        TaskGraph taskGraph = new TaskGraph("tg_rapid") //
                .task("t0", TestPlaceholderConcurrentExecution::vectorAdd, //
                        Param.in("arrayA", int[].class), //
                        Param.in("arrayB", int[].class), //
                        Param.out("arrayC", int[].class)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, Param.out("arrayC", int[].class));

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Execute many times rapidly with different inputs each time
        for (int exec = 0; exec < numExecutions; exec++) {
            int[] a = new int[size];
            int[] b = new int[size];
            int[] c = new int[size];
            
            int valueA = exec;
            int valueB = exec * 2;
            Arrays.fill(a, valueA);
            Arrays.fill(b, valueB);

            executionPlan.execute(Args.of("arrayA", a).and("arrayB", b).and("arrayC", c).build());

            // Verify results
            int expected = valueA + valueB;
            for (int i = 0; i < size; i++) {
                assertEquals("Execution " + exec + ": c[" + i + "] should be " + expected, expected, c[i]);
            }
        }
    }
}
