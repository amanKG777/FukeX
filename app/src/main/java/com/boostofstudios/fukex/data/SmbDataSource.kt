package com.boostofstudios.fukex.data
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.EOFException
import java.io.IOException

class SmbDataSource : BaseDataSource(true) {
    private var file: SmbFile? = null
    private var randomAccessFile: SmbRandomAccessFile? = null
    private var inputStream: java.io.InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val smbUriStr = uri.toString()
            val parsedUri = android.net.Uri.parse(smbUriStr)
            val userInfo = parsedUri.userInfo
            val context = if (userInfo != null) {
                val parts = userInfo.split(":", limit = 2)
                val decodedUser = java.net.URLDecoder.decode(parts[0], "UTF-8")
                val decodedPass = if (parts.size > 1) java.net.URLDecoder.decode(parts[1], "UTF-8") else ""
                val domain = if (decodedUser.contains(";")) decodedUser.substringBefore(";") else null
                val actualUser = if (decodedUser.contains(";")) decodedUser.substringAfter(";") else decodedUser
                val auth = jcifs.smb.NtlmPasswordAuthenticator(domain, actualUser, decodedPass)
                SmbConfig.baseContext.withCredentials(auth)
            } else {
                SmbConfig.baseContext
            }
            file = SmbFile(smbUriStr, context)
            val isSmbFile = file
            if (isSmbFile == null) throw IOException("Failed to create SmbFile")
            randomAccessFile = SmbRandomAccessFile(isSmbFile, "r")
            randomAccessFile!!.seek(dataSpec.position)
            inputStream = java.io.BufferedInputStream(object : java.io.InputStream() {
                override fun read(): Int {
                    val b = randomAccessFile!!.read()
                    return if (b == -1) -1 else b and 0xFF
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    return randomAccessFile!!.read(b, off, len)
                }
            }, 1024 * 256) // 256 KB buffer
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                val contentLength = randomAccessFile!!.length()
                if (contentLength == 0L) {
                    C.LENGTH_UNSET.toLong()
                } else {
                    contentLength - dataSpec.position
                }
            }
        } catch (e: Exception) {
            throw IOException(e)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            readLength
        } else {
            Math.min(bytesRemaining, readLength.toLong()).toInt()
        }
        val bytesRead: Int
        try {
            bytesRead = inputStream!!.read(buffer, offset, bytesToRead)
        } catch (e: IOException) {
            throw e
        }
        if (bytesRead == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                throw EOFException()
            }
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? {
        return uri
    }

    override fun close() {
        uri = null
        try {
            inputStream?.close()
            randomAccessFile?.close()
        } catch (e: IOException) {
            throw e
        } finally {
            inputStream = null
            randomAccessFile = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
