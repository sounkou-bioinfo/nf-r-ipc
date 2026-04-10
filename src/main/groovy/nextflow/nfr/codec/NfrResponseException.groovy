package nextflow.nfr.codec

import groovy.transform.CompileStatic

@CompileStatic
class NfrResponseException extends CodecException {

    NfrResponseException(String message) {
        super(message)
    }

    NfrResponseException(String message, Throwable cause) {
        super(message, cause)
    }
}
