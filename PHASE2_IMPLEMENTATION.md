# Phase 2 Implementation: Placeholder Schema Capture at Snapshot

## Summary

This implementation completes Phase 2 of the typed placeholders feature as described in `docs/proposals/typed-placeholders-threadsafe-exec.md`. The goal is to capture a schema of placeholder parameters when they are used in TaskGraph definitions and transfer directives, and persist this schema in the ImmutableTaskGraph for later binding in Phase 3.

## Changes Made

### 1. New API Types

#### PlaceholderRef DTO (`uk.ac.manchester.tornado.api.common.PlaceholderRef`)
- Simple data transfer object representing a placeholder parameter reference
- Fields:
  - `name: String` - The parameter name
  - `type: Class<?>` - The parameter type
  - `index: int` - The positional index (0-based)
  - `role: String` - The role/context (e.g., "task", "transferIn", "transferOut", "consume", "persist")
- Includes validation in constructor and proper equals/hashCode/toString methods

### 2. TaskGraph Placeholder Registry

#### Added to `uk.ac.manchester.tornado.api.TaskGraph`:
- **Field**: `Map<String, List<PlaceholderRef>> placeholderRegistry` - stores placeholder references keyed by context
- **Helper methods**:
  - `registerPlaceholder()` - Registers a single placeholder
  - `registerTaskPlaceholders()` - Scans task arguments for Param<?> instances
  - `registerTransferPlaceholders()` - Scans transfer directive arguments for Param<?> instances
  - `getPlaceholderRegistry()` - Returns immutable view of the registry

#### Modified Methods:
- **All task() methods** (Task, Task1-Task15): Now call `registerTaskPlaceholders()` to detect and register Param<?> instances in arguments
- **transferToDevice()**: Calls `registerTransferPlaceholders()` with role "transferIn"
- **transferToHost()**: Calls `registerTransferPlaceholders()` with role "transferOut"
- **consumeFromDevice()**: Calls `registerTransferPlaceholders()` with role "consume"
- **persistOnDevice()**: Calls `registerTransferPlaceholders()` with role "persist"
- **snapshot()**: Copies the placeholder registry to the cloned TaskGraph

### 3. ImmutableTaskGraph Schema Exposure

#### Added to `uk.ac.manchester.tornado.api.ImmutableTaskGraph`:
- **Method**: `public Map<String, List<PlaceholderRef>> getPlaceholderRegistry()` - Exposes placeholder metadata captured at snapshot time

### 4. Comprehensive Tests

Created `uk.ac.manchester.tornado.unittests.api.TestPlaceholderSchemaCapture` with tests for:
- Task parameter placeholders
- Transfer directive placeholders (transferIn, transferOut)
- Consume and persist placeholders
- Mixed concrete and placeholder parameters
- Empty registry (backward compatibility)

## Key Design Decisions

### Schema Storage
The placeholder registry uses a `Map<String, List<PlaceholderRef>>` structure where:
- For tasks: key is the task ID (e.g., "t0")
- For transfers: key is `role_parameterName` (e.g., "transferIn_inputData")

This design allows:
- Multiple placeholders per context (e.g., a task with 3 parameters)
- Easy lookup by context
- Clear separation between task and transfer placeholders

### Backward Compatibility
The implementation is fully backward compatible:
- Existing code using concrete objects continues to work unchanged
- The registry only captures Param<?> instances; concrete objects are ignored
- Empty registry when no placeholders are used

### Detection Mechanism
Placeholder detection uses simple `instanceof Param` checks in the registration methods. This is efficient and doesn't require reflection or bytecode inspection.

## Integration Points

This Phase 2 implementation provides the foundation for Phase 3 (Runtime Binding), which will:
1. Use the captured schema to validate Args bindings
2. Build per-invocation argument lists from the schema + ExecutorFrame.argBindings
3. Support thread-safe, re-entrant execution

## Testing

The tests verify:
- ✓ Placeholder parameters are correctly captured with name, type, index, and role
- ✓ Transfer directives capture placeholders properly
- ✓ Mixed concrete and placeholder parameters work correctly
- ✓ Empty registry for backward compatibility
- ✓ All placeholder roles (task, transferIn, transferOut, consume, persist) are supported

## Files Modified/Created

### Created:
- `tornado-api/src/main/java/uk/ac/manchester/tornado/api/common/PlaceholderRef.java`
- `tornado-unittests/src/main/java/uk/ac/manchester/tornado/unittests/api/TestPlaceholderSchemaCapture.java`

### Modified:
- `tornado-api/src/main/java/uk/ac/manchester/tornado/api/TaskGraph.java`
- `tornado-api/src/main/java/uk/ac/manchester/tornado/api/ImmutableTaskGraph.java`

## Build Notes

The project requires:
- Java 21 (with --enable-preview for Foreign Function & Memory API)
- Maven 3.x
- Build using: `make jdk21` or the appropriate backend-specific target

The implementation follows the existing code style and patterns in the TornadoVM codebase.
