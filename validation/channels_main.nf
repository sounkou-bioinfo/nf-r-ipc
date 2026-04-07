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

    ch = Channel.fromList(rows)
    ch.map { row -> [sample: row.sample, x2: row.x * 2] }
      .view { row -> "ROW ${row.sample} x2=${row.x2}" }
}
