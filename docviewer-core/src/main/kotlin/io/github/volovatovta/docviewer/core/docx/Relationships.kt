package io.github.volovatovta.docviewer.core.docx

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream

internal class Relationship(val type: String, val target: String, val external: Boolean)

// Type URIs differ between transitional and strict OOXML, so relationships are matched by their last segment.
internal object RelationshipTypes {
    const val OFFICE_DOCUMENT = "/officeDocument"
    const val STYLES = "/styles"
    const val NUMBERING = "/numbering"

    fun notes(kind: String) = "/${kind}s"
}

// The _rels/*.rels of one part: relationship id → target, with internal targets resolved to ZIP paths.
internal class Relationships(private val byId: Map<String, Relationship>) {

    fun internalTarget(id: String?): String? = id?.let(byId::get)?.takeUnless { it.external }?.target

    fun externalTarget(id: String?): String? = id?.let(byId::get)?.takeIf { it.external }?.target

    fun ofType(typeSuffix: String): Relationship? = byId.values.firstOrNull { it.type.endsWith(typeSuffix) }

    fun partOfType(typeSuffix: String): String? =
        byId.values.firstOrNull { it.type.endsWith(typeSuffix) && !it.external }?.target

    companion object {
        val None = Relationships(emptyMap())

        // Internal targets are relative to the directory of the part that owns the .rels file.
        fun parse(input: InputStream, ownerDirectory: String): Relationships {
            val handler = RelationshipsHandler(ownerDirectory)
            Xml.parse(input, handler)
            return Relationships(handler.relationships)
        }

        fun resolvePath(baseDirectory: String, target: String): String {
            if (target.startsWith("/")) return target.removePrefix("/")
            val segments = baseDirectory.split('/').filter { it.isNotEmpty() }.toMutableList()
            for (segment in target.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> segments.removeLastOrNull()
                    else -> segments += segment
                }
            }
            return segments.joinToString("/")
        }
    }
}

private class RelationshipsHandler(private val ownerDirectory: String) : DefaultHandler() {
    val relationships = HashMap<String, Relationship>()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        if (localName(localName, qName) != "Relationship") return
        val id = attributes["Id"] ?: return
        val target = attributes["Target"] ?: return
        val external = attributes["TargetMode"].equals("External", ignoreCase = true)
        val resolved = if (external) target else Relationships.resolvePath(ownerDirectory, target)
        relationships[id] = Relationship(attributes["Type"].orEmpty(), resolved, external)
    }
}
