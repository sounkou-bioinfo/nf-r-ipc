package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Paths
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import nextflow.nfr.value.NAValue
import spock.lang.Specification

class ArrowRoundtripIntegrationTest extends Specification {

    def 'should roundtrip nested values and nulls end-to-end through arrow codec'() {
        given:
        def codec = new ArrowJavaCodec()
        def req = Files.createTempFile('nfr-integ-req', '.arrows')
        def resp = Files.createTempFile('nfr-integ-resp', '.arrows')

        def controlReq = [
            protocol_version: 1,
            call_id: 'call-e2e-1',
            function: 'echo',
            script_mode: 'inline',
            script_ref: '<inline>',
            payload_kind: 'value_graph'
        ]

        def payload = [
            intValue: 42L,
            dblValue: 3.5d,
            boolValue: true,
            naValue: null,
            nested: [
                sample: 'S1',
                values: [1L, null, 2L],
                flags: [true, false, null],
                more: [k: 'v', n: null]
            ]
        ]

        and:
        codec.writeRequest(req, controlReq, payload)
        DecodedResponse reqDecoded = codec.readResponse(req)
        assert reqDecoded.data == payload

        when:
        def controlResp = [
            protocol_version: 1,
            call_id: 'call-e2e-1',
            status: 'ok',
            result_kind: 'value_graph'
        ]
        codec.writeRequest(resp, controlResp, reqDecoded.data)
        DecodedResponse respDecoded = codec.readResponse(resp)

        then:
        IntegrationAssertions.assertOkEnvelope(respDecoded.control, 'call-e2e-1', 'value_graph')
        respDecoded.data == payload
    }

    def 'should pass mtcars-like dataframe output as list of records'() {
        given:
        def codec = new ArrowJavaCodec()
        def path = Files.createTempFile('nfr-mtcars', '.arrows')

        def rows = loadMtcarsRows()
        assert rows.size() == 32

        def control = [
            protocol_version: 1,
            call_id: 'call-mtcars-1',
            status: 'ok',
            result_kind: 'table'
        ]

        when:
        codec.writeRequest(path, control, rows)
        DecodedResponse decoded = codec.readResponse(path)

        then:
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'call-mtcars-1', 'table')
        decoded.data == rows
        IntegrationAssertions.assertRecordListShape((List<Map<String,Object>>)decoded.data, 32, ['rownames', 'mpg', 'cyl', 'hp'])

        and:
        Map first = ((List<Map>)decoded.data).first()
        IntegrationAssertions.assertMtcarsRow(first, 'Mazda RX4', 21d, 6L, 110L)

        and:
        Map last = ((List<Map>)decoded.data).last()
        IntegrationAssertions.assertMtcarsRow(last, 'Volvo 142E', 21.4d, 4L, 109L)
    }

    def 'should roundtrip mtcars with typed NA markers and null distinctly'() {
        given:
        def codec = new ArrowJavaCodec()
        def path = Files.createTempFile('nfr-mtcars-na', '.arrows')

        List<Map<String,Object>> rows = loadMtcarsRows()
        assert rows.size() == 32

        // Simulate R-side missing semantics on tabular-like output encoded as records.
        rows[0].mpg = NAValue.DOUBLE
        rows[0].carb = NAValue.INTEGER
        rows[0].rownames = NAValue.CHARACTER
        rows[1].vs = NAValue.LOGICAL
        rows[2].hp = null

        def control = [
            protocol_version: 1,
            call_id: 'call-mtcars-na-1',
            status: 'ok',
            result_kind: 'table'
        ]

        when:
        codec.writeRequest(path, control, rows)
        DecodedResponse decoded = codec.readResponse(path)
        List<Map<String,Object>> out = (List<Map<String,Object>>)decoded.data

        then:
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'call-mtcars-na-1', 'table')
        IntegrationAssertions.assertRecordListShape(out, 32, ['rownames', 'mpg', 'cyl', 'hp'])

        and:
        out[0].mpg == NAValue.DOUBLE
        out[0].carb == NAValue.INTEGER
        out[0].rownames == NAValue.CHARACTER
        out[1].vs == NAValue.LOGICAL
        out[2].hp == null

        and:
        IntegrationAssertions.assertMtcarsRow(out[3], 'Hornet 4 Drive', 21.4d, 6L, 110L)
    }

    private static List<Map<String,Object>> loadMtcarsRows() {
        def path = Paths.get('src/test/resources/mtcars.csv')
        List<String> lines = Files.readAllLines(path)
        List<String> header = lines.first().split(',').toList()
        List<Map<String,Object>> out = []

        for (int i = 1; i < lines.size(); i++) {
            List<String> parts = lines[i].split(',', -1).toList()
            Map<String,Object> row = [:]
            for (int c = 0; c < header.size(); c++) {
                String key = header[c]
                String value = parts[c]
                row[key] = parseCell(value)
            }
            out.add(row)
        }
        return out
    }

    private static Object parseCell(String value) {
        if (value == null || value.isEmpty()) {
            return null
        }
        if (value ==~ /^-?\d+$/) {
            return Long.parseLong(value)
        }
        if (value ==~ /^-?\d*\.\d+$/) {
            return Double.parseDouble(value)
        }
        return value
    }
}
