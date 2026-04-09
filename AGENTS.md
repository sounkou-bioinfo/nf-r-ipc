# nf-r IPC Plan (Arrow-Only)

This file captures the implementation plan for an `nf-r` style Nextflow plugin that bridges to R with **Arrow IPC only** (no JSON payloads, no JSON control sidecars).

## Goals

- Build a practical Nextflow<->R bridge without embedding R in the Nextflow JVM.
- Use Arrow IPC as the only protocol format for both control and data.
- Keep the first release narrow, predictable, and testable.
- Support direct nested list/map argument round-trip between Groovy and R.
- Support channel-friendly record outputs (`rRecords`, `rTable`).

## Non-Goals (v1)

- No in-JVM R embedding (`rJava`, `JRI`).
- No arbitrary R object graph serialization.
- No long-lived distributed worker pool.
- No custom executor or runtime replacement.

## Architecture Decision

- **Execution model:** Nextflow plugin function calls an external `Rscript` process.
- **Protocol model:** one Arrow IPC stream/file for request, one for response.
- **Control plane:** first batch/table in the Arrow stream (`__nfr_control__`).
- **Data plane:** subsequent batch(es)/table(s) (`__nfr_data__`).

Arrow Java is the selected JVM codec for this repository baseline.

This follows the proven pattern: thin orchestration + typed columnar IPC + process isolation.

## Codec Plan (Arrow Java baseline)

- Define a shared codec interface (`IpcCodec`) for request/response read/write.
- Keep protocol/schema logic outside codec implementations.
- Use `ArrowJavaCodec` as the production backend.
- JNI work can be explored later behind the same interface without changing protocol/tests.

## Protocol Contract (Arrow-Only)

### Request (`request.arrows`)

- Batch 0/table name: `__nfr_control__`
- Required control fields:
  - `protocol_version` (int32)
  - `call_id` (utf8)
  - `function` (utf8)
  - `script_mode` (utf8; `path` or `inline`)
  - `script_ref` (utf8; path or inline id)
  - `payload_kind` (utf8; `value_graph` or `table`)
- Batch 1..N/table name: `__nfr_data__`

### Response (`response.arrows`)

- Batch 0/table name: `__nfr_control__`
- Required control fields:
  - `protocol_version` (int32)
  - `call_id` (utf8)
  - `status` (utf8; `ok` or `error`)
  - `result_kind` (utf8; nullable on error)
  - `error_class` (utf8; nullable)
  - `error_message` (utf8; nullable)
- Batch 1..N/table name: `__nfr_data__` (present for `status=ok`)

## Type Scope (v1)

Supported values crossing the boundary:

- table-like data (`data.frame`/`tibble` <-> Arrow table)
- scalar records (single-row table)
- list of records (multi-row table)
- nested lists and named maps (direct round-trip)
- primitive columns: integer, double, string, boolean, nulls
- typed missing values: `na_logical`, `na_integer`, `na_double`, `na_character`
- recursive data-frame-like values via `data_frame` tag (named equal-length scalar columns)

Deferred:

- factors, POSIXct/datetime edge-cases, list-columns with deep nesting
- closures, formulas, environments, S4 object graphs

## Delivery Roadmap

### Phase 1 - Protocol and single function MVP

1. Add plugin extension function: `rFunction(Map args, String code = '')` plus script-path form.
2. Implement `IpcCodec` interface and `ArrowJavaCodec` request/response path.
3. Implement `Rscript` launcher and environment plumbing.
4. Implement Arrow response reader and typed result conversion.
5. Implement structured error surfacing from response control batch.

## Argument Passing Model (nf-python style, Arrow-native)

Mirror the `nf-python` call pattern on the Nextflow side:

- User-facing call accepts named arguments map (`Map args`) and either inline code or script path.
- Reserved keys are not forwarded as function args: `function`, `script`, `_executable`, `_conda_env`, `_r_libs`, `_on_error`, `_payload_kind`.
- Remaining keys are forwarded as named arguments to R.

Equivalent shapes to support:

- `rFunction([x: 1, y: 2], code)`
- `rFunction(script: 'script.R', x: [1,2,3], meta: [sample:'S1'])`

Environment variables for launcher contract:

- `NFR_REQUEST_IPC` -> request Arrow IPC path
- `NFR_RESPONSE_IPC` -> response Arrow IPC path

R helper runtime reconstructs forwarded args and exposes them as a named list.

## Direct List/Map Support (required)

To support heterogeneous nested values without JSON, encode generic argument values in a dedicated Arrow value graph table.

### `__nfr_data__` value graph schema (v1)

- `value_id` (int64) unique node id
- `parent_id` (int64, nullable) parent node id
- `key` (utf8, nullable) map key when parent is a map/object
- `index` (int32, nullable) position when parent is a list
- `tag` (utf8) one of: `null`, `na_logical`, `na_integer`, `na_double`, `na_character`, `list`, `map`, `data_frame`, `string`, `int64`, `float64`, `bool`
- `v_string` (utf8, nullable)
- `v_int64` (int64, nullable)
- `v_float64` (float64, nullable)
- `v_bool` (bool, nullable)

Rules:

- Root node represents the forwarded argument map.
- Lists are represented by child rows with ordered `index`.
- Maps are represented by child rows with `key`.
- Scalars are represented in typed value columns according to `tag`.

This keeps list/map passing Arrow-only while preserving nested structure for Groovy<->R round-trips.

### Phase 2 - R helper runtime (`nfR`)

1. Provide minimal R helpers:
   - `nf_input()`
   - `nf_output(x)`
   - `nf_main()`
2. Parse control batch and dispatch target function.
3. Read/write Arrow IPC via `nanoarrow`.
4. Normalize R errors into response control fields.

### Phase 3 - Hardening

1. Add protocol validation guards (missing batch, schema mismatch, version mismatch).
2. Add deterministic temp/scratch lifecycle and cleanup.
3. Add config knobs (`executable`, `conda_executable`, `on_error`, `payload_kind`, mode stream/file, keep-scratch).
4. Improve diagnostics (stderr capture, call id in exceptions).

### Phase 4 - Optional performance path

1. Add persistent worker mode (socket transport).
2. Keep Arrow IPC framing identical to v1.
3. Benchmark spawn-per-call vs resident worker.

## Testing Plan

### Unit tests

- control/data batch encoding/decoding correctness
- schema validation and required fields
- error propagation mapping

### Integration tests

- happy path: numeric/string table in, transformed table out
- failure path: R exception -> Nextflow plugin exception with call id
- null handling across Java/R boundaries
- inline code and script-path modes
- nested list/map round-trip parity with `nf-python` style argument passing
- `NA` vs `NULL` round-trip parity
- atomic vector mapping parity
- recursive `data_frame` mapping parity
- channel-focused validations for `rRecords` and `_on_error: return`

### Compatibility tests

- multiple Nextflow versions supported by this plugin baseline
- R versions used in CI matrix

## MVP Acceptance Criteria

- `include { rFunction } from 'plugin/nf-r-ipc'` works in sample workflows.
- No JSON is used for request/response payload or control.
- End-to-end call succeeds for table input/output.
- End-to-end call succeeds for nested list/map input/output.
- End-to-end call fails with structured error for deliberate R error.
- `rRecords` works for channel-oriented outputs.
- Tests pass in CI.

## Suggested Initial Repository Tasks

1. Create `src/main/groovy/.../RExtension.groovy` with `rFunction` entry point.
2. Add `IpcCodec` abstraction and Arrow Java codec implementation.
3. Add `src/main/resources/nfr_launcher.R` and minimal runtime contract.
4. Add integration example pipeline under `validation/`.
5. Add protocol doc under `README.md` once MVP is green.

## Risks and Mitigations

- **Risk:** schema drift between Java and R.
  - **Mitigation:** explicit protocol version + strict control schema checks.
- **Risk:** large payload memory pressure.
  - **Mitigation:** stream mode first; avoid materializing unnecessary copies.
- **Risk:** ambiguous type conversions.
  - **Mitigation:** keep v1 type contract narrow and explicit.

## Operating Principle

Prefer a small, strict, Arrow-native contract that is easy to reason about over broad, magical language interop.
