package nextflow.nfr

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import spock.lang.Specification
import nextflow.nfr.codec.CodecException
import nextflow.nfr.value.NAValue

class RExtensionTest extends Specification {

    static class TestRExtension extends RExtension {
        @Override
        protected String resolveRExecutableFromConda(String condaEnv) {
            if (condaEnv.contains('/')) {
                return "${condaEnv}/bin/Rscript"
            }
            return "/opt/conda/envs/${condaEnv}/bin/Rscript"
        }

        @Override
        protected LaunchResult runRscript(Map<String, Object> launch, java.nio.file.Path scratch, java.nio.file.Path requestIpc, java.nio.file.Path responseIpc, String code) {
            java.nio.file.Files.copy(requestIpc, responseIpc)
            return new LaunchResult(0, 'ok')
        }
    }

    def 'should reject script and inline code together'() {
        given:
        def ext = new TestRExtension()

        when:
        ext.rFunction([script: 'x.R'], 'print(1)')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Cannot use both code and script options together')
    }

    def 'should require either script or inline code'() {
        given:
        def ext = new TestRExtension()

        when:
        ext.rFunction([function: 'f'], '')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Missing script or code argument')
    }

    def 'should forward only non-reserved args'() {
        given:
        def ext = new TestRExtension()

        when:
        def result = ext.rFunction([
            script: 'x.R',
            _executable: '/usr/bin/Rscript',
            _r_libs: '/tmp/libs',
            function: 'f',
            x: 1,
            meta: [sample: 'S1']
        ], '')

        then:
        result.forwarded_args == [x: 1, meta: [sample: 'S1']]
        result.codec == 'arrow-java'
        result.runtime.executable == '/usr/bin/Rscript'
        result.runtime.conda_env == null
        result.runtime.r_libs == '/tmp/libs'
        result.runtime.command == ['/usr/bin/Rscript']
    }

    def 'should default function name to main'() {
        given:
        def ext = new TestRExtension()

        when:
        def result = ext.rFunction([x: 1], 'main <- function(x) x')

        then:
        result.control.function == 'main'
    }

    def 'should build conda path command when env looks like path'() {
        given:
        def ext = new TestRExtension()

        when:
        def result = ext.rFunction([
            _conda_env: '/opt/conda/envs/r-4.5',
            function: 'f',
            _on_error: 'return'
        ], 'f <- function() list()')

        then:
        result.runtime.command == ['/opt/conda/envs/r-4.5/bin/Rscript']
        result.control.call_id != null
    }

    def 'should reject executable and conda env together'() {
        given:
        def ext = new TestRExtension()

        when:
        ext.rFunction([
            _executable: 'Rscript',
            _conda_env: 'r-env',
            function: 'f'
        ], 'f <- function() list()')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("cannot be used together")
    }

    def 'should accept table payload kind and normalize to records'() {
        given:
        def ext = new TestRExtension()

        when:
        def result = ext.rFunction([
            script: 'x.R',
            _payload_kind: 'table',
            cols: [sample: ['S1', 'S2'], x: [1, 2]]
        ], '')

        then:
        result.control.payload_kind == 'table'
        result.decoded_data == [[sample: 'S1', x: 1L], [sample: 'S2', x: 2L]]
    }

    def 'should fail on invalid table payload shape'() {
        given:
        def ext = new TestRExtension()

        when:
        ext.rFunction([
            script: 'x.R',
            _payload_kind: 'table',
            bad: [a: [1, 2], b: [1]]
        ], '')

        then:
        def e = thrown(CodecException)
        e.message.contains('Invalid table payload shape')
    }

    def 'should expose missing-value helper predicates'() {
        given:
        def ext = new TestRExtension()

        expect:
        ext.isNULL(null)
        !ext.isNULL(NAValue.DOUBLE)

        ext.isNA(NAValue.LOGICAL)
        ext.isNA(NAValue.INTEGER)
        ext.isNA(NAValue.DOUBLE)
        ext.isNA(NAValue.CHARACTER)
        !ext.isNA(null)
        !ext.isNA(1)

        ext.isNALogical(NAValue.LOGICAL)
        !ext.isNALogical(NAValue.INTEGER)
        ext.isNAInteger(NAValue.INTEGER)
        !ext.isNAInteger(NAValue.DOUBLE)
        ext.isNADouble(NAValue.DOUBLE)
        !ext.isNADouble(NAValue.CHARACTER)
        ext.isNACharacter(NAValue.CHARACTER)
        !ext.isNACharacter(NAValue.LOGICAL)

        ext.naType(NAValue.LOGICAL) == 'logical'
        ext.naType(NAValue.INTEGER) == 'integer'
        ext.naType(NAValue.DOUBLE) == 'double'
        ext.naType(NAValue.CHARACTER) == 'character'
        ext.naType(null) == null
        ext.naType(123) == null

        ext.isMissing(null)
        ext.isMissing(NAValue.DOUBLE)
        !ext.isMissing('x')

        ext.coalesce(null, 'fallback') == 'fallback'
        ext.coalesce(NAValue.INTEGER, 7) == 7
        ext.coalesce('ok', 'fallback') == 'ok'

        ext.coalesceNULL(null, 'null-fallback') == 'null-fallback'
        ext.coalesceNULL(NAValue.DOUBLE, 'null-fallback') == NAValue.DOUBLE
        ext.coalesceNULL('ok', 'null-fallback') == 'ok'

        ext.coalesceNA(NAValue.CHARACTER, 'na-fallback') == 'na-fallback'
        ext.coalesceNA(null, 'na-fallback') == null
        ext.coalesceNA('ok', 'na-fallback') == 'ok'

        ext.renderValue(null) == 'NULL'
        ext.renderValue(NAValue.LOGICAL) == 'NA<logical>'
        ext.renderValue(NAValue.INTEGER) == 'NA<integer>'
        ext.renderValue(NAValue.DOUBLE) == 'NA<double>'
        ext.renderValue(NAValue.CHARACTER) == 'NA<character>'
        ext.renderValue('S1') == 'S1'

        ext.asLocalDate('2024-01-02') == LocalDate.of(2024, 1, 2)
        ext.asInstantUtc('2024-01-02 03:04:05 UTC') == Instant.parse('2024-01-02T03:04:05Z')
        ext.asZonedDateTime('2024-01-02 03:04:05 UTC').zone == ZoneId.of('UTC')
        ext.asZonedDateTime('2024-01-02 03:04:05 UTC', 'Europe/Paris').zone == ZoneId.of('Europe/Paris')
        ext.asDurationSeconds(2.5d) == Duration.ofMillis(2500)
        ext.asDurationSeconds('1.25') == Duration.ofMillis(1250)

        ext.assertNotMissing('ok') == 'ok'
        ext.assertNotMissing(1, 'x') == 1
    }

    def 'assertNotMissing should fail with clear message'() {
        given:
        def ext = new TestRExtension()

        when:
        ext.assertNotMissing(null, 'sample_id')

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message == 'Missing value for sample_id: NULL'

        when:
        ext.assertNotMissing(NAValue.DOUBLE, 'score')

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message == 'Missing value for score: NA<double>'
    }
}
