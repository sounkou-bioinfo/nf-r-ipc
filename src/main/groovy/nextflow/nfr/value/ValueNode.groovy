package nextflow.nfr.value

import groovy.transform.CompileStatic

@CompileStatic
class ValueNode {
    final long valueId
    final Long parentId
    final String key
    final Integer index
    final ValueGraphTag tag
    final String vString
    final Long vInt64
    final Double vFloat64
    final Boolean vBool

    ValueNode(
        long valueId,
        Long parentId,
        String key,
        Integer index,
        ValueGraphTag tag,
        String vString,
        Long vInt64,
        Double vFloat64,
        Boolean vBool
    ) {
        this.valueId = valueId
        this.parentId = parentId
        this.key = key
        this.index = index
        this.tag = tag
        this.vString = vString
        this.vInt64 = vInt64
        this.vFloat64 = vFloat64
        this.vBool = vBool
    }
}
