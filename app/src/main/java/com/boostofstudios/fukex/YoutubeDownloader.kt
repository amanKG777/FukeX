package com.boostofstudios.fukex

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class YoutubeDownloader private constructor() : Downloader() {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private var instance: YoutubeDownloader? = null
        fun getInstance(): YoutubeDownloader {
            if (instance == null) {
                instance = YoutubeDownloader()
            }
            return instance!!
        }
    }

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder()
            .url(url)
            .method(
                httpMethod,
                if (httpMethod != "GET" && dataToSend != null) dataToSend.toRequestBody(null)
                else if (httpMethod != "GET" && httpMethod != "HEAD") "".toRequestBody(null) else null
            )

        headers?.forEach { (key, list) ->
            list.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        val okHttpRequest = builder.build()
        val response = client.newCall(okHttpRequest).execute()

        val responseBody = response.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()
        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers.values(name)
        }

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            request.url()
        )
    }
}
