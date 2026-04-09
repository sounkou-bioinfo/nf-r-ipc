include { channelFromR } from 'plugin/nf-r-ipc'

workflow {
    ch = channelFromR([
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

    ch.map { row -> [sample: row.sample, x3: row.x * 3] }
      .view { row -> "ROW ${row.sample} x3=${row.x3}" }
}
