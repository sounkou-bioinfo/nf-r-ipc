package nextflow.nfr

import groovy.transform.CompileStatic
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

@CompileStatic
class NfRPlugin extends BasePlugin {

    NfRPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }
}
