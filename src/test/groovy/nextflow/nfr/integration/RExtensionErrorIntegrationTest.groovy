package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Path
import nextflow.nfr.RExtension
import nextflow.nfr.codec.CodecException
import nextflow.nfr.codec.DecodedResponse
import nextflow.nfr.codec.IpcCodec
import spock.lang.Specification

class RExtensionErrorIntegrationTest extends Specification {

    static class TestRExtension extends RExtension {
        @Override
        protected int runRscript(Map<String, Object> launch, java.nio.file.Path scratch, java.nio.file.Path requestIpc, java.nio.file.Path responseIpc, String code) {
            java.nio.file.Files.copy(requestIpc, responseIpc)
            return 0
        }
    }

    def 'should surface structured error when response status is error'() {
        given:
        def ext = new TestRExtension()
        def fakeCodec = new ErrorCodec()
        setCodec(ext, fakeCodec)

        when:
        ext.rFunction([function: 'boom', x: 1L], '')

        then:
        def e = thrown(CodecException)
        e.message.contains('rFunction failed')
        e.message.contains('RRuntimeError')
        e.message.contains('simulated failure')
    }

    def 'should return structured error payload when _on_error return is set'() {
        given:
        def ext = new TestRExtension()
        def fakeCodec = new ErrorCodec()
        setCodec(ext, fakeCodec)

        when:
        def out = ext.rFunction([function: 'boom', x: 1L, _on_error: 'return'], '')

        then:
        out.control.status == 'error'
        out.control.error_class == 'RRuntimeError'
        out.control.error_message == 'simulated failure'
    }

    private static void setCodec(RExtension extension, IpcCodec codec) {
        def f = RExtension.class.getDeclaredField('codec')
        f.accessible = true
        f.set(extension, codec)
    }

    static class ErrorCodec implements IpcCodec {

        @Override
        String getName() {
            return 'test-error-codec'
        }

        @Override
        void writeRequest(Path ipcPath, Map<String, Object> control, Object data) {
            Files.writeString(ipcPath, "stub")
        }

        @Override
        DecodedResponse readResponse(Path ipcPath) {
            return new DecodedResponse([
                protocol_version: 1,
                call_id: 'err-1',
                status: 'error',
                error_class: 'RRuntimeError',
                error_message: 'simulated failure'
            ], null)
        }
    }
}
