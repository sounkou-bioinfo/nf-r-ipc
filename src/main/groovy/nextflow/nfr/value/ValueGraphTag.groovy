package nextflow.nfr.value

import groovy.transform.CompileStatic

@CompileStatic
enum ValueGraphTag {
    NULL('null'),
    NA_LOGICAL('na_logical'),
    NA_INTEGER('na_integer'),
    NA_DOUBLE('na_double'),
    NA_CHARACTER('na_character'),
    DATA_FRAME('data_frame'),
    LIST('list'),
    MAP('map'),
    STRING('string'),
    INT64('int64'),
    FLOAT64('float64'),
    BOOL('bool')

    final String wireName

    ValueGraphTag(String wireName) {
        this.wireName = wireName
    }

    static ValueGraphTag fromWireName(String wireName) {
        for (ValueGraphTag tag : values()) {
            if (tag.wireName == wireName) {
                return tag
            }
        }
        throw new IllegalArgumentException("Unknown value graph tag: ${wireName}")
    }
}
