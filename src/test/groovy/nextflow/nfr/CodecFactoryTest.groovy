package nextflow.nfr

import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.CodecException
import nextflow.nfr.codec.CodecFactory
import nextflow.nfr.codec.NanoarrowJniCodec
import spock.lang.Specification

class CodecFactoryTest extends Specification {

    def 'should use arrow-java by default'() {
        when:
        def codec = CodecFactory.create((String)null)

        then:
        codec instanceof ArrowJavaCodec
    }

    def 'should select nanoarrow-jni when configured'() {
        when:
        def codec = CodecFactory.create('nanoarrow-jni')

        then:
        codec instanceof NanoarrowJniCodec
    }

    def 'should fail on unknown codec'() {
        when:
        CodecFactory.create('unknown-codec')

        then:
        def e = thrown(CodecException)
        e.message.contains('Unsupported nfR codec')
    }
}
