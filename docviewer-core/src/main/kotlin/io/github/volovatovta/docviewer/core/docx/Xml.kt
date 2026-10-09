package io.github.volovatovta.docviewer.core.docx

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

internal object Xml {
    private val factory: SAXParserFactory by lazy {
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // Not every platform parser knows these features; Android's Expat never resolves external entities anyway.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
    }

    fun parse(input: InputStream, handler: DefaultHandler) {
        factory.newSAXParser().parse(InputSource(input), handler)
    }
}

internal fun localName(localName: String?, qName: String?): String =
    if (!localName.isNullOrEmpty()) localName else qName.orEmpty().substringAfter(':')

// Attribute lookup by local name: transitional and strict OOXML use different namespace URIs.
internal operator fun Attributes.get(local: String): String? {
    for (i in 0 until length) {
        val name = getLocalName(i).let { if (it.isNullOrEmpty()) getQName(i).substringAfter(':') else it }
        if (name == local) return getValue(i)
    }
    return null
}

internal fun Attributes.int(local: String): Int? = this[local]?.trim()?.toIntOrNull() ?: this[local]?.trim()?.toDoubleOrNull()?.toInt()

// OOXML on/off attribute: a missing w:val means "on".
internal fun Attributes.onOff(): Boolean = when (this["val"]?.lowercase()) {
    null, "1", "true", "on" -> true
    else -> false
}

internal fun parseHexColor(value: String?): Int? {
    val v = value?.trim()?.removePrefix("#") ?: return null
    if (v.length != 6 || v.equals("auto", ignoreCase = true)) return null
    return v.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}
