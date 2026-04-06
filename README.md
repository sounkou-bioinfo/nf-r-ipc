
<!-- README.md is generated from README.Rmd. Please edit that file -->

# nf-r IPC plugin

`nf-r-ipc` is a Nextflow plugin prototype for Groovy/R interop using
Arrow IPC and a value-graph payload for nested list/map arguments.

The R launcher uses `nanoarrow::read_nanoarrow()` and
`nanoarrow::write_nanoarrow()` for Arrow IPC stream I/O.

## Build and test

``` nextflow
./gradlew test --tests nextflow.nfr.integration.ArrowRoundtripIntegrationTest
#> 
#> > Task :help
#> 
#> Welcome to Gradle 8.14.
#> 
#> To run a build, run gradlew <task> ...
#> 
#> To see a list of available tasks, run gradlew tasks
#> 
#> To see more detail about a task, run gradlew help --task <task>
#> 
#> To see a list of command-line options, run gradlew --help
#> 
#> For more detail on using Gradle, see https://docs.gradle.org/8.14/userguide/command_line_interface.html
#> 
#> For troubleshooting, visit https://help.gradle.org
#> 
#> BUILD SUCCESSFUL in 384ms
#> 1 actionable task: 1 executed
```

## Example Nextflow pipeline

The repository includes `validation/main.nf` using:

- `include { rFunction } from 'plugin/nf-r-ipc'`
- nested list/map arguments
- `_on_error: 'return'` call-level option

``` bash
cat  validation/main.nf
#> include { rFunction } from 'plugin/nf-r-ipc'
#> 
#> workflow {
#>     def ok = rFunction([
#>         function: 'echo',
#>         _executable: 'Rscript',
#>         sample: 'S1',
#>         values: [1, 2, 3],
#>         meta: [batch: 'B1', flags: [true, false, null]]
#>     ], '''
#>         echo <- function(sample, values, meta) {
#>           list(sample = sample, values = values, meta = meta)
#>         }
#>     ''')
#> 
#>     println "OK status=${ok.control.status ?: 'ok'} codec=${ok.codec}"
#>     println "OK runtime=${ok.runtime.command}"
#>     println "OK decoded=${ok.decoded_data}"
#> 
#>     // Demonstrate call-level error handling contract without throwing.
#>     // In current bootstrap, this is a shape example until external R launcher is wired.
#>     def err = rFunction([
#>         function: 'explode',
#>         _on_error: 'return',
#>         _executable: 'Rscript',
#>         trigger: 'error'
#>     ], '''
#>         explode <- function(trigger) {
#>           stop('boom from R runtime')
#>         }
#>     ''')
#>     println "ERR status=${err.control.status ?: 'ok'}"
#> }
```

Run it after installing the plugin locally:

``` nextflow
make install
nextflow run validation/main.nf -plugins nf-r-ipc@0.1.0
#> ./gradlew assemble
#> > Task :compileJava UP-TO-DATE
#> > Task :compileGroovy UP-TO-DATE
#> > Task :processResources UP-TO-DATE
#> > Task :classes UP-TO-DATE
#> > Task :extensionPoints UP-TO-DATE
#> > Task :jar UP-TO-DATE
#> > Task :packagePlugin UP-TO-DATE
#> > Task :assemble UP-TO-DATE
#> 
#> BUILD SUCCESSFUL in 378ms
#> 6 actionable tasks: 6 up-to-date
#> [33mNextflow 25.10.4 is available - Please consider updating your version to it(B[m
#> 
#>  N E X T F L O W   ~  version 25.10.2
#> 
#> Launching `validation/main.nf` [happy_ardinghelli] DSL2 - revision: c74e91c0e9
#> 
#> SLF4J(E): A service provider failed to instantiate:
#> org.slf4j.spi.SLF4JServiceProvider: ch.qos.logback.classic.spi.LogbackServiceProvider not a subtype
#> SLF4J(W): No SLF4J providers were found.
#> SLF4J(W): Defaulting to no-operation (NOP) logger implementation
#> SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
#> OK status=ok codec=arrow-java
#> OK runtime=[Rscript]
#> OK decoded=[sample:S1, values:[1, 2, 3], meta:[batch:B1, flags:[true, false, null]]]
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

Run:

``` nextflow
nextflow run validation/conda_main.nf -plugins nf-r-ipc@0.1.0 -c validation/nextflow.config
```

Expected output shape:

- `OK status=ok codec=arrow-java`
- `OK runtime=[...]`
- `ERR status=error`

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
