# NEWS

All notable changes to `nf-r-ipc` are documented in this file.

## [Unreleased]

## [0.2.0]

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

### Changed
- Runtime diagnostics now include launcher output tail in thrown errors.
- CI workflows now include README render checks and conda integration coverage.
- Value-graph decode now enforces strict structural validation (single root, parent existence, key/index constraints, scalar value-column rules).
- Table mode now fails fast on invalid shapes instead of best-effort fallback.
- Added missing-value helper predicates/utilities in plugin API:
  - `isNULL`, `isNA`, `isNALogical`, `isNAInteger`, `isNADouble`, `isNACharacter`
  - `naType`, `isMissing`, `coalesce`, `assertNotMissing`
- Removed SLF4J provider mismatch runtime warnings by excluding transitive `slf4j-api` from Arrow dependencies.

## [0.1.0]

### Added
- Initial Arrow-only Nextflow↔R bridge MVP via plugin function `rFunction(...)`.
- External `Rscript` launcher contract using Arrow IPC control/data framing.
