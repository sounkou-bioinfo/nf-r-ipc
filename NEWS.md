# NEWS

All notable changes to `nf-r-ipc` are documented in this file.

## [Unreleased]

### Added
- Typed error classes for clearer failure modes:
  - `NfrRuntimeException` (launcher/runtime failures)
  - `NfrResponseException` (structured response errors)
  - `NfrParseException` (helper conversion parsing failures)
- New integration coverage:
  - `LargeVectorIntegrationTest` for large random vector handling

### Changed
- Helper conversion functions now raise typed parse exceptions on malformed input.

### Fixed
- Security hardening: pinned Jackson BOM to `2.21.1` to pull patched `jackson-core` and related modules for GHSA-72hv-8253-57qq.

## [0.2.0] - 2026-04-09

### Added
- Channel-focused helper APIs:
  - `rRecords(...)` for channel-friendly list-of-record outputs
  - `rTable(...)` as a convenience alias for table payload mode
  - `channelFromR(...)` for direct channel factory style record emission
- Payload mode selector `_payload_kind` with supported values:
  - `value_graph` (default)
  - `table`
- Recursive value-graph support for data-frame-like values through `data_frame` tag.
- `CONTRACT.md` with strict protocol/type invariants for control and value-graph payloads.
- Extended integration coverage for:
  - atomic vectors
  - typed NA handling
  - recursive dataframe mappings
  - launcher script mode and conda mode
- Additional validation pipelines:
  - `validation/channels_main.nf`
  - `validation/channels_error_main.nf`
  - `validation/conda_script_main.nf`
- Missing-value helper predicates/utilities in plugin API:
  - `isNULL`, `isNA`, `isNALogical`, `isNAInteger`, `isNADouble`, `isNACharacter`
  - `naType`, `isMissing`, `coalesce`, `coalesceNULL`, `coalesceNA`, `assertNotMissing`, `renderValue`
- Temporal conversion helpers in plugin API:
  - `asLocalDate`, `asInstantUtc`, `asZonedDateTime`, `asDurationSeconds`
- Extended normalized R class support:
  - `ordered` factor -> string labels
  - `difftime` -> numeric seconds
- Integration test coverage for normalized class handling:
  - `RTypeCoverageIntegrationTest`

### Changed
- Runtime diagnostics now include launcher output tail in thrown errors.
- CI workflows now include README render checks and conda integration coverage.
- Value-graph decode now enforces strict structural validation (single root, parent existence, key/index constraints, scalar value-column rules).
- Table mode now fails fast on invalid shapes instead of best-effort fallback.
- README reorganized into a didactic, end-to-end flow with concept-first sections and executable examples.
- README now includes a real `mtcars` end-to-end example and helper cheat sheet.
- Contract docs now explicitly cover normalized class mapping and helper API behavior.

### Fixed
- Preserved named `NULL` entries in R map outputs (no key dropping during normalization).
- Removed SLF4J provider mismatch runtime warnings by excluding transitive `slf4j-api` from Arrow dependencies.

## [0.1.0]

### Added
- Initial Arrow-only Nextflow↔R bridge MVP via plugin function `rFunction(...)`.
- External `Rscript` launcher contract using Arrow IPC control/data framing.
