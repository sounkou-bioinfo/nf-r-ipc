# NEWS

All notable changes to `nf-r-ipc` are documented in this file.

## [Unreleased]

### Added
- Channel-focused helper APIs:
  - `rRecords(...)` for channel-friendly list-of-record outputs
  - `rTable(...)` as a convenience alias for table payload mode
- Payload mode selector `_payload_kind` with supported values:
  - `value_graph` (default)
  - `table`
- Recursive value-graph support for data-frame-like values through `data_frame` tag.
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

## [0.1.0]

### Added
- Initial Arrow-only Nextflow↔R bridge MVP via plugin function `rFunction(...)`.
- External `Rscript` launcher contract using Arrow IPC control/data framing.
