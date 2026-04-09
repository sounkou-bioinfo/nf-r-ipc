package nextflow.nfr.integration

import groovyx.gpars.dataflow.DataflowQueue
import nextflow.nfr.RExtension
import spock.lang.Specification

class ChannelFromRIntegrationTest extends Specification {

    static class TestRExtension extends RExtension {
        @Override
        protected LaunchResult runRscript(Map<String, Object> launch, java.nio.file.Path scratch, java.nio.file.Path requestIpc, java.nio.file.Path responseIpc, String code) {
            java.nio.file.Files.copy(requestIpc, responseIpc)
            return new LaunchResult(0, 'ok')
        }
    }

    def 'channelFromR should create a channel from records'() {
        given:
        def ext = new TestRExtension()

        when:
        def ch = ext.channelFromR([
            function: 'f',
            script: 'x.R',
            rows: [[sample: 'S1', x: 1], [sample: 'S2', x: 2]]
        ], '')

        then:
        ch instanceof DataflowQueue
    }
}
