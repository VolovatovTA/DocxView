package io.github.volovatovta.docviewer.core.docx

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

internal object Xml {

    // Hardening switches; not every platform parser knows all of them, and Android's Expat never loads entities anyway.
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
        factory.newSAXParser().parse(InputSource(input), handler)
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
