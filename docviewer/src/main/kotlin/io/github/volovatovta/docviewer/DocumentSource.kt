package io.github.volovatovta.docviewer

import android.net.Uri
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File
import java.io.InputStream

// Where the document comes from. `format` forces a parser instead of detecting it from the content.
public sealed class DocumentSource {
    public abstract val format: DocumentFormat?

    public data class FromUri(
        val uri: Uri,
        val fileName: String? = null,
        val mimeType: String? = null,
        override val format: DocumentFormat? = null,
    ) : DocumentSource()

    public data class FromFile(val file: File, override val format: DocumentFormat? = null) : DocumentSource()

    public data class FromAsset(val path: String, override val format: DocumentFormat? = null) : DocumentSource()

    public data class FromUrl(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        override val format: DocumentFormat? = null,
    ) : DocumentSource()

    public class FromBytes(
        public val bytes: ByteArray,
        public val fileName: String? = null,
        override val format: DocumentFormat? = null,
    ) : DocumentSource() {
        // Same array instance = same document, so an inline `DocumentSource.bytes(b)` does not reload on recomposition.
        override fun equals(other: Any?): Boolean = other is FromBytes && other.bytes === bytes && other.fileName == fileName && other.format == format

        override fun hashCode(): Int = System.identityHashCode(bytes) * 31 + (fileName?.hashCode() ?: 0)
    }

    // Compared by identity: remember it, or every recomposition starts a new load.
    public class FromStream(
        public val fileName: String? = null,
        override val format: DocumentFormat? = null,
        public val open: StreamProvider,
    ) : DocumentSource()

    public fun interface StreamProvider {
        public fun open(): InputStream
    }

    public companion object {
        @JvmStatic
        @JvmOverloads
        public fun uri(uri: Uri, format: DocumentFormat? = null): DocumentSource = FromUri(uri, format = format)

        @JvmStatic
        @JvmOverloads
        public fun file(file: File, format: DocumentFormat? = null): DocumentSource = FromFile(file, format)

        @JvmStatic
        @JvmOverloads
        public fun asset(path: String, format: DocumentFormat? = null): DocumentSource = FromAsset(path, format)

        @JvmStatic
        @JvmOverloads
        public fun url(url: String, headers: Map<String, String> = emptyMap()): DocumentSource = FromUrl(url, headers)

        @JvmStatic
        @JvmOverloads
        public fun bytes(bytes: ByteArray, fileName: String? = null): DocumentSource = FromBytes(bytes, fileName)

        // Parses the dv_source XML attribute: asset:///, file://, content://, http(s):// or a plain path.
        @JvmStatic
        public fun parse(value: String): DocumentSource = when {
            value.startsWith("asset:///") -> FromAsset(value.removePrefix("asset:///"))
            value.startsWith("http://") || value.startsWith("https://") -> FromUrl(value)
            value.startsWith("content://") || value.startsWith("file://") || value.startsWith("android.resource://") -> FromUri(Uri.parse(value))
            else -> FromFile(File(value))
        }
    }
}
