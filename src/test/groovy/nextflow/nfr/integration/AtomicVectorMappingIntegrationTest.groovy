package nextflow.nfr.integration

import java.nio.file.Files
import java.nio.file.Path
import nextflow.nfr.codec.ArrowJavaCodec
import nextflow.nfr.codec.DecodedResponse
import spock.lang.Specification

class AtomicVectorMappingIntegrationTest extends Specification {

    def 'should preserve groovy primitive arrays as list values through arrow codec'() {
        given:
        def codec = new ArrowJavaCodec()
        Path path = Files.createTempFile('nfr-atomic-groovy', '.arrows')

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'atomic-groovy-1',
            status: 'ok',
            result_kind: 'value_graph'
        ]
        Map<String,Object> payload = [
            ints: ([1, 2, 3] as int[]),
            doubles: ([1.5d, 2.5d] as double[]),
            bools: ([true, false] as boolean[]),
            strings: (['a', 'b'] as String[])
        ]

        when:
        codec.writeRequest(path, control, payload)
        DecodedResponse decoded = codec.readResponse(path)
        Map out = (Map)decoded.data

        then:
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'atomic-groovy-1', 'value_graph')
        out.ints == [1L, 2L, 3L]
        out.doubles == [1.5d, 2.5d]
        out.bools == [true, false]
        out.strings == ['a', 'b']
    }

    def 'should map R atomic vectors to list-like values via launcher'() {
        given:
        def codec = new ArrowJavaCodec()
        Path request = Files.createTempFile('nfr-atomic-r-req', '.arrows')
        Path response = Files.createTempFile('nfr-atomic-r-resp', '.arrows')
        Path launcher = Path.of('src/main/resources/nfr_launcher.R')

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'atomic-r-1',
            function: 'make_vectors',
            script_mode: 'inline',
            script_ref: '<inline>',
            payload_kind: 'value_graph'
        ]
        codec.writeRequest(request, control, [
            ints: [1L, 2L, 3L],
            doubles: [1.25d, 2.5d],
            bools: [true, false],
            strings: ['x', 'y']
        ])

        String inlineCode = """
make_vectors <- function(ints, doubles, bools, strings) {
  list(
    ints = ints,
    doubles = doubles,
    bools = bools,
    strings = strings
  )
}
""".trim()

        when:
        ProcessBuilder pb = new ProcessBuilder(['Rscript', launcher.toString()])
        pb.redirectErrorStream(true)
        pb.environment().put('NFR_REQUEST_IPC', request.toString())
        pb.environment().put('NFR_RESPONSE_IPC', response.toString())
        pb.environment().put('NFR_INLINE_CODE', inlineCode)
        Process proc = pb.start()
        proc.waitFor()
        DecodedResponse decoded = codec.readResponse(response)
        Map out = (Map)decoded.data

        then:
        proc.exitValue() == 0
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'atomic-r-1', 'value_graph')
        out.ints == [1d, 2d, 3d]
        out.doubles == [1.25d, 2.5d]
        out.bools == [true, false]
        out.strings == ['x', 'y']
    }

    def 'should map int64 values to R double semantics in launcher response'() {
        given:
        def codec = new ArrowJavaCodec()
        Path request = Files.createTempFile('nfr-r-int64-req', '.arrows')
        Path response = Files.createTempFile('nfr-r-int64-resp', '.arrows')
        Path launcher = Path.of('src/main/resources/nfr_launcher.R')

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: 'atomic-r-int64-1',
            function: 'echo_num',
            script_mode: 'inline',
            script_ref: '<inline>',
            payload_kind: 'value_graph'
        ]
        codec.writeRequest(request, control, [x: 1L, y: [2L, 3L]])

        String inlineCode = """
echo_num <- function(x, y) {
  list(x = x, y = y)
}
""".trim()

        when:
        ProcessBuilder pb = new ProcessBuilder(['Rscript', launcher.toString()])
        pb.redirectErrorStream(true)
        pb.environment().put('NFR_REQUEST_IPC', request.toString())
        pb.environment().put('NFR_RESPONSE_IPC', response.toString())
        pb.environment().put('NFR_INLINE_CODE', inlineCode)
        Process proc = pb.start()
        proc.waitFor()
        DecodedResponse decoded = codec.readResponse(response)
        Map out = (Map)decoded.data

        then:
        proc.exitValue() == 0
        IntegrationAssertions.assertOkEnvelope(decoded.control, 'atomic-r-int64-1', 'value_graph')
        out.x == 1d
        out.y == [2d, 3d]
    }
}
