package nextflow.nfr.codec

import groovy.transform.CompileStatic
import java.nio.file.Path
import nextflow.nfr.codec.jni.NanoarrowJniNative

@CompileStatic
class NanoarrowJniCodec implements IpcCodec {

    static final String CODEC_NAME = 'nanoarrow-jni'

    @Override
    String getName() {
        return CODEC_NAME
    }

    @Override
    void writeRequest(Path ipcPath, Map<String,Object> control, Object data) {
        ensureNativeLoaded()
        NanoarrowJniNative.writeRequest(ipcPath.toString(), control, data)
    }

    @Override
    DecodedResponse readResponse(Path ipcPath) {
        ensureNativeLoaded()
        Map<String,Object> payload = NanoarrowJniNative.readResponse(ipcPath.toString())
        if (payload == null) {
            throw new CodecException('Nanoarrow JNI codec returned null response payload')
        }
        Map<String,Object> control = (Map<String,Object>)(payload.get('control') ?: [:])
        Object data = payload.get('data')
        return new DecodedResponse(control, data)
    }

    private static void ensureNativeLoaded() {
        try {
            NanoarrowJniNative.load()
        } catch (UnsatisfiedLinkError e) {
            throw new CodecException('Failed to load nanoarrow JNI library', e)
        }
    }
}
