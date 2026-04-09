
<!-- README.md is generated from README.Rmd. Please edit this file. -->

# nf-r IPC plugin

`nf-r-ipc` is a Nextflow plugin for Groovy/R interop using Arrow IPC
only.

The implementation uses a strict request/response contract over Arrow
and runs R through an external `Rscript` launcher (`nanoarrow` on the R
side).

- Protocol and type contract: `CONTRACT.md`
- Change log: `NEWS.md`

## Build and install

``` bash
./gradlew test --tests nextflow.nfr.integration.ArrowRoundtripIntegrationTest
#> > Task :compileJava NO-SOURCE
#> > Task :compileGroovy UP-TO-DATE
#> > Task :processResources UP-TO-DATE
#> > Task :classes UP-TO-DATE
#> > Task :extensionPoints UP-TO-DATE
#> > Task :jar
#> > Task :packagePlugin
#> > Task :assemble
#> > Task :compileTestJava NO-SOURCE
#> > Task :compileTestGroovy UP-TO-DATE
#> > Task :processTestResources UP-TO-DATE
#> > Task :testClasses UP-TO-DATE
#> > Task :test UP-TO-DATE
#> 
#> [Incubating] Problems report is available at: file:///root/nf-r-ipc/build/reports/problems/problems-report.html
#> 
#> Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.
#> 
#> You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
#> 
#> For more on this, please refer to https://docs.gradle.org/8.14/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
#> 
#> BUILD SUCCESSFUL in 686ms
#> 8 actionable tasks: 2 executed, 6 up-to-date
```

``` bash
make install
#> ./gradlew install
#> > Task :compileJava NO-SOURCE
#> > Task :compileGroovy UP-TO-DATE
#> > Task :processResources UP-TO-DATE
#> > Task :classes UP-TO-DATE
#> > Task :extensionPoints UP-TO-DATE
#> > Task :jar UP-TO-DATE
#> > Task :packagePlugin UP-TO-DATE
#> > Task :assemble UP-TO-DATE
#> 
#> > Task :installPlugin
#> Plugin nf-r-ipc installed successfully!
#> Installation location: /root/.nextflow/plugins
#> Installation location determined by - Default location (~/.nextflow/plugins)
#> 
#> [Incubating] Problems report is available at: file:///root/nf-r-ipc/build/reports/problems/problems-report.html
#> 
#> Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.
#> 
#> You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
#> 
#> For more on this, please refer to https://docs.gradle.org/8.14/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
#> 
#> BUILD SUCCESSFUL in 492ms
#> 6 actionable tasks: 1 executed, 5 up-to-date
```

## Mental model

Think of `rFunction(...)` as returning an envelope:

- `control`: status and diagnostics
- `decoded_data`: decoded payload (nested maps/lists, records, missing
  markers)
- `runtime`: interpreter metadata

Core entry points:

- `rFunction(args, codeOrScript)` -\> full envelope
- `rTable(args, codeOrScript)` -\> list-of-records table result
- `rRecords(args, codeOrScript)` -\> list-of-records helper
- `channelFromR(args, codeOrScript)` -\> channel directly

## First working call

``` nextflow
include { rFunction } from 'plugin/nf-r-ipc'

workflow {
  def out = rFunction([
      function: 'echo',
      _executable: 'Rscript',
      sample: 'S1',
      values: [1, 2, 3],
      meta: [batch: 'B1', flags: [true, false, null]]
  ], '''
      echo <- function(sample, values, meta) {
        list(sample = sample, values = values, meta = meta)
      }
  ''')

  assert out.control.status == 'ok'
  println "runtime=${out.runtime.command}"
  println "decoded=${out.decoded_data}"
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpZrKvmM/readme-nextflow-12bb917cbd1bd5.nf` [adoring_magritte] DSL2 - revision: 02574ee18f
#> 
#> runtime=[Rscript]
#> decoded=[sample:S1, values:[1.0, 2.0, 3.0], meta:[batch:B1, flags:[true, false, null]]]
```

## Type mapping

Current type behavior:

- Scalars: integers/doubles/booleans/strings
- Containers: maps/lists (recursive)
- Table-like values:
  - list-of-records inputs are materialized as `data.frame` in R
  - `data.frame` outputs are normalized to list-of-records
  - map-of-equal-length scalar-columns uses `data_frame` tag
- `int64` in R is represented via R numeric semantics (`double`)

### Missing values: `NULL` vs typed `NA`

The plugin keeps these distinct:

- `NULL` -\> `null`
- `NA` -\> typed markers (`LOGICAL`, `INTEGER`, `DOUBLE`, `CHARACTER`)

Use helper functions from the plugin API:

- Predicates: `isNULL`, `isNA`, `isNALogical`, `isNAInteger`,
  `isNADouble`, `isNACharacter`
- Utilities: `naType`, `isMissing`, `coalesce`, `assertNotMissing`

``` nextflow
include { rFunction; isNULL; isNA; isNALogical; isNAInteger; isNADouble; isNACharacter; naType; isMissing; coalesce; assertNotMissing } from 'plugin/nf-r-ipc'

workflow {
  def out = rFunction([
      function: 'emit_missing',
      _on_error: 'return',
      _executable: 'Rscript'
  ], '''
      emit_missing <- function() {
        list(
          null_value = NULL,
          na_logical = NA,
          na_integer = NA_integer_,
          na_double = NA_real_,
          na_character = NA_character_,
          nested = list(NA_real_, NULL, NA_character_)
        )
      }
  ''')

  assert out.control.status == 'ok'

  assert isNULL(out.decoded_data.null_value)
  assert isNA(out.decoded_data.na_logical)
  assert isNA(out.decoded_data.na_integer)
  assert isNA(out.decoded_data.na_double)
  assert isNA(out.decoded_data.na_character)

  assert isNALogical(out.decoded_data.na_logical)
  assert isNAInteger(out.decoded_data.na_integer)
  assert isNADouble(out.decoded_data.na_double)
  assert isNACharacter(out.decoded_data.na_character)

  assert isNADouble(out.decoded_data.nested[0])
  assert isNULL(out.decoded_data.nested[1])
  assert isNACharacter(out.decoded_data.nested[2])

  assert naType(out.decoded_data.na_double) == 'double'
  assert isMissing(out.decoded_data.null_value)
  assert isMissing(out.decoded_data.na_character)
  assert !isMissing('ok')

  assert coalesce(out.decoded_data.null_value, 'fallback-null') == 'fallback-null'
  assert coalesce(out.decoded_data.na_integer, 99) == 99
  assert coalesce('present', 'fallback') == 'present'

  assert assertNotMissing('S1', 'sample_id') == 'S1'

  println "decoded=${out.decoded_data}"
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpZrKvmM/readme-nextflow-12bb91416bc49e.nf` [ridiculous_allen] DSL2 - revision: 139bf87e1b
#> 
#> decoded=[null_value:null, na_logical:LOGICAL, na_integer:INTEGER, na_double:DOUBLE, na_character:CHARACTER, nested:[DOUBLE, null, CHARACTER]]
```

## Error handling modes

- Default: throw on `status=error`
- `_on_error: 'return'`: return envelope with `control.status='error'`

``` nextflow
include { rFunction } from 'plugin/nf-r-ipc'

workflow {
  def err = rFunction([
      function: 'boom_fn',
      _on_error: 'return',
      _executable: 'Rscript'
  ], '''
      boom_fn <- function() {
        stop('boom for diagnostics')
      }
  ''')

  assert err.control.status == 'error'
  println "error_class=${err.control.error_class}"
  println "error_message=${err.control.error_message}"
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpZrKvmM/readme-nextflow-12bb9140435610.nf` [spontaneous_boyd] DSL2 - revision: e4d574c15c
#> 
#> error_class=RRuntimeError
#> error_message=boom for diagnostics
```

## Table and channels

Use `rTable(...)` when you want records directly:

``` nextflow
include { rTable } from 'plugin/nf-r-ipc'

workflow {
  def rows = rTable([
      function: 'make_rows',
      _executable: 'Rscript'
  ], '''
      make_rows <- function() {
        data.frame(sample = c('S1','S2'), x = c(1,2), stringsAsFactors = FALSE)
      }
  ''')

  assert rows.size() == 2
  println "rows=${rows}"
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpZrKvmM/readme-nextflow-12bb915da058a6.nf` [high_wilson] DSL2 - revision: ca11b15d66
#> 
#> rows=[[sample:S1, x:1.0], [sample:S2, x:2.0]]
```

Use `channelFromR(...)` when you want channel-first style:

``` nextflow
include { channelFromR; coalesce } from 'plugin/nf-r-ipc'

workflow {
  ch = channelFromR([
      function: 'make_rows',
      _executable: 'Rscript'
  ], '''
      make_rows <- function() {
        data.frame(sample = c('S1','S2'), x = c(1, NA), stringsAsFactors = FALSE)
      }
  ''')

  ch.map { row -> [sample: row.sample, x2: coalesce(row.x, 0) * 2] }
    .view { row -> "ROW ${row.sample} x2=${row.x2}" }
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpZrKvmM/readme-nextflow-12bb917eb6b0ec.nf` [cheesy_plateau] DSL2 - revision: c2d18268f6
#> 
#> ROW S1 x2=2.0
#> ROW S2 x2=0
```

### Table-mode strictness

Table mode (`_payload_kind: 'table'` or `rTable`) is fail-fast:

- accepted shapes: list-of-records or map-of-equal-length scalar-columns
- invalid shape raises `CodecException`

## Runtime selection

Runtime selectors:

- `_executable`: direct interpreter path/name
- `_conda_env`: conda env name or prefix path
- `_r_libs`: sets `R_LIBS`

`_executable` and `_conda_env` are mutually exclusive.

``` bash
cat > /tmp/nfr_runtime_guard_example.nf <<'NF'
include { rFunction } from 'plugin/nf-r-ipc'

workflow {
  rFunction([
    function: 'f',
    _executable: 'Rscript',
    _conda_env: '/root/miniconda3'
  ], '''
    f <- function() list()
  ''')
}
NF

set +e
nextflow run /tmp/nfr_runtime_guard_example.nf -plugins nf-r-ipc@0.2.0 >/tmp/nfr_runtime_guard_example.log 2>&1
status=$?
set -e
echo "exit=$status"
grep -E "cannot be used together" /tmp/nfr_runtime_guard_example.log || true
#> exit=1
#> ERROR ~ The '_executable' and '_conda_env' options cannot be used together
```

Conda executable resolution order:

- `nfR.conda_executable`
- `NFR_CONDA_EXE`
- `conda` on `PATH`

`NFR_CONDA_EXE` override demo:

``` bash
if [ -x /root/miniconda3/bin/conda ]; then
  export NFR_CONDA_EXE=/root/miniconda3/bin/conda
  nextflow run validation/conda_main.nf -plugins nf-r-ipc@0.2.0 -c validation/nextflow.config
else
  echo "Conda binary not found at /root/miniconda3/bin/conda; skipping override demo"
fi
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `validation/conda_main.nf` [small_swirles] DSL2 - revision: fbd21a0aa8
#> 
#> OK status=ok codec=arrow-java
#> OK runtime=[/usr/bin/Rscript]
#> OK decoded=[sample:S1, values:[1.0, 2.0, 3.0], meta:[batch:B1, flags:[true, false, null]]]
#> ERR status=error
```

## End-to-end example

This one captures nested structures, typed missing values, table output,
and channel transformation.

``` nextflow
include { rFunction; rTable; channelFromR; isMissing; isNADouble; coalesce } from 'plugin/nf-r-ipc'

workflow {
  def env = rFunction([
      function: 'analyze',
      _on_error: 'return',
      _executable: 'Rscript',
      params_map: [batch: 'B1', flag: true],
      rows: [[sample: 'S1', x: 1], [sample: 'S2', x: 2]]
  ], '''
      analyze <- function(params_map, rows) {
        rows$score <- c(10, NA_real_)
        list(meta = params_map, rows = rows, notes = list(NULL, NA_character_))
      }
  ''')

  assert env.control.status == 'ok'
  assert env.decoded_data.meta.batch == 'B1'
  assert isNADouble(env.decoded_data.rows[1].score)
  assert isMissing(env.decoded_data.notes[0])

  def rows = rTable([
      function: 'make_rows',
      _executable: 'Rscript'
  ], '''
      make_rows <- function() {
        data.frame(sample = c('S1','S2'), x = c(1, NA), stringsAsFactors = FALSE)
      }
  ''')

  Channel
    .fromList(rows)
    .map { row -> [sample: row.sample, x2: coalesce(row.x, 0) * 2] }
    .view { row -> "TABLE ${row.sample} x2=${row.x2}" }

  ch = channelFromR([
      function: 'make_rows2',
      _executable: 'Rscript'
  ], '''
      make_rows2 <- function() {
        data.frame(sample = c('A','B'), v = c(3, 4), stringsAsFactors = FALSE)
      }
  ''')

  ch.map { row -> [sample: row.sample, v3: row.v * 3] }
    .view { row -> "CHAN ${row.sample} v3=${row.v3}" }
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpZrKvmM/readme-nextflow-12bb911e1e0535.nf` [nasty_shockley] DSL2 - revision: 2e081cf29f
#> 
#> TABLE S1 x2=2.0
#> CHAN A v3=9.0
#> TABLE S2 x2=0
#> CHAN B v3=12.0
```
