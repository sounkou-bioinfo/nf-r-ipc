package nextflow.nfr.codec

import groovy.transform.CompileStatic
import java.nio.file.Path

@CompileStatic
interface IpcCodec {

    String getName()

    void writeRequest(Path ipcPath, Map<String,Object> control, Object data)

    DecodedResponse readResponse(Path ipcPath)
}
