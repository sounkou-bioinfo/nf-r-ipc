package nextflow.nfr.integration

import nextflow.nfr.RExtension
import spock.lang.Specification

class RRecordsIntegrationTest extends Specification {

    static class TestRExtension extends RExtension {
        @Override
        protected LaunchResult runRscript(Map<String, Object> launch, java.nio.file.Path scratch, java.nio.file.Path requestIpc, java.nio.file.Path responseIpc, String code) {
            java.nio.file.Files.copy(requestIpc, responseIpc)
            return new LaunchResult(0, 'ok')
        }
    }

    def 'rRecords should return list-of-records for list input'() {
        given:
        def ext = new TestRExtension()

        when:
        def out = ext.rRecords([
            function: 'f',
            script: 'x.R',
            rows: [[sample: 'S1', x: 1], [sample: 'S2', x: 2]],
            _on_error: 'return'
        ], '')

        then:
        out == [[sample: 'S1', x: 1L], [sample: 'S2', x: 2L]]
    }

    def 'rRecords should convert map-of-columns result shape'() {
        given:
        def ext = new TestRExtension()

        when:
        def out = ext.rRecords([
            function: 'f',
            script: 'x.R',
            cols: [sample: ['S1', 'S2'], x: [1, 2]],
            _on_error: 'return'
        ], '')

        then:
        out == [[sample: 'S1', x: 1L], [sample: 'S2', x: 2L]]
    }
}
