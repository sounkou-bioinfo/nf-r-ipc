package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Path
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import spock.lang.Specification

class DataFrameMappingIntegrationTest extends Specification {

    def 'should map list-of-records input to data.frame in R and back to records'() {
        given:
        def codec = new ArrowJavaCodec()
        Path request = Files.createTempFile('nfr-df-req', '.arrows')
        Path response = Files.createTempFile('nfr-df-resp', '.arrows')
        Path launcher = Path.of('src/main/resources/nfr_launcher.R')

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'df-map-1',
            function: 'mutate_df',
            script_mode: 'inline',
            script_ref: '<inline>',
            payload_kind: 'value_graph'
        ]

        List<Map<String,Object>> rows = [
            [sample: 'S1', x: 1L, y: 2d],
            [sample: 'S2', x: 3L, y: 4d]
        ]
        codec.writeRequest(request, control, [tbl: rows])

        String inlineCode = '''
mutate_df <- function(tbl) {
  stopifnot(is.data.frame(tbl))
  tbl$score <- tbl$x + tbl$y
  tbl
}
'''.trim()

        when:
        ProcessBuilder pb = new ProcessBuilder(['Rscript', launcher.toString()])
        pb.redirectErrorStream(true)
        pb.environment().put('NFR_REQUEST_IPC', request.toString())
        pb.environment().put('NFR_RESPONSE_IPC', response.toString())
        pb.environment().put('NFR_INLINE_CODE', inlineCode)
        Process proc = pb.start()
        proc.waitFor()
        DecodedResponse decoded = codec.readResponse(response)
        List<Map> out = (List<Map>)decoded.data

        then:
        proc.exitValue() == 0
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'df-map-1', 'value_graph')
        out.size() == 2
        out[0].sample == 'S1'
        ((Number)out[0].score).doubleValue() == 3d
        ((Number)out[1].score).doubleValue() == 7d
    }
}
