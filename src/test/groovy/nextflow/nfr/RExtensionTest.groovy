package nextflow.nfr

import spock.lang.Specification

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
        protected int runRscript(Map<String, Object> launch, java.nio.file.Path scratch, java.nio.file.Path requestIpc, java.nio.file.Path responseIpc, String code) {
            java.nio.file.Files.copy(requestIpc, responseIpc)
            return 0
        }
    }

    def 'should reject script and inline code together'() {
        given:
        def ext = new TestRExtension()

        when:
        ext.rFunction([script: 'x.R'], 'print(1)')

        then:
        AssertionError e = thrown()
        e.message.contains('Cannot use both code and script options together')
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

    def 'should build conda path command when env looks like path'() {
        given:
        def ext = new TestRExtension()

        when:
        def result = ext.rFunction([
            _conda_env: '/opt/conda/envs/r-4.5',
            function: 'f',
            _on_error: 'return'
        ], '')

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
        ], '')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("cannot be used together")
    }
}
