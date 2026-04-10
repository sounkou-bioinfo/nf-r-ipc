package nextflow.nfr.integration

import java.nio.file.Files
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import nextflow.nfr.codec.CodecException
import nextflow.nfr.codec.NfrResponseException
import nextflow.nfr.RExtension
import nextflow.nfr.codec.IpcCodec
import java.nio.file.Path
import spock.lang.Specification

class RLevelErrorEnvelopeIntegrationTest extends Specification {

    static class TestRExtension extends RExtension {
        @Override
        protected LaunchResult runRscript(Map<String, Object> launch, Path scratch, Path requestIpc, Path responseIpc, String code) {
            java.nio.file.Files.copy(requestIpc, responseIpc)
            return new LaunchResult(0, 'launcher-error-envelope-output')
        }
    }

    def 'should preserve R-level error envelope fields'() {
        given:
        def codec = new ArrowJavaCodec()
        def path = Files.createTempFile('nfr-r-error', '.arrows')

        def control = [
            protocol_version: 1,
            call_id: 'call-r-error-1',
            status: 'error',
            error_class: 'RRuntimeError',
            error_message: 'object x not found'
        ]

        when:
        codec.writeRequest(path, control, null)
        DecodedResponse decoded = codec.readResponse(path)

        then:
        IntegrationAssertions.assertErrorEnvelope(decoded.control, 'call-r-error-1', 'RRuntimeError', 'object x not found')
        decoded.data == null
    }

    def 'should raise codec exception from structured R-level error control'() {
        given:
        def ext = new TestRExtension()
        def codec = new EchoErrorEnvelopeCodec()
        setCodec(ext, codec)

        when:
        ext.rFunction([function: 'brokenFn', x: 1], 'brokenFn <- function(x) x')

        then:
        def e = thrown(NfrResponseException)
        e.message.contains('rFunction failed')
        e.message.contains('RRuntimeError')
        e.message.contains('object x not found')
    }

    private static void setCodec(RExtension extension, IpcCodec codec) {
        def f = RExtension.class.getDeclaredField('codec')
        f.accessible = true
        f.set(extension, codec)
    }

    static class EchoErrorEnvelopeCodec implements IpcCodec {
        private final ArrowJavaCodec delegate = new ArrowJavaCodec()

        @Override
        String getName() {
            return 'arrow-java-error-envelope'
        }

        @Override
        void writeRequest(java.nio.file.Path ipcPath, Map<String, Object> control, Object data) {
            Map<String,Object> errorControl = [
                protocol_version: 1,
                call_id: String.valueOf(control.call_id),
                status: 'error',
                error_class: 'RRuntimeError',
                error_message: 'object x not found'
            ]
            delegate.writeRequest(ipcPath, errorControl, null)
        }

        @Override
        DecodedResponse readResponse(java.nio.file.Path ipcPath) {
            delegate.readResponse(ipcPath)
        }
    }
}
