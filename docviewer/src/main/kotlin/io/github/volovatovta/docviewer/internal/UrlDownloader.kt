package io.github.volovatovta.docviewer.internal

import android.content.Context
import android.net.Uri
import io.github.volovatovta.docviewer.DocumentSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Downloads documents over HTTP(S), following redirects by hand under stricter rules than HttpURLConnection.
internal object UrlDownloader {
    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 30_000
    private const val MAX_REDIRECTS = 5
    private const val HTTPS = "https"
    private val SupportedProtocols = setOf("http", HTTPS)
    private val Success = 200..299
    private val Redirect = 300..399
    private val FileNameInDisposition = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?")

    private class Response(val connection: HttpURLConnection, val url: URL)

    suspend fun download(
        context: Context,
        source: DocumentSource.FromUrl,
        onProgress: (Float?) -> Unit,
    ): ResolvedFile {
        val response = connect(source)
        try {
            val metadata = SourceMetadata(
                fileName = response.fileName(),
                mimeType = response.connection.contentType,
                sizeBytes = response.contentLength(),
            )
            return DocumentCache.store(context, metadata, onProgress) { response.connection.inputStream }
        } catch (e: IOException) {
            throw NetworkException("Download failed", e)
        } finally {
            response.connection.disconnect()
        }
    }

    private fun connect(source: DocumentSource.FromUrl): Response {
        val origin = URL(source.url)
        var url = origin
        repeat(MAX_REDIRECTS + 1) {
            // Caller headers (often Authorization) must not leak to another host after a redirect.
            val headers = if (url.host.equals(origin.host, ignoreCase = true)) source.headers else emptyMap()
            val connection = open(url, headers)
            val code = connection.responseCodeOf(url)
            if (code in Success) return Response(connection, url)
            val next = connection.getHeaderField("Location")?.takeIf { code in Redirect }?.let { URL(url, it) }
            connection.disconnect()
            url = next?.also { requireSafeRedirect(from = url, to = it) }
                ?: throw NetworkException("HTTP $code for ${source.url}")
        }
        throw NetworkException("Too many redirects for ${source.url}")
    }

    private fun open(url: URL, headers: Map<String, String>): HttpURLConnection {
        if (url.protocol !in SupportedProtocols) throw NetworkException("Unsupported URL scheme: ${url.protocol}")
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = false
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
    }

    private fun HttpURLConnection.responseCodeOf(url: URL): Int = try {
        responseCode
    } catch (e: IOException) {
        throw NetworkException("Cannot connect to ${url.host}", e)
    }

    private fun requireSafeRedirect(from: URL, to: URL) {
        if (from.protocol == HTTPS && to.protocol != HTTPS) {
            throw NetworkException("Refusing redirect from HTTPS to ${to.protocol}")
        }
    }

    private fun Response.fileName(): String? {
        val fromHeader = connection.getHeaderField("Content-Disposition")
            ?.let { FileNameInDisposition.find(it)?.groupValues?.get(1) }
        val fromPath = url.path.substringAfterLast('/').takeIf { it.isNotEmpty() }
        return (fromHeader ?: fromPath)?.let(Uri::decode)
    }

    // URLConnection.contentLengthLong is API 24+.
    private fun Response.contentLength(): Long? =
        connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
}
