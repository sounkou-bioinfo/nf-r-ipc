
<!-- README.md is generated from README.Rmd. Please edit that file -->

# nf-r IPC plugin

`nf-r-ipc` is a Nextflow plugin prototype for Groovy/R interop using
Arrow IPC and a value-graph payload for nested list/map arguments.

The R launcher uses `nanoarrow::read_nanoarrow()` and
`nanoarrow::write_nanoarrow()` for Arrow IPC stream I/O.

## Build and test

``` bash
./gradlew test --tests nextflow.nfr.integration.ArrowRoundtripIntegrationTest
#> > Task :compileJava NO-SOURCE
#> > Task :compileGroovy UP-TO-DATE
#> > Task :processResources UP-TO-DATE
#> > Task :classes UP-TO-DATE
#> > Task :extensionPoints UP-TO-DATE
#> > Task :jar UP-TO-DATE
#> > Task :packagePlugin UP-TO-DATE
#> > Task :assemble UP-TO-DATE
#> > Task :compileTestJava NO-SOURCE
#> > Task :compileTestGroovy UP-TO-DATE
#> > Task :processTestResources UP-TO-DATE
#> > Task :testClasses UP-TO-DATE
#> > Task :test UP-TO-DATE
#> 
#> BUILD SUCCESSFUL in 983ms
#> 8 actionable tasks: 8 up-to-date
```

## Install locally

Run this once before examples:

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
#> > Task :installPlugin UP-TO-DATE
#> 
#> BUILD SUCCESSFUL in 1s
#> 6 actionable tasks: 6 up-to-date
```

## Example Nextflow pipeline

The repository includes `validation/main.nf` using:

- `include { rFunction } from 'plugin/nf-r-ipc'`
- nested list/map arguments
- `_on_error: 'return'` call-level option

``` nextflow
include { rFunction } from 'plugin/nf-r-ipc'

workflow {
    def ok = rFunction([
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

    println "OK status=${ok.control.status ?: 'ok'} codec=${ok.codec}"
    println "OK runtime=${ok.runtime.command}"
    println "OK decoded=${ok.decoded_data}"

    def err = rFunction([
        function: 'explode',
        _on_error: 'return',
        _executable: 'Rscript',
        trigger: 'error'
    ], '''
        explode <- function(trigger) {
          stop('boom from R runtime')
        }
    ''')
    println "ERR status=${err.control.status ?: 'ok'}"
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpqJ8DCd/readme-nextflow-78e1674ba83a2.nf` [hopeful_becquerel] DSL2 - revision: 3efd8eaaac
#> 
#> SLF4J(E): A service provider failed to instantiate:
#> org.slf4j.spi.SLF4JServiceProvider: ch.qos.logback.classic.spi.LogbackServiceProvider not a subtype
#> SLF4J(W): No SLF4J providers were found.
#> SLF4J(W): Defaulting to no-operation (NOP) logger implementation
#> SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
#> OK status=ok codec=arrow-java
#> OK runtime=[Rscript]
#> OK decoded=[sample:S1, values:[1.0, 2.0, 3.0], meta:[batch:B1, flags:[true, false, null]]]
#> ERR status=error
```

This example executes real inline R functions (`echo`, `explode`)
through the bundled launcher and reports `status=ok`/`status=error`
envelopes.

## Conda-backed example

`validation/conda_main.nf` demonstrates `_conda_env` resolution, with
`validation/nextflow.config` setting:

- `plugins.id = 'nf-r-ipc'`
- `nfR.conda_executable = '/root/miniconda3/bin/conda'`

``` nextflow
include { rFunction } from 'plugin/nf-r-ipc'

workflow {
    def ok = rFunction([
        function: 'echo',
        _conda_env: '/root/miniconda3',
        sample: 'S1',
        values: [1, 2, 3],
        meta: [batch: 'B1', flags: [true, false, null]]
    ], '''
        echo <- function(sample, values, meta) {
          list(sample = sample, values = values, meta = meta)
        }
    ''')

    println "OK status=${ok.control.status ?: 'ok'} codec=${ok.codec}"
    println "OK runtime=${ok.runtime.command}"
    println "OK decoded=${ok.decoded_data}"
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpqJ8DCd/readme-nextflow-78e1633a6bb60.nf` [stoic_heyrovsky] DSL2 - revision: 2774f29512
#> 
#> SLF4J(E): A service provider failed to instantiate:
#> org.slf4j.spi.SLF4JServiceProvider: ch.qos.logback.classic.spi.LogbackServiceProvider not a subtype
#> SLF4J(W): No SLF4J providers were found.
#> SLF4J(W): Defaulting to no-operation (NOP) logger implementation
#> SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
#> OK status=ok codec=arrow-java
#> OK runtime=[/usr/bin/Rscript]
#> OK decoded=[sample:S1, values:[1.0, 2.0, 3.0], meta:[batch:B1, flags:[true, false, null]]]
```

Expected output shape:

- `OK status=ok codec=arrow-java`
- `OK runtime=[...]`
- `ERR status=error`

## External R script with Conda

`validation/conda_script_main.nf` uses `script:` to load R functions
from:

- `validation/scripts/echo_external.R`

``` nextflow
include { rFunction } from 'plugin/nf-r-ipc'

workflow {
    def ok = rFunction([
        function: 'echo_external',
        script: 'validation/scripts/echo_external.R',
        _conda_env: '/root/miniconda3',
        sample: 'S1',
        values: [1, 2, 3],
        meta: [batch: 'B1', flags: [true, false, null]]
    ])

    println "OK status=${ok.control.status ?: 'ok'} codec=${ok.codec}"
    println "OK runtime=${ok.runtime.command}"
    println "OK decoded=${ok.decoded_data}"
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpqJ8DCd/readme-nextflow-78e16667b66a9.nf` [spontaneous_sinoussi] DSL2 - revision: 7b069adc40
#> 
#> SLF4J(E): A service provider failed to instantiate:
#> org.slf4j.spi.SLF4JServiceProvider: ch.qos.logback.classic.spi.LogbackServiceProvider not a subtype
#> SLF4J(W): No SLF4J providers were found.
#> SLF4J(W): Defaulting to no-operation (NOP) logger implementation
#> SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
#> OK status=ok codec=arrow-java
#> OK runtime=[/usr/bin/Rscript]
#> OK decoded=[sample:S1-external, values:[1.0, 2.0, 3.0], meta:[batch:B1, flags:[true, false, null]]]
```

## Recursive data frame-like values

This example shows a recursive payload where a data frame-like structure
is represented as a named map of equal-length columns. The value graph
tags this shape as `data_frame`, decodes it to an R `data.frame`, and
returns list-of-records after mutation.

``` nextflow
include { rFunction } from 'plugin/nf-r-ipc'

workflow {
    def out = rFunction([
        function: 'mutate_df',
        tbl: [
            [sample: 'S1', x: 1, y: 2],
            [sample: 'S2', x: 3, y: 4]
        ],
        meta: [batch: 'B1']
    ], '''
        mutate_df <- function(tbl, meta) {
          stopifnot(is.data.frame(tbl))
          tbl$score <- tbl$x + tbl$y
          list(tbl = tbl, meta = meta)
        }
    ''')

    println "status=${out.control.status ?: 'ok'}"
    println "decoded=${out.decoded_data}"
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpqJ8DCd/readme-nextflow-78e1666cf65f8.nf` [dreamy_volta] DSL2 - revision: beccbcb99f
#> 
#> SLF4J(E): A service provider failed to instantiate:
#> org.slf4j.spi.SLF4JServiceProvider: ch.qos.logback.classic.spi.LogbackServiceProvider not a subtype
#> SLF4J(W): No SLF4J providers were found.
#> SLF4J(W): Defaulting to no-operation (NOP) logger implementation
#> SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
#> status=ok
#> decoded=[tbl:[[sample:S1, x:1.0, y:2.0, score:3.0], [sample:S2, x:3.0, y:4.0, score:7.0]], meta:[batch:B1]]
```

## Channel-oriented records helper

`rRecords(...)` is a channel-friendly helper that guarantees
list-of-records output. You can pass this result directly to
`Channel.fromList(...)`.

``` nextflow
include { rRecords } from 'plugin/nf-r-ipc'

workflow {
    def rows = rRecords([
        function: 'make_rows',
        _executable: 'Rscript'
    ], '''
        make_rows <- function() {
          data.frame(
            sample = c('S1', 'S2'),
            x = c(1, 2),
            stringsAsFactors = FALSE
          )
        }
    ''')

    Channel.fromList(rows)
      .map { row -> [sample: row.sample, x2: row.x * 2] }
      .view { row -> "ROW ${row.sample} x2=${row.x2}" }
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpqJ8DCd/readme-nextflow-78e16444c740a.nf` [extravagant_heyrovsky] DSL2 - revision: 277cebeada
#> 
#> SLF4J(E): A service provider failed to instantiate:
#> org.slf4j.spi.SLF4JServiceProvider: ch.qos.logback.classic.spi.LogbackServiceProvider not a subtype
#> SLF4J(W): No SLF4J providers were found.
#> SLF4J(W): Defaulting to no-operation (NOP) logger implementation
#> SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
#> ROW S1 x2=2.0
#> ROW S2 x2=4.0
```

## Channel error branching

Use `_on_error: 'return'` when you want to keep channel flow alive and
branch/filter on envelope status.

``` nextflow
include { rFunction } from 'plugin/nf-r-ipc'

workflow {
    def ok = rFunction([
        function: 'ok_fn',
        _on_error: 'return',
        _executable: 'Rscript',
        sample: 'S1'
    ], '''
        ok_fn <- function(sample) {
          list(sample = sample, status = 'ok')
        }
    ''')

    def err = rFunction([
        function: 'boom_fn',
        _on_error: 'return',
        _executable: 'Rscript',
        sample: 'S2'
    ], '''
        boom_fn <- function(sample) {
          stop('boom in channel flow')
        }
    ''')

    Channel.of(ok, err)
      .map { e -> [status: e.control.status ?: 'ok', payload: e.decoded_data, error: e.control.error_message] }
      .view { row -> "status=${row.status} payload=${row.payload} error=${row.error}" }
}
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `/tmp/RtmpqJ8DCd/readme-nextflow-78e1656d72ef9.nf` [friendly_gautier] DSL2 - revision: cf3d01a459
#> 
#> SLF4J(E): A service provider failed to instantiate:
#> org.slf4j.spi.SLF4JServiceProvider: ch.qos.logback.classic.spi.LogbackServiceProvider not a subtype
#> SLF4J(W): No SLF4J providers were found.
#> SLF4J(W): Defaulting to no-operation (NOP) logger implementation
#> SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
#> status=ok payload=[sample:S1, status:ok] error=null
#> status=error payload=null error=boom in channel flow
```

## Error-handling modes

- Default: throw on `status=error`
- Per-call override: `_on_error: 'return'` to return structured error
  envelope instead of throwing
- Config default: `nfR.on_error = 'throw'|'return'`

## R runtime selection

The plugin now supports per-call runtime selectors similar to
`nf-python`:

- `_executable`: explicit `Rscript` path (or interpreter name)
- `_conda_env`: Conda env name or prefix path
- `_r_libs`: value for R libs search path wiring

`_executable` and `_conda_env` are mutually exclusive.

Type mapping notes (current):

- **Scalars and containers**
  - Groovy scalar numbers map to encoded `int64`/`float64` values
  - Groovy lists/maps map to R lists / named lists
  - Groovy primitive arrays are encoded as list values
  - R atomic vectors (`c(...)`) are encoded as list values on return
  - list-of-records arguments are materialized as `data.frame` when
    passed to R functions
  - R `data.frame` results are normalized back to list-of-records on
    return
  - recursive data-frame-like maps (named equal-length list columns) are
    tagged as `data_frame` in the value graph
- **`int64` treatment in R**
  - R has no native `int64` scalar type in base runtime
  - launcher responses map `int64` wire values to R `double` semantics
- **Missing values (`NA`) vs `NULL`**
  - R `NULL` maps to wire `null`
  - typed R missing values map to typed NA tags in the value graph:
    - `NA` logical -\> `na_logical`
    - `NA_integer_` -\> `na_integer`
    - `NA_real_` -\> `na_double`
    - `NA_character_` -\> `na_character`
  - these tags are preserved across round-trip and remain distinct from
    `null`

Resolution order is call option, then config, then default:

- `nfR.executable` (default `Rscript`)
- `nfR.conda_env`
- `nfR.r_libs`

When `_conda_env` is set:

- env spec is resolved via Nextflow `CondaCache` (same style used in
  `nf-python`)
- plugin resolves concrete `Rscript` path with:
  - `conda run -p <resolved_env_path> which Rscript`
  - then executes that resolved interpreter directly

Conda executable resolution uses:

- `nfR.conda_executable` when configured
- else `NFR_CONDA_EXE`
- else `conda` from `PATH`
