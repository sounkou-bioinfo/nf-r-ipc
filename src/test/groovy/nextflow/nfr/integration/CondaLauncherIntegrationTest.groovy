package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Path
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import spock.lang.Specification

class CondaLauncherIntegrationTest extends Specification {

    def 'should execute launcher using conda-resolved Rscript when enabled'() {
        given:
        String enabled = System.getenv('NFR_TEST_CONDA')
        if (!('1'.equals(enabled) || 'true'.equalsIgnoreCase(enabled))) {
            return
        }

        String condaExe = System.getenv('NFR_TEST_CONDA_EXE') ?: '/root/miniconda3/bin/conda'
        if (!Files.exists(Path.of(condaExe))) {
            return
        }

        String condaPrefix = System.getenv('NFR_TEST_CONDA_PREFIX') ?: '/root/miniconda3'
        Path launcher = Path.of('src/main/resources/nfr_launcher.R')
        Path request = Files.createTempFile('nfr-conda-req', '.arrows')
        Path response = Files.createTempFile('nfr-conda-resp', '.arrows')

        def codec = new ArrowJavaCodec()
        codec.writeRequest(request, [
            protocol_version: 1,
            call_id: 'conda-launcher-ok',
            function: 'echo',
            script_mode: 'inline',
            script_ref: '<inline>',
            payload_kind: 'value_graph'
        ], [sample: 'S1'])

        String inlineCode = "echo <- function(sample) { list(sample = paste0(sample, '-conda')) }"

        when:
        ProcessBuilder pb = new ProcessBuilder([
            condaExe,
            'run',
            '-p',
            condaPrefix,
            'Rscript',
            launcher.toString()
        ])
        pb.redirectErrorStream(true)
        pb.environment().put('NFR_REQUEST_IPC', request.toString())
        pb.environment().put('NFR_RESPONSE_IPC', response.toString())
        pb.environment().put('NFR_INLINE_CODE', inlineCode)
        Process proc = pb.start()
        proc.waitFor()
        DecodedResponse decoded = codec.readResponse(response)

        then:
        proc.exitValue() == 0
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'conda-launcher-ok', 'value_graph')
        ((Map)decoded.data).sample == 'S1-conda'
    }
}
