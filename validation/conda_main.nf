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

    def err = rFunction([
        function: 'explode',
        _on_error: 'return',
        _conda_env: '/root/miniconda3',
        trigger: 'error'
    ], '''
        explode <- function(trigger) {
          stop('boom from R runtime')
        }
    ''')
    println "ERR status=${err.control.status ?: 'ok'}"
}
