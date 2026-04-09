# nf-r IPC Contract

This document defines the strict protocol and type contract for `nf-r-ipc`.

## Scope

- Transport: Arrow IPC only
- Control section: `__nfr_control__`
- Data section: `__nfr_data__`
- Payload kinds:
  - `value_graph` (default)
  - `table`

## Control Fields

### Request control (required)

- `protocol_version` (int32)
- `call_id` (utf8)
- `function` (utf8)
- `script_mode` (utf8: `path` or `inline`)
- `script_ref` (utf8)
- `payload_kind` (utf8: `value_graph` or `table`)

### Response control (required)

- `protocol_version` (int32)
- `call_id` (utf8)
- `status` (utf8: `ok` or `error`)
- `result_kind` (utf8, nullable on error)
- `error_class` (utf8, nullable)
- `error_message` (utf8, nullable)

## Value Graph Schema

Each row is one node:

- `value_id` (int64, unique)
- `parent_id` (int64, nullable)
- `key` (utf8, nullable)
- `index` (int32, nullable)
- `tag` (utf8)
- `v_string` (utf8, nullable)
- `v_int64` (int64, nullable)
- `v_float64` (float64, nullable)
- `v_bool` (bool, nullable)

Supported tags:

- `null`
- `na_logical`
- `na_integer`
- `na_double`
- `na_character`
- `list`
- `map`
- `data_frame`
- `string`
- `int64`
- `float64`
- `bool`

## Strict Validation Rules

1. Graph must contain exactly one root (`parent_id = null`).
2. `value_id` must be unique.
3. Every non-root `parent_id` must exist.
4. Parent/child relation constraints:
   - `list` parent: child must have `index`, must not have `key`.
   - `map`/`data_frame` parent: child must have `key`, must not have `index`.
   - scalar/null/NA parent: must not have children.
5. Container child uniqueness:
   - `list`: `index` values must be unique and non-negative.
   - `map`/`data_frame`: `key` values must be unique.
6. Scalar value-column strictness:
   - `string`: only `v_string` set
   - `int64`: only `v_int64` set
   - `float64`: only `v_float64` set
   - `bool`: only `v_bool` set
   - container/null/NA tags: no value columns set

## Missing Values

- `NULL` maps to `null` tag.
- Typed R missing values map to typed tags:
  - `NA` logical -> `na_logical`
  - `NA_integer_` -> `na_integer`
  - `NA_real_` -> `na_double`
  - `NA_character_` -> `na_character`
- Typed NA tags must remain distinct from `null`.

## Numeric Semantics

- Wire `int64` values map to R double semantics in launcher responses.
- `float64` preserves `NaN`, `Inf`, and `-Inf`.

## R Class Normalization (launcher responses)

To keep the wire contract narrow and predictable, the launcher normalizes selected R classes before encoding:

- `factor` -> `string` values (level labels)
- `ordered` factor -> `string` values (ordered labels)
- `Date` -> ISO date `string` (`YYYY-MM-DD`)
- `POSIXct`/`POSIXlt`/`POSIXt` -> UTC timestamp `string`
- `difftime` -> `float64` seconds

This normalization is applied recursively, including values inside returned data-frame rows.

## Missing-Value Helper API (Nextflow side)

The plugin exposes helpers for explicit missing-value handling in workflow code:

- Predicates:
  - `isNULL(x)`
  - `isNA(x)`
  - `isNALogical(x)`
  - `isNAInteger(x)`
  - `isNADouble(x)`
  - `isNACharacter(x)`
  - `isMissing(x)` (`NULL` or typed `NA`)
- Utilities:
  - `naType(x)` -> `logical|integer|double|character|null`
  - `coalesce(x, fallback)`
  - `coalesceNULL(x, fallback)`
  - `coalesceNA(x, fallback)`
  - `assertNotMissing(x[, label])`
  - `renderValue(x)` -> stable string form (`NULL`, `NA<double>`, etc.)
  - `asLocalDate(x)`
  - `asInstantUtc(x)`
  - `asZonedDateTime(x[, zoneId])`
  - `asDurationSeconds(x)`
