# Typed, Named Placeholders and Thread-Safe Per-Invocation Execution

This document tracks incremental changes to enable:
- Typed, named placeholders at TaskGraph definition time (`Param<T>`).
- Per-execution runtime bindings (`Args`) supplied at `execute(...)`.
- Thread-safe, re-entrant execution of an `ImmutableTaskGraph` by moving per-run state into the per-execution frame/context.

## Phase 1 (API scaffolding, safe no-ops)
- Add `Param<T>` and `Args`. (done)
- Extend `ExecutorFrame` with `argBindings` / `hasOverrides`. (done)
- Add `TornadoExecutionPlan.execute(Args)` overload. (done)
- No behavior change yet; runtime may ignore empty bindings.

## Phase 2 (Schema capture at snapshot)
- When `TaskGraph.task(...)` or `transferToDevice/transferToHost/consumeFromDevice/persistOnDevice`
  receive `Param<?>`, capture a schema (name, type, access, role, positional index).
- Persist schema in the immutable TaskGraph for binding at execute time.
- Backward-compatible if concrete objects are used.

## Phase 3 (Runtime binding + re-entrancy)
- In `TornadoTaskGraph.execute(...)`, if `ExecutorFrame.hasOverrides()`:
  - Build per-invocation argument lists and transfer directives from schema + `ExecutorFrame.argBindings`.
  - Keep per-run state local to the invocation (avoid mutating shared fields like `streamIn/Out`, `argumentsLookUp`, persisted maps).

## Phase 4 (consume/persist semantics)
- Apply `consumeFromDevice` / `persistOnDevice` to the bound objects for the current invocation.
- Optionally add residency tokens in future to decouple from host-object identity.

## Phase 5 (Tests and benchmarks)
- Multithreaded tests: a single `ImmutableTaskGraph` executed concurrently with different `Args`.
- Backward-compat tests: existing suites must pass.
- Microbenchmarks to confirm kernel reuse remains intact within a single plan.

## Notes
- Compiled-code cache is currently namespaced per ExecutionPlan (`executionPlanId`). Broadening cache scope can be considered later.