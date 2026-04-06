package nextflow.nfr.integration

import java.nio.file.Files
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import nextflow.nfr.value.NAValue
import spock.lang.Specification

class NAValueIntegrationTest extends Specification {

    def 'should roundtrip NA markers and null distinctly through arrow codec'() {
        given:
        def codec = new ArrowJavaCodec()
        def path = Files.createTempFile('nfr-na', '.arrows')

        def control = [
            protocol_version: 1,
            call_id: 'call-na-1',
            status: 'ok',
            result_kind: 'value_graph'
        ]

        def payload = [
            nullValue: null,
            naLogical: NAValue.LOGICAL,
            naInteger: NAValue.INTEGER,
            naDouble: NAValue.DOUBLE,
            naCharacter: NAValue.CHARACTER,
            nested: [NAValue.DOUBLE, null]
        ]

        when:
        codec.writeRequest(path, control, payload)
        DecodedResponse decoded = codec.readResponse(path)
        Map out = (Map)decoded.data

        then:
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'call-na-1', 'value_graph')
        IntegrationAssertions.assertNullVsNaDistinct(out, 'nullValue', 'naLogical', NAValue.LOGICAL)
        out.naInteger == NAValue.INTEGER
        out.naDouble == NAValue.DOUBLE
        out.naCharacter == NAValue.CHARACTER
        ((List)out.nested)[0] == NAValue.DOUBLE
        ((List)out.nested)[1] == null
    }
}
