package nextflow.nfr.codec.jni;

import java.util.Map;

public final class NanoarrowJniNative {

    private static volatile boolean loaded;

    private NanoarrowJniNative() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        String lib = System.getProperty("nfr.nanoarrow.lib");
        if (lib != null && !lib.trim().isEmpty()) {
            System.load(lib);
        } else {
            System.loadLibrary("nfr_nanoarrow_jni");
        }
        loaded = true;
    }

    public static native void writeRequest(String ipcPath, Map<String, Object> control, Object data);

    public static native Map<String, Object> readResponse(String ipcPath);
}
