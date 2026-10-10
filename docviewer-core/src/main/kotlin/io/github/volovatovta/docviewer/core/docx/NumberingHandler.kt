package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.ooxml.get
import io.github.volovatovta.docviewer.core.ooxml.int
import io.github.volovatovta.docviewer.core.ooxml.localName
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

// Collects w:abstractNum level definitions and the w:num instances that use them.
internal class NumberingHandler : DefaultHandler() {
    val abstractLevels = HashMap<String, MutableMap<Int, NumberingLevel>>()
    val instances = HashMap<String, NumberingInstance>()

    private var abstractId: String? = null
    private var level: NumberingLevel? = null
    private var instanceId: String? = null
    private var instanceAbstractId: String? = null
    private var startOverrides = HashMap<Int, Int>()
    private var overrideLevel: Int? = null

    private val insideOverride get() = overrideLevel != null

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        val element = localName(localName, qName)
        when (element) {
            "abstractNum" -> abstractId = attributes["abstractNumId"]
            "lvl" -> if (!insideOverride) startLevel(attributes)
            "num" -> startInstance(attributes)
            "abstractNumId" -> if (instanceId != null) instanceAbstractId = attributes["val"]
            "lvlOverride" -> overrideLevel = attributes.int("ilvl")
            "startOverride" -> overrideLevel?.let { lvl -> attributes.int("val")?.let { startOverrides[lvl] = it } }
            else -> level?.let { readLevelProperty(element, attributes, it) }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (localName(localName, qName)) {
            "abstractNum" -> abstractId = null
            "lvl" -> if (!insideOverride) level = null
            "lvlOverride" -> overrideLevel = null
            "num" -> finishInstance()
        }
    }

    private fun startLevel(attributes: Attributes) {
        val definition = NumberingLevel()
        abstractId?.let { abstractLevels.getOrPut(it) { HashMap() }[attributes.int("ilvl") ?: 0] = definition }
        level = definition
    }

    private fun readLevelProperty(element: String, attributes: Attributes, level: NumberingLevel) {
        when (element) {
            "start" -> level.start = attributes.int("val") ?: 1
            "numFmt" -> level.format = attributes["val"] ?: NumberingLevel.DECIMAL
            "lvlText" -> level.text = attributes["val"].orEmpty()
            "ind" -> {
                level.indentLeft = attributes.int("start") ?: attributes.int("left")
                level.hanging = attributes.int("hanging")
            }
            "rFonts" -> level.font = attributes["ascii"] ?: attributes["hAnsi"]
        }
    }

    private fun startInstance(attributes: Attributes) {
        instanceId = attributes["numId"]
        instanceAbstractId = null
        startOverrides = HashMap()
    }

    private fun finishInstance() {
        val id = instanceId
        val abstract = instanceAbstractId
        if (id != null && abstract != null) instances[id] = NumberingInstance(abstract, startOverrides)
        instanceId = null
    }
}
