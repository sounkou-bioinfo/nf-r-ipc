package nextflow.nfr.integration

import java.nio.file.Files
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import spock.lang.Specification

class ArrowSpecialValuesIntegrationTest extends Specification {

    def 'should roundtrip NaN, Inf, NA-like null, and NULL-like null markers'() {
        given:
        def codec = new ArrowJavaCodec()
        def path = Files.createTempFile('nfr-special', '.arrows')

        def control = [
            protocol_version: 1,
            call_id: 'call-special-1',
            status: 'ok',
            result_kind: 'value_graph'
        ]

        def payload = [
            nan: Double.NaN,
            posInf: Double.POSITIVE_INFINITY,
            negInf: Double.NEGATIVE_INFINITY,
            naValue: null,
            nullValue: null,
            nested: [
                values: [Double.NaN, Double.POSITIVE_INFINITY, null]
            ]
        ]

        when:
        codec.writeRequest(path, control, payload)
        DecodedResponse decoded = codec.readResponse(path)
        Map out = (Map)decoded.data

        then:
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'call-special-1', 'value_graph')
        IntegrationAssertions.assertFiniteOrSpecialDouble(out.nan, true, null)
        IntegrationAssertions.assertFiniteOrSpecialDouble(out.posInf, false, 1)
        IntegrationAssertions.assertFiniteOrSpecialDouble(out.negInf, false, -1)
        IntegrationAssertions.assertNullVsNaDistinct(out, 'nullValue', 'naValue', null)

        and:
        List nestedValues = (List)((Map)out.nested).values
        IntegrationAssertions.assertFiniteOrSpecialDouble(nestedValues[0], true, null)
        IntegrationAssertions.assertFiniteOrSpecialDouble(nestedValues[1], false, 1)
        nestedValues[2] == null
    }
}
