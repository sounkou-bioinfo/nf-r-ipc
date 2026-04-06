package nextflow.nfr.codec

import groovy.transform.CompileStatic

@CompileStatic
class DecodedResponse {
    final Map<String,Object> control
    final Object data

    DecodedResponse(Map<String,Object> control, Object data) {
        this.control = control
        this.data = data
    }
}
