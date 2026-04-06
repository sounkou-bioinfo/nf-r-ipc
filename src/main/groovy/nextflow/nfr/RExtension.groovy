package nextflow.nfr

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import nextflow.conda.CondaCache
import nextflow.Session
import nextflow.nfr.codec.CodecFactory
import nextflow.nfr.codec.CodecException
import nextflow.nfr.codec.DecodedResponse
import nextflow.nfr.codec.IpcCodec
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint

@CompileStatic
class RExtension extends PluginExtensionPoint {

    private Session session
    private IpcCodec codec

    @Override
    protected void init(Session session) {
        this.session = session
        this.codec = CodecFactory.create(session)
    }

    private IpcCodec getCodec() {
        if (codec == null) {
            codec = CodecFactory.create((String)null)
        }
        return codec
    }

    @Function
    Object rFunction(String code = '') {
        return rFunction([:], code)
    }

    @Function
    Object rFunction(Map args, String code = '') {
        assert !(code && args.containsKey('script')) : 'Cannot use both code and script options together'

        List<String> excludedKeys = ['function', 'script', '_executable', '_conda_env', '_r_libs', '_on_error']
        Map forwardedArgs = args.findAll { k, v -> !(k in excludedKeys) }
        Map<String,Object> launch = resolveLaunch(args)

        String callId = UUID.randomUUID().toString()
        Path scratch = Files.createTempDirectory('nfr-')
        Path requestIpc = scratch.resolve('request.arrows')
        Path responseIpc = scratch.resolve('response.arrows')

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: callId,
            function: (args.get('function') ?: ''),
            script_mode: args.containsKey('script') ? 'path' : 'inline',
            script_ref: (args.get('script') ?: '<inline>'),
            payload_kind: 'value_graph',
            code_present: !code.isEmpty(),
            runtime_executable: launch.executable,
            runtime_conda_env: launch.conda_env,
            runtime_r_libs: launch.r_libs
        ]

        IpcCodec selectedCodec = getCodec()
        selectedCodec.writeRequest(requestIpc, control, forwardedArgs)

        int exitCode = runRscript(launch, scratch, requestIpc, responseIpc, code)
        if (!Files.exists(responseIpc)) {
            throw new CodecException("R launcher did not produce response IPC (exit=${exitCode}): ${responseIpc}")
        }

        DecodedResponse decoded = selectedCodec.readResponse(responseIpc)
        String onError = resolveOnError(args)
        if (isError(decoded.control)) {
            if (onError == 'return') {
                return [
                    call_id: callId,
                    codec: selectedCodec.name,
                    runtime: launch,
                    forwarded_args: forwardedArgs,
                    control: decoded.control,
                    decoded_data: decoded.data
                ]
            }
            ensureSuccess(callId, decoded.control)
        }

        return [
            call_id: callId,
            codec: selectedCodec.name,
            runtime: launch,
            forwarded_args: forwardedArgs,
            control: decoded.control,
            decoded_data: decoded.data
        ]
    }

    private Map<String,Object> resolveLaunch(Map args) {
        String executableOpt = firstNonBlank(
            args?.get('_executable') as String,
            session?.config?.navigate('nfR.executable') as String,
            null
        )

        String condaEnv = firstNonBlank(
            args?.get('_conda_env') as String,
            session?.config?.navigate('nfR.conda_env') as String,
            null
        )

        if (executableOpt && condaEnv) {
            throw new IllegalArgumentException("The '_executable' and '_conda_env' options cannot be used together")
        }

        String executable = executableOpt ?: 'Rscript'

        String rLibs = firstNonBlank(
            args?.get('_r_libs') as String,
            session?.config?.navigate('nfR.r_libs') as String,
            null
        )

        List<String> command = new ArrayList<>()
        if (condaEnv != null) {
            String resolved = resolveRExecutableFromConda(condaEnv)
            command.add(resolved)
        } else {
            command.add(executable)
        }

        return [
            executable: executable,
            conda_env: condaEnv,
            r_libs: rLibs,
            command: command
        ]
    }

    protected String resolveRExecutableFromConda(String condaEnv) {
        CondaCache cache = new CondaCache(session.getCondaConfig())
        Path condaPath = cache.getCachePathFor(condaEnv)

        String condaExe = firstNonBlank(
            session?.config?.navigate('nfR.conda_executable') as String,
            System.getenv('NFR_CONDA_EXE'),
            'conda'
        )

        Process proc = new ProcessBuilder(condaExe, 'run', '-p', condaPath.toString(), 'which', 'Rscript')
            .redirectErrorStream(true)
            .start()
        String output = proc.inputStream.text.trim()
        int code = proc.waitFor()

        if (code != 0 || !output) {
            throw new IllegalStateException("Failed to find Rscript executable in conda environment: ${condaEnv}\nOutput: ${output}")
        }

        return output
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.trim().isEmpty()) return a.trim()
        if (b != null && !b.trim().isEmpty()) return b.trim()
        return c
    }

    private String resolveOnError(Map args) {
        String callValue = args?.get('_on_error') == null ? null : String.valueOf(args.get('_on_error'))
        String cfgValue = session?.config?.navigate('nfR.on_error') as String
        String value = (callValue ?: cfgValue ?: 'throw').trim().toLowerCase()
        if (!(value in ['throw', 'return'])) {
            throw new IllegalArgumentException("Invalid _on_error value: ${value}. Expected 'throw' or 'return'")
        }
        return value
    }

    private static boolean isError(Map<String,Object> control) {
        String status = control?.get('status') == null ? null : String.valueOf(control.get('status'))
        return status == 'error'
    }

    protected int runRscript(Map<String,Object> launch, Path scratch, Path requestIpc, Path responseIpc, String code) {
        Path launcherScript = materializeLauncherScript(scratch)
        List<String> command = new ArrayList<>((List<String>)launch.command)
        command.add(launcherScript.toString())

        ProcessBuilder pb = new ProcessBuilder(command)
        pb.redirectErrorStream(true)
        Map<String,String> env = pb.environment()
        env.put('NFR_REQUEST_IPC', requestIpc.toString())
        env.put('NFR_RESPONSE_IPC', responseIpc.toString())
        if (code != null && !code.isEmpty()) {
            env.put('NFR_INLINE_CODE', code)
        }
        if (launch.r_libs != null) {
            env.put('R_LIBS', String.valueOf(launch.r_libs))
        }

        Process process = pb.start()
        int exit = process.waitFor()
        String output = new String(process.inputStream.readAllBytes(), StandardCharsets.UTF_8)

        if (exit != 0 && !Files.exists(responseIpc)) {
            throw new CodecException("R launcher failed (exit=${exit})\n${output}")
        }
        return exit
    }

    protected Path materializeLauncherScript(Path scratch) {
        InputStream stream = RExtension.class.getResourceAsStream('/nfr_launcher.R')
        if (stream == null) {
            throw new CodecException('Missing bundled launcher resource: /nfr_launcher.R')
        }

        Path target = scratch.resolve('nfr_launcher.R')
        stream.withCloseable { InputStream in ->
            Files.copy(in, target)
        }
        return target
    }

    private static void ensureSuccess(String callId, Map<String,Object> control) {
        String status = control?.get('status') == null ? null : String.valueOf(control.get('status'))
        if (status == null || status == 'ok') {
            return
        }
        if (status != 'error') {
            throw new CodecException("Invalid response status for call_id=${callId}: ${status}")
        }

        String errorClass = control.get('error_class') == null ? 'RError' : String.valueOf(control.get('error_class'))
        String errorMessage = control.get('error_message') == null ? 'unknown error' : String.valueOf(control.get('error_message'))
        throw new CodecException("rFunction failed [call_id=${callId}] ${errorClass}: ${errorMessage}")
    }
}
