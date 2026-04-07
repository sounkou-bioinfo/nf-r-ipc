package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Path
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import spock.lang.Specification

class RecursiveDataFrameTagIntegrationTest extends Specification {

    def 'should roundtrip recursive payload containing data_frame-like maps'() {
        given:
        def codec = new ArrowJavaCodec()
        Path path = Files.createTempFile('nfr-recursive-df', '.arrows')

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'recursive-df-1',
            status: 'ok',
            result_kind: 'value_graph'
        ]

        Map<String,Object> payload = [
            meta: [batch: 'B1'],
            tbl: [
                sample: ['S1', 'S2'],
                x: [1L, 3L],
                y: [2d, 4d]
            ],
            nested: [
                tables: [
                    [k: ['A', 'B'], v: [10L, 20L]],
                    [k: ['C', 'D'], v: [30L, 40L]]
                ]
            ]
        ]

        when:
        codec.writeRequest(path, control, payload)
        DecodedResponse decoded = codec.readResponse(path)
        Map out = (Map)decoded.data

        then:
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'recursive-df-1', 'value_graph')
        out == payload
    }
}
