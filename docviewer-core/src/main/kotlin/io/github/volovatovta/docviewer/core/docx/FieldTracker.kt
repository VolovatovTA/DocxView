package io.github.volovatovta.docviewer.core.docx

// Word fields (HYPERLINK, PAGEREF, TOC…): the instruction part is hidden, the result part is shown.
internal class FieldTracker {

    private enum class Phase { Instruction, Result }

    private class Field(var phase: Phase, var link: String? = null) {
        val instruction = StringBuilder()
    }

    private val fields = ArrayList<Field>()

    val isInsideInstruction: Boolean get() = fields.any { it.phase == Phase.Instruction }

    val link: String? get() = fields.lastOrNull { it.link != null }?.link

    fun onFieldCharacter(type: String?) {
        when (type) {
            "begin" -> fields += Field(Phase.Instruction)
            "separate" -> fields.lastOrNull()?.let(::showResult)
            "end" -> end()
        }
    }

    fun beginSimple(instruction: String) {
        fields += Field(Phase.Result, hyperlinkTarget(instruction))
    }

    fun end() {
        fields.removeLastOrNull()
    }

    fun appendInstruction(text: String) {
        fields.lastOrNull()?.takeIf { it.phase == Phase.Instruction }?.instruction?.append(text)
    }

    private fun showResult(field: Field) {
        field.phase = Phase.Result
        field.link = hyperlinkTarget(field.instruction.toString())
    }

    private companion object {
        const val HYPERLINK = "HYPERLINK"
        val localAnchorSwitch = Regex("\\\\l\\b")
        val quotedTarget = Regex("\"([^\"]+)\"")

        // HYPERLINK "url" opens a link; with the \l switch it only jumps to a bookmark inside the document.
        fun hyperlinkTarget(instruction: String): String? {
            val trimmed = instruction.trim()
            val isHyperlink = trimmed.startsWith(HYPERLINK, ignoreCase = true)
            if (!isHyperlink || localAnchorSwitch.containsMatchIn(trimmed)) return null
            return quotedTarget.find(trimmed)?.groupValues?.get(1)
                ?: trimmed.drop(HYPERLINK.length).trim().substringBefore(' ').takeIf { it.isNotEmpty() }
        }
    }
}
