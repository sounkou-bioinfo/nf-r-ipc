package nextflow.nfr.value

import spock.lang.Specification

class ValueGraphCodecTest extends Specification {

    def 'should roundtrip nested map and list values'() {
        given:
        def input = [
            x: 1L,
            y: 3.14d,
            ok: true,
            meta: [sample: 'S1', attrs: [null, false, [k: 'v']]],
            items: [[a: 1L], [a: 2L]]
        ]

        when:
        def nodes = ValueGraphCodec.encode(input)
        def output = ValueGraphCodec.decode(nodes)

        then:
        output == input
    }

    def 'should preserve typed NA markers distinctly from null'() {
        given:
        def input = [
            nullValue: null,
            naLogical: NAValue.LOGICAL,
            naInteger: NAValue.INTEGER,
            naDouble: NAValue.DOUBLE,
            naCharacter: NAValue.CHARACTER,
            nested: [NAValue.DOUBLE, null, 'x']
        ]

        when:
        def nodes = ValueGraphCodec.encode(input)
        def output = ValueGraphCodec.decode(nodes)

        then:
        output == input
        output.nullValue == null
        output.naDouble == NAValue.DOUBLE
    }

    def 'should encode data-frame-like map as data_frame tag and roundtrip'() {
        given:
        def input = [
            sample: ['S1', 'S2'],
            x: [1L, 3L],
            y: [2d, 4d]
        ]

        when:
        def nodes = ValueGraphCodec.encode(input)
        def output = ValueGraphCodec.decode(nodes)

        then:
        nodes.any { it.tag == ValueGraphTag.DATA_FRAME }
        output == input
    }
}
