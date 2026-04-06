package nextflow.nfr.codec

import java.nio.file.Files
import spock.lang.Specification

class ArrowJavaCodecTest extends Specification {

    def 'should roundtrip control and nested value graph'() {
        given:
        def codec = new ArrowJavaCodec()
        def temp = Files.createTempFile('nfr-arrow-java', '.arrows')
        def control = [protocol_version: 1, call_id: 'abc', status: 'ok']
        def data = [x: 1L, meta: [sample: 'S1', values: [1L, 2L, null]], flag: true]

        when:
        codec.writeRequest(temp, control, data)
        def decoded = codec.readResponse(temp)

        then:
        decoded.control == control
        decoded.data == data
    }
}
