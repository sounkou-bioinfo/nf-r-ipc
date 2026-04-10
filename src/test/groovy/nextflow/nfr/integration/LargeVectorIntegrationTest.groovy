package nextflow.nfr.integration

import nextflow.nfr.RExtension
import spock.lang.Specification

class LargeVectorIntegrationTest extends Specification {

    def 'should handle large random vector payload from R'() {
        given:
        def ext = new RExtension()

        when:
        Map out = (Map)ext.rFunction([
            function: 'emit_big',
            _executable: 'Rscript'
        ], '''
            emit_big <- function() {
              set.seed(42)
              list(values = as.numeric(runif(20000)))
            }
        ''')

        List values = (List)((Map)out.decoded_data).values

        then:
        out.control.status == 'ok'
        values.size() == 20000
        ((Number)values[0]).doubleValue() >= 0d
        ((Number)values[0]).doubleValue() <= 1d
        ((Number)values[19999]).doubleValue() >= 0d
        ((Number)values[19999]).doubleValue() <= 1d
    }
}
