package nextflow.nfr

import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.CodecFactory
import spock.lang.Specification

class CodecFactoryTest extends Specification {

    def 'should use arrow-java by default'() {
        when:
        def codec = CodecFactory.create()

        then:
        codec instanceof ArrowJavaCodec
    }
}
