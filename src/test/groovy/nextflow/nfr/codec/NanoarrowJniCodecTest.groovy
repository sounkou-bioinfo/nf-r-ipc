package nextflow.nfr.codec

import java.nio.file.Files
import spock.lang.Specification

class NanoarrowJniCodecTest extends Specification {

    def 'should fail cleanly when JNI library is missing'() {
        given:
        def codec = new NanoarrowJniCodec()
        def temp = Files.createTempFile('nfr-nanoarrow-jni', '.arrows')

        when:
        codec.readResponse(temp)

        then:
        def e = thrown(CodecException)
        e.message.contains('Failed to load nanoarrow JNI library')
    }
}
