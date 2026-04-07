
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
#> > Task :test
#> 
#> [Incubating] Problems report is available at: file:///root/nf-r-ipc/build/reports/problems/problems-report.html
#> 
#> Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.
#> 
#> You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
#> 
#> For more on this, please refer to https://docs.gradle.org/8.14/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
#> 
#> BUILD SUCCESSFUL in 1s
#> 8 actionable tasks: 1 executed, 7 up-to-date
```

## Install locally

Run this once before examples:

``` bash
make install
#> make[1]: Entering directory '/root/nf-r-ipc'
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
#> BUILD SUCCESSFUL in 473ms
#> 6 actionable tasks: 1 executed, 5 up-to-date
#> make[1]: Leaving directory '/root/nf-r-ipc'
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
#> Launching `/tmp/Rtmpv2H1Dz/readme-nextflow-66084529d7de4.nf` [drunk_mcclintock] DSL2 - revision: 3efd8eaaac
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
#> Launching `/tmp/Rtmpv2H1Dz/readme-nextflow-6608437dc852a.nf` [lonely_maxwell] DSL2 - revision: 2774f29512
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
#> Launching `/tmp/Rtmpv2H1Dz/readme-nextflow-66084244c6878.nf` [backstabbing_monod] DSL2 - revision: 7b069adc40
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

Type mapping notes (current):

- Groovy scalar numbers -\> R numeric scalars (int64/float64 encoded)
- Groovy lists/maps -\> R lists / named lists
- Groovy primitive arrays -\> encoded as list values
- R atomic vectors (`c(...)`) -\> encoded as list values on return
- `int64` values map to R `double` semantics in launcher responses
- R `NULL` remains `null`; typed missing values are preserved via NA
  tags in the value graph

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

`_executable` and `_conda_env` are mutually exclusive.

Conda executable resolution uses:

- `nfR.conda_executable` when configured
- else `NFR_CONDA_EXE`
- else `conda` from `PATH`
