package com.boostofstudios.fukex.data
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import java.util.Properties

object SmbConfig {
    val baseContext: BaseContext by lazy {
        val props = Properties()
        props.setProperty("jcifs.smb.client.enableSMB2", "true")
        props.setProperty("jcifs.smb.client.useSMB2Negotiation", "true")
        props.setProperty("jcifs.smb.client.dfs.disabled", "true")
        BaseContext(PropertyConfiguration(props))
    }
}
