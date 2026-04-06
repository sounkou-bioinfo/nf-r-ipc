package nextflow.nfr.integration

import groovy.transform.CompileStatic
import nextflow.nfr.value.NAValue

@CompileStatic
class IntegrationAssertions {

    static void assertOkEnvelope(Map<String,Object> control, String callId, String resultKind = null) {
        assert control != null
        assert control.get('status') == 'ok'
        if (callId != null) {
            assert control.get('call_id') == callId
        }
        if (resultKind != null) {
            assert control.get('result_kind') == resultKind
        }
    }

    static void assertErrorEnvelope(Map<String,Object> control, String callId, String errorClass, String errorMessage) {
        assert control != null
        assert control.get('status') == 'error'
        if (callId != null) {
            assert control.get('call_id') == callId
        }
        assert control.get('error_class') == errorClass
        assert control.get('error_message') == errorMessage
    }

    static void assertNullVsNaDistinct(Map<String,Object> map, String nullKey, String naKey, Object expectedNaOrMarker) {
        assert map.containsKey(nullKey)
        assert map.get(nullKey) == null
        assert map.containsKey(naKey)
        assert map.get(naKey) == expectedNaOrMarker
    }

    static void assertFiniteOrSpecialDouble(Object value, boolean isNaN, Integer signForInfinity) {
        assert value instanceof Number
        double d = ((Number)value).doubleValue()
        if (isNaN) {
            assert Double.isNaN(d)
            return
        }
        assert Double.isInfinite(d)
        if (signForInfinity != null) {
            assert Math.signum(d) == Math.signum((double)signForInfinity)
        }
    }

    static void assertMtcarsRow(Map<String,Object> row, String rowname, double mpg, long cyl, long hp) {
        assert row != null
        assert row.get('rownames') == rowname
        assert row.get('mpg') instanceof Number
        assert ((Number)row.get('mpg')).doubleValue() == mpg
        assert row.get('cyl') instanceof Number
        assert ((Number)row.get('cyl')).longValue() == cyl
        assert row.get('hp') instanceof Number
        assert ((Number)row.get('hp')).longValue() == hp
    }

    static void assertRecordListShape(List<Map<String,Object>> rows, int expectedSize, List<String> requiredKeys) {
        assert rows != null
        assert rows.size() == expectedSize
        if (expectedSize == 0) {
            return
        }
        Map<String,Object> first = rows.first()
        assert first != null
        for (String key : requiredKeys) {
            assert first.containsKey(key)
        }
    }
}
