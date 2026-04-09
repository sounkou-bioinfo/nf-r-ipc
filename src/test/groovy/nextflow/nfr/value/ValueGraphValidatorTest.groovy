package nextflow.nfr.value

import spock.lang.Specification

class ValueGraphValidatorTest extends Specification {

    def 'should reject duplicate value ids'() {
        when:
        ValueGraphValidator.validate([
            new ValueNode(1L, null, null, null, ValueGraphTag.MAP, null, null, null, null),
            new ValueNode(1L, 1L, 'x', null, ValueGraphTag.INT64, null, 1L, null, null)
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Duplicate value_id')
    }

    def 'should reject missing parent node'() {
        when:
        ValueGraphValidator.validate([
            new ValueNode(1L, null, null, null, ValueGraphTag.MAP, null, null, null, null),
            new ValueNode(2L, 999L, 'x', null, ValueGraphTag.INT64, null, 1L, null, null)
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('missing parent')
    }

    def 'should reject map child missing key'() {
        when:
        ValueGraphValidator.validate([
            new ValueNode(1L, null, null, null, ValueGraphTag.MAP, null, null, null, null),
            new ValueNode(2L, 1L, null, null, ValueGraphTag.INT64, null, 1L, null, null)
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('missing key')
    }

    def 'should reject list child missing index'() {
        when:
        ValueGraphValidator.validate([
            new ValueNode(1L, null, null, null, ValueGraphTag.LIST, null, null, null, null),
            new ValueNode(2L, 1L, null, null, ValueGraphTag.INT64, null, 1L, null, null)
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('missing index')
    }

    def 'should reject scalar node with children'() {
        when:
        ValueGraphValidator.validate([
            new ValueNode(1L, null, null, null, ValueGraphTag.INT64, null, 1L, null, null),
            new ValueNode(2L, 1L, null, 0, ValueGraphTag.INT64, null, 2L, null, null)
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('cannot have children')
    }

    def 'should reject invalid scalar value columns'() {
        when:
        ValueGraphValidator.validate([
            new ValueNode(1L, null, null, null, ValueGraphTag.STRING, null, null, null, null)
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid value columns for string')
    }
}
