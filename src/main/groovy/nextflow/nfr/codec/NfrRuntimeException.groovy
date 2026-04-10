package nextflow.nfr.codec

import groovy.transform.CompileStatic

@CompileStatic
class NfrRuntimeException extends CodecException {

    NfrRuntimeException(String message) {
        super(message)
    }

    NfrRuntimeException(String message, Throwable cause) {
        super(message, cause)
    }
}
