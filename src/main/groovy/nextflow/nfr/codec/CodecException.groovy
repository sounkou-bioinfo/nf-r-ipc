package nextflow.nfr.codec

import groovy.transform.CompileStatic

@CompileStatic
class CodecException extends RuntimeException {

    CodecException(String message) {
        super(message)
    }

    CodecException(String message, Throwable cause) {
        super(message, cause)
    }
}
