package io.github.volovatovta.docviewer.core.ooxml

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

internal object Xml {

    // Hardening switches; not every platform parser knows all of them, hence the DOCTYPE check in parse as well.
    private val secureFeatures = mapOf(
        // OOXML parts never carry a DOCTYPE, so rejecting it also stops entity-expansion bombs on the JVM.
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        XMLConstants.FEATURE_SECURE_PROCESSING to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
    )

    private val factory: SAXParserFactory by lazy {
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            secureFeatures.forEach { (feature, enabled) -> runCatching { setFeature(feature, enabled) } }
        }
    }

    fun parse(input: InputStream, handler: DefaultHandler) {
        factory.newSAXParser().parse(InputSource(input.refusingDoctype()), handler)
    }

    // Old Android Expat ignores disallow-doctype-decl and expands internal entities without limit before any
    // callback can stop it, so the prolog is read ahead and a DTD refused before the parser sees it.
    private fun InputStream.refusingDoctype(): InputStream {
        val input = BufferedInputStream(this, Prolog.LIMIT)
        input.mark(Prolog.LIMIT)
        val bytes = ByteArray(Prolog.LIMIT)
        var length = 0
        while (length < Prolog.LIMIT) {
            val read = input.read(bytes, length, Prolog.LIMIT - length)
            if (read < 0) break
            length += read
        }
        input.reset()
        // Dropping zero bytes reads the ASCII markup of UTF-16 parts as well.
        val prolog = String(bytes, 0, length, Charsets.ISO_8859_1).replace("\u0000", "")
        if (!Prolog.endsBeforeAnyDoctype(prolog)) throw SAXException("A DOCTYPE is not allowed")
        return input
    }
}

/** What may precede the root element: a declaration, comments and processing instructions. */
internal object Prolog {
    const val LIMIT = 64 * 1024

    // True once the root element starts; false at a DOCTYPE, or when the prolog does not end within the bytes.
    fun endsBeforeAnyDoctype(text: String): Boolean {
        var at = text.indexOf('<')
        while (at >= 0) {
            val skipTo = when {
                text.startsWith("<?", at) -> text.indexOf("?>", at)
                text.startsWith("<!--", at) -> text.indexOf("-->", at)
                else -> return !text.startsWith("<!", at)
            }
            if (skipTo < 0) return false
            at = text.indexOf('<', skipTo)
        }
        return text.length < LIMIT
    }
}

/** A SAX handler that sees local element names only, so prefixes and namespace URIs never matter. */
internal abstract class ElementHandler : DefaultHandler() {
    // The text of the element being read, kept while collecting is on.
    protected val text = StringBuilder()
    protected var collecting = false

    protected open fun start(name: String, attributes: Attributes) = Unit

    protected open fun end(name: String) = Unit

    final override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) =
        start(localName(localName, qName), attributes)

    final override fun endElement(uri: String?, localName: String?, qName: String?) = end(localName(localName, qName))

    final override fun characters(ch: CharArray, start: Int, length: Int) {
        if (collecting) text.appendRange(ch, start, start + length)
    }
}

internal fun localName(localName: String?, qName: String?): String =
    if (!localName.isNullOrEmpty()) localName else qName.orEmpty().substringAfter(':')

// Attribute lookup by local name: transitional and strict OOXML use different namespace URIs.
internal operator fun Attributes.get(local: String): String? =
    (0 until length).firstOrNull { localName(getLocalName(it), getQName(it)) == local }?.let(::getValue)

internal fun Attributes.int(local: String): Int? {
    val value = this[local]?.trim() ?: return null
    return value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
}

// OOXML on/off attribute: a missing w:val means "on".
internal fun Attributes.onOff(): Boolean = this["val"]?.lowercase().let { it == null || it in ON_VALUES }

internal fun Attributes.isOn(local: String): Boolean = this[local]?.lowercase() in ON_VALUES

internal fun parseHexColor(value: String?): Int? {
    val hex = value?.trim()?.removePrefix("#")?.takeIf { it.length == HEX_COLOR_LENGTH } ?: return null
    return hex.toIntOrNull(HEX_RADIX)?.let { it or OPAQUE_ALPHA }
}

private val ON_VALUES = setOf("1", "true", "on")
private const val HEX_COLOR_LENGTH = 6
private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF000000.toInt()
