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

    def err = rFunction([
        function: 'explode_external',
        script: 'validation/scripts/echo_external.R',
        _on_error: 'return',
        _conda_env: '/root/miniconda3',
        trigger: 'error'
    ])
    println "ERR status=${err.control.status ?: 'ok'}"
}
