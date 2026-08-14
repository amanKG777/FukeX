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
        props.setProperty("jcifs.smb.client.connTimeout", "8000")
        props.setProperty("jcifs.smb.client.soTimeout", "20000")
        props.setProperty("jcifs.smb.client.responseTimeout", "15000")
        props.setProperty("jcifs.smb.client.sessionTimeout", "20000")
        BaseContext(PropertyConfiguration(props))
    }

    fun contextFor(username: String?, password: String?): jcifs.CIFSContext {
        if (username.isNullOrEmpty()) return baseContext
        val domain = if (username.contains(";")) username.substringBefore(";") else null
        val user = if (username.contains(";")) username.substringAfter(";") else username
        val auth = jcifs.smb.NtlmPasswordAuthenticator(domain, user, password ?: "")
        return baseContext.withCredentials(auth)
    }
}
