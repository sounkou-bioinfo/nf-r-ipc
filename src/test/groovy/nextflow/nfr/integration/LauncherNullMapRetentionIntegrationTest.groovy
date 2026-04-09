package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Path
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import nextflow.nfr.value.NAValue
import spock.lang.Specification

class LauncherNullMapRetentionIntegrationTest extends Specification {

    def 'should retain named NULL entries from R maps'() {
        given:
        def codec = new ArrowJavaCodec()
        Path request = Files.createTempFile('nfr-launcher-null-map-req', '.arrows')
        Path response = Files.createTempFile('nfr-launcher-null-map-resp', '.arrows')
        Path launcher = Path.of('src/main/resources/nfr_launcher.R')
        assert Files.exists(launcher)

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'launcher-null-map-ok',
            function: 'emit_missing',
            script_mode: 'inline',
            script_ref: '<inline>',
            payload_kind: 'value_graph'
        ]
        codec.writeRequest(request, control, [:])

        String inlineCode = '''
            emit_missing <- function() {
              list(
                null_value = NULL,
                na_logical = NA,
                na_integer = NA_integer_,
                na_double = NA_real_,
                na_character = NA_character_,
                nested = list(NA_real_, NULL, NA_character_)
              )
            }
        '''

        when:
        int rc = runProcess(['Rscript', launcher.toString()], request, response, inlineCode)
        DecodedResponse decoded = codec.readResponse(response)
        Map out = (Map)decoded.data

        then:
        rc == 0
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'launcher-null-map-ok', 'value_graph')
        IntegrationAssertions.assertNullVsNaDistinct(out, 'null_value', 'na_logical', NAValue.LOGICAL)
        out.na_integer == NAValue.INTEGER
        out.na_double == NAValue.DOUBLE
        out.na_character == NAValue.CHARACTER
        ((List)out.nested)[0] == NAValue.DOUBLE
        ((List)out.nested)[1] == null
        ((List)out.nested)[2] == NAValue.CHARACTER
    }

    private static int runProcess(List<String> command, Path request, Path response, String inlineCode) {
        ProcessBuilder pb = new ProcessBuilder(command)
        pb.redirectErrorStream(true)
        pb.environment().put('NFR_REQUEST_IPC', request.toString())
        pb.environment().put('NFR_RESPONSE_IPC', response.toString())
        if (inlineCode != null) {
            pb.environment().put('NFR_INLINE_CODE', inlineCode)
        }
        Process proc = pb.start()
        proc.waitFor()
        return proc.exitValue()
    }
}
