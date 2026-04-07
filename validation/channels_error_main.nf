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
      .map { envelope -> [status: envelope.control.status ?: 'ok', envelope: envelope] }
      .branch {
        success: it.status == 'ok'
        failure: it.status == 'error'
      }

    Channel.of(ok, err)
      .map { e -> [status: e.control.status ?: 'ok', payload: e.decoded_data] }
      .filter { row -> row.status == 'ok' }
      .view { row -> "OK payload=${row.payload}" }

    Channel.of(ok, err)
      .map { e -> [status: e.control.status ?: 'ok', error: e.control.error_message] }
      .filter { row -> row.status == 'error' }
      .view { row -> "ERR message=${row.error}" }
}
