package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Path
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import spock.lang.Specification

class LauncherScriptModeIntegrationTest extends Specification {

    def 'should execute external R script via launcher'() {
        given:
        def codec = new ArrowJavaCodec()
        Path request = Files.createTempFile('nfr-launcher-req', '.arrows')
        Path response = Files.createTempFile('nfr-launcher-resp', '.arrows')
        Path launcher = Path.of('src/main/resources/nfr_launcher.R')
        assert Files.exists(launcher)

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'launcher-script-ok',
            function: 'echo_external',
            script_mode: 'path',
            script_ref: 'validation/scripts/echo_external.R',
            payload_kind: 'value_graph'
        ]
        Map<String,Object> args = [
            sample: 'S1',
            values: [1, 2, 3],
            meta: [batch: 'B1']
        ]
        codec.writeRequest(request, control, args)

        when:
        int rc = runProcess(['Rscript', launcher.toString()], request, response, null)
        DecodedResponse decoded = codec.readResponse(response)

        then:
        rc == 0
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'launcher-script-ok', 'value_graph')
        ((Map)decoded.data).sample == 'S1-external'
    }

    def 'should return structured error envelope for failing external script function'() {
        given:
        def codec = new ArrowJavaCodec()
        Path request = Files.createTempFile('nfr-launcher-err-req', '.arrows')
        Path response = Files.createTempFile('nfr-launcher-err-resp', '.arrows')
        Path launcher = Path.of('src/main/resources/nfr_launcher.R')

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'launcher-script-err',
            function: 'explode_external',
            script_mode: 'path',
            script_ref: 'validation/scripts/echo_external.R',
            payload_kind: 'value_graph'
        ]
        codec.writeRequest(request, control, [trigger: 'x'])

        when:
        int rc = runProcess(['Rscript', launcher.toString()], request, response, null)
        DecodedResponse decoded = codec.readResponse(response)

        then:
        rc == 0
        IntegrationAssertions.assertErrorEnvelope(decoded.control, 'launcher-script-err', 'RRuntimeError', 'boom from external R script')
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
