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

    @CompileStatic
    static class LaunchResult {
        final int exitCode
        final String output

        LaunchResult(int exitCode, String output) {
            this.exitCode = exitCode
            this.output = output
        }
    }

    private Session session
    private IpcCodec codec

    @Override
    protected void init(Session session) {
        this.session = session
        this.codec = CodecFactory.create(session)
    }

    private IpcCodec getCodec() {
        if (codec == null) {
            codec = CodecFactory.create()
        }
        return codec
    }

    @Function
    Object rFunction(String code = '') {
        return rFunction([:], code)
    }

    @Function
    Object rFunction(Map args, String code = '') {
        validateCall(args, code)

        List<String> excludedKeys = ['function', 'script', '_executable', '_conda_env', '_r_libs', '_on_error', '_payload_kind']
        Map forwardedArgs = args.findAll { k, v -> !(k in excludedKeys) }
        Map<String,Object> launch = resolveLaunch(args)
        String payloadKind = resolvePayloadKind(args)

        String callId = UUID.randomUUID().toString()
        Path scratch = Files.createTempDirectory('nfr-')
        Path requestIpc = scratch.resolve('request.arrows')
        Path responseIpc = scratch.resolve('response.arrows')

        String functionName = args.get('function') == null ? 'main' : String.valueOf(args.get('function'))

        Map<String,Object> control = [
            protocol_version: 1,
            call_id: callId,
            function: functionName,
            script_mode: args.containsKey('script') ? 'path' : 'inline',
            script_ref: (args.get('script') ?: '<inline>'),
            payload_kind: payloadKind,
            code_present: !code.isEmpty(),
            runtime_executable: launch.executable,
            runtime_conda_env: launch.conda_env,
            runtime_r_libs: launch.r_libs
        ]

        IpcCodec selectedCodec = getCodec()
        selectedCodec.writeRequest(requestIpc, control, forwardedArgs)

        LaunchResult run = runRscript(launch, scratch, requestIpc, responseIpc, code)
        if (!Files.exists(responseIpc)) {
            throw new CodecException("R launcher did not produce response IPC (exit=${run.exitCode}): ${responseIpc}\n${tail(run.output)}")
        }

        DecodedResponse decoded = selectedCodec.readResponse(responseIpc)
        Object decodedData = decoded.data
        if ('table' == payloadKind && !isError(decoded.control)) {
            decodedData = normalizeTableData(decodedData)
        }
        String onError = resolveOnError(args)
        if (isError(decoded.control)) {
            if (onError == 'return') {
                return [
                    call_id: callId,
                    codec: selectedCodec.name,
                    runtime: launch,
                    forwarded_args: forwardedArgs,
                    control: decoded.control,
                    decoded_data: decodedData,
                    launcher_output: run.output
                ]
            }
            ensureSuccess(callId, decoded.control, run.output)
        }

        return [
            call_id: callId,
            codec: selectedCodec.name,
            runtime: launch,
            forwarded_args: forwardedArgs,
            control: decoded.control,
            decoded_data: decodedData,
            launcher_output: run.output
        ]
    }

    @Function
    List<Map<String,Object>> rTable(Map args, String code = '') {
        Map<String,Object> call = new LinkedHashMap<>(args)
        call.put('_payload_kind', 'table')
        return rRecords(call, code)
    }

    @Function
    List<Map<String,Object>> rRecords(Map args, String code = '') {
        Object out = rFunction(args, code)
        if (!(out instanceof Map)) {
            throw new CodecException('Invalid rRecords response envelope')
        }

        Object data = ((Map)out).get('decoded_data')
        if (data instanceof List && isRecordList((List)data)) {
            if (isSingleWrapperList((List)data)) {
                Object unwrapped = ((Map)((List)data).get(0)).values().first()
                if (unwrapped instanceof List && isRecordList((List)unwrapped)) {
                    return (List<Map<String,Object>>)unwrapped
                }
                if (unwrapped instanceof Map && isColumnMap((Map)unwrapped)) {
                    return columnMapToRecords((Map<String,Object>)unwrapped)
                }
            }
            return (List<Map<String,Object>>)data
        }
        if (data instanceof Map && isColumnMap((Map)data)) {
            return columnMapToRecords((Map<String,Object>)data)
        }
        if (data instanceof Map && ((Map)data).size() == 1) {
            Object only = ((Map)data).values().first()
            if (only instanceof List && isRecordList((List)only)) {
                return (List<Map<String,Object>>)only
            }
            if (only instanceof Map && isColumnMap((Map)only)) {
                return columnMapToRecords((Map<String,Object>)only)
            }
        }

        throw new CodecException('rRecords expects list-of-records or map-of-columns result')
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

    private String resolvePayloadKind(Map args) {
        String callValue = args?.get('_payload_kind') == null ? null : String.valueOf(args.get('_payload_kind'))
        String cfgValue = session?.config?.navigate('nfR.payload_kind') as String
        String value = (callValue ?: cfgValue ?: 'value_graph').trim().toLowerCase()
        if (!(value in ['value_graph', 'table'])) {
            throw new IllegalArgumentException("Invalid _payload_kind value: ${value}. Expected 'value_graph' or 'table'")
        }
        return value
    }

    private static void validateCall(Map args, String code) {
        if (code && args.containsKey('script')) {
            throw new IllegalArgumentException('Cannot use both code and script options together')
        }
        if (!code && !args.containsKey('script')) {
            throw new IllegalArgumentException('Missing script or code argument')
        }
    }

    private static boolean isError(Map<String,Object> control) {
        String status = control?.get('status') == null ? null : String.valueOf(control.get('status'))
        return status == 'error'
    }

    protected LaunchResult runRscript(Map<String,Object> launch, Path scratch, Path requestIpc, Path responseIpc, String code) {
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
        return new LaunchResult(exit, output)
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

    private static void ensureSuccess(String callId, Map<String,Object> control, String launcherOutput) {
        String status = control?.get('status') == null ? null : String.valueOf(control.get('status'))
        if (status == null || status == 'ok') {
            return
        }
        if (status != 'error') {
            throw new CodecException("Invalid response status for call_id=${callId}: ${status}")
        }

        String errorClass = control.get('error_class') == null ? 'RError' : String.valueOf(control.get('error_class'))
        String errorMessage = control.get('error_message') == null ? 'unknown error' : String.valueOf(control.get('error_message'))
        throw new CodecException("rFunction failed [call_id=${callId}] ${errorClass}: ${errorMessage}\nLauncher output (tail): ${tail(launcherOutput)}")
    }

    private static String tail(String text) {
        if (text == null || text.isEmpty()) {
            return ''
        }
        List<String> lines = text.readLines()
        int n = Math.min(10, lines.size())
        return lines.subList(lines.size() - n, lines.size()).join(' | ')
    }

    private static boolean isRecordList(List rows) {
        for (Object row : rows) {
            if (!(row instanceof Map)) {
                return false
            }
        }
        return true
    }

    private static boolean isSingleWrapperList(List rows) {
        if (rows.size() != 1) {
            return false
        }
        Object row = rows.get(0)
        return row instanceof Map && ((Map)row).size() == 1
    }

    private static boolean isColumnMap(Map map) {
        if (map.isEmpty()) {
            return true
        }
        Integer expected = null
        for (Object value : map.values()) {
            if (!(value instanceof List)) {
                return false
            }
            List col = (List)value
            if (!isScalarColumn(col)) {
                return false
            }
            int n = col.size()
            if (expected == null) {
                expected = n
            } else if (expected != n) {
                return false
            }
        }
        return true
    }

    private static boolean isScalarColumn(List col) {
        for (Object item : col) {
            if (item == null) {
                continue
            }
            if (item instanceof Map || item instanceof List) {
                return false
            }
            if (item.getClass().isArray()) {
                return false
            }
        }
        return true
    }

    private static List<Map<String,Object>> columnMapToRecords(Map<String,Object> cols) {
        if (cols.isEmpty()) {
            return []
        }
        int n = ((List)cols.values().first()).size()
        List<Map<String,Object>> out = new ArrayList<>(n)
        for (int i = 0; i < n; i++) {
            Map<String,Object> row = new LinkedHashMap<>()
            cols.each { String k, Object v ->
                row.put(k, ((List)v).get(i))
            }
            out.add(row)
        }
        return out
    }

    private static Object normalizeTableData(Object data) {
        if (data instanceof List && isRecordList((List)data)) {
            return data
        }
        if (data instanceof Map && isColumnMap((Map)data)) {
            return columnMapToRecords((Map<String,Object>)data)
        }
        if (data instanceof Map && ((Map)data).size() == 1) {
            Object only = ((Map)data).values().first()
            if (only instanceof List && isRecordList((List)only)) {
                return only
            }
            if (only instanceof Map && isColumnMap((Map)only)) {
                return columnMapToRecords((Map<String,Object>)only)
            }
        }
        return data
    }
}
