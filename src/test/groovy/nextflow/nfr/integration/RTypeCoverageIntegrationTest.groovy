package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Path
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import spock.lang.Specification

class RTypeCoverageIntegrationTest extends Specification {

    def 'should normalize factor and date-time classes in launcher output'() {
        given:
        def codec = new ArrowJavaCodec()
        Path request = Files.createTempFile('nfr-typecov-req', '.arrows')
        Path response = Files.createTempFile('nfr-typecov-resp', '.arrows')
        Path launcher = Path.of('src/main/resources/nfr_launcher.R')

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'r-typecov-1',
            function: 'emit_types',
            script_mode: 'inline',
            script_ref: '<inline>',
            payload_kind: 'value_graph'
        ]
        codec.writeRequest(request, control, [:])

        String inlineCode = '''
emit_types <- function() {
  list(
    fac = factor('A'),
    ordered_fac = ordered('hi', levels = c('lo','hi')),
    date = as.Date('2024-01-02'),
    ts = as.POSIXct('2024-01-02 03:04:05', tz = 'UTC'),
    dt = as.difftime(90, units = 'secs'),
    deep = list(level1 = list(level2 = list(level3 = list(flag = TRUE, when = as.POSIXct('2024-01-05 06:07:08', tz = 'UTC'))))),
    frame = data.frame(
      grp = factor(c('x', 'y')),
      d = as.Date(c('2024-01-02', '2024-01-03')),
      t = as.POSIXct(c('2024-01-02 03:04:05', '2024-01-03 04:05:06'), tz = 'UTC'),
      stringsAsFactors = FALSE
    )
  )
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
        Map out = (Map)decoded.data
        List<Map> frame = (List<Map>)out.frame

        then:
        proc.exitValue() == 0
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'r-typecov-1', 'value_graph')

        out.fac == 'A'
        out.ordered_fac == 'hi'
        out.date == '2024-01-02'
        out.ts.toString().contains('2024-01-02')
        out.ts.toString().contains('UTC')
        ((Number)out.dt).doubleValue() == 90d

        Map deep = (Map)out.deep
        ((Map)((Map)((Map)deep.level1).level2).level3).flag == true
        ((Map)((Map)((Map)deep.level1).level2).level3).when.toString().contains('UTC')

        frame.size() == 2
        frame[0].grp == 'x'
        frame[1].grp == 'y'
        frame[0].d == '2024-01-02'
        frame[1].d == '2024-01-03'
        frame[0].t.toString().contains('2024-01-02')
        frame[1].t.toString().contains('2024-01-03')
        frame[0].t.toString().contains('UTC')
    }
}
