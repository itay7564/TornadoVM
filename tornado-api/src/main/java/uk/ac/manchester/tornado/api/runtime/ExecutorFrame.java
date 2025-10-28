/*
 * Copyright (c) 2024, 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.runtime;

import java.util.Map;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;

/**
 * Class to store all objects and parameters related to the dispatch of an execution plan.
 */
public class ExecutorFrame {

    private final long executionPlanId;
    private GridScheduler gridScheduler;
    private ProfilerMode profilerMode;

    // Per-execution argument bindings (placeholder name -> concrete object).
    // When non-empty, runtime should use these bindings to resolve kernel args and transfers.
    private Map<String, Object> argBindings;
    private boolean hasOverrides;

    public ExecutorFrame(long id) {
        this.executionPlanId = id;
    }

    public ExecutorFrame setGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
        return this;
    }

    public GridScheduler getGridScheduler() {
        return gridScheduler;
    }

    public long getExecutionPlanId() {
        return this.executionPlanId;
    }

    public void setProfilerMode(ProfilerMode profilerMode) {
        this.profilerMode = profilerMode;
    }

    public void setProfilerOff() {
        this.profilerMode = null;
    }

    public ProfilerMode getProfilerMode() {
        return profilerMode;
    }

    // New API for per-execution argument bindings
    public ExecutorFrame setArgBindings(Map<String, Object> bindings) {
        this.argBindings = bindings;
        this.hasOverrides = bindings != null && !bindings.isEmpty();
        return this;
    }

    public Map<String, Object> getArgBindings() {
        return argBindings;
    }

    public boolean hasOverrides() {
        return hasOverrides;
    }
}