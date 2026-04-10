package nextflow.nfr.codec

import groovy.transform.CompileStatic

@CompileStatic
class NfrParseException extends CodecException {

    NfrParseException(String message) {
        super(message)
    }

    NfrParseException(String message, Throwable cause) {
        super(message, cause)
    }
}
