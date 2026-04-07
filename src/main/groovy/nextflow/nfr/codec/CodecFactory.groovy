package nextflow.nfr.codec

import groovy.transform.CompileStatic
import nextflow.Session

@CompileStatic
class CodecFactory {

    static IpcCodec create(Session session) {
        return create()
    }

    static IpcCodec create() {
        return new ArrowJavaCodec()
    }
}
