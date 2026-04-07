package nextflow.nfr.integration

import nextflow.nfr.RExtension
import nextflow.nfr.value.NAValue
import spock.lang.Specification

class RRecordsNAIntegrationTest extends Specification {

    static class TestRExtension extends RExtension {
        @Override
        protected int runRscript(Map<String, Object> launch, java.nio.file.Path scratch, java.nio.file.Path requestIpc, java.nio.file.Path responseIpc, String code) {
            java.nio.file.Files.copy(requestIpc, responseIpc)
            return 0
        }
    }

    def 'rRecords should preserve typed NA markers in column-style results'() {
        given:
        def ext = new TestRExtension()

        when:
        def out = ext.rRecords([
            function: 'f',
            script: 'x.R',
            _on_error: 'return',
            cols: [
                sample: ['S1', 'S2'],
                score: [NAValue.DOUBLE, 2.5d],
                flag: [NAValue.LOGICAL, true]
            ]
        ], '')

        then:
        out.size() == 2
        out[0].sample == 'S1'
        out[0].score == NAValue.DOUBLE
        out[0].flag == NAValue.LOGICAL
        out[1].sample == 'S2'
        out[1].score == 2.5d
        out[1].flag == true
    }
}
