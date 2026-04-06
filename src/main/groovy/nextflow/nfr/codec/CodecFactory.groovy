package nextflow.nfr.codec

import groovy.transform.CompileStatic
import nextflow.Session

@CompileStatic
class CodecFactory {

    static IpcCodec create(Session session) {
        String configured = session?.config?.navigate('nfR.codec') as String
        return create(configured)
    }

    static IpcCodec create(String configured) {
        String name = (configured ?: ArrowJavaCodec.CODEC_NAME).trim()
        switch (name) {
            case ArrowJavaCodec.CODEC_NAME:
                return new ArrowJavaCodec()
            case NanoarrowJniCodec.CODEC_NAME:
                return new NanoarrowJniCodec()
            default:
                throw new CodecException("Unsupported nfR codec: ${name}")
        }
    }
}
