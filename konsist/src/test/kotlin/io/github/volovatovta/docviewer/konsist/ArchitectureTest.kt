package io.github.volovatovta.docviewer.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureTest {

    private val core = Konsist.scopeFromDirectory("docviewer-core/src/main")
    private val viewer = Konsist.scopeFromDirectory("docviewer/src/main")

    @Test
    fun `parsers do not depend on Android`() {
        core.files.assertFalse { it.importsAny("android.", "androidx.") }
    }

    // The parsers run on Android 6 too, where java.time and newer java.nio APIs do not exist yet.
    @Test
    fun `parsers use only APIs that Android has since API 23`() {
        core.files.assertFalse { it.importsAny("java.time", "java.nio.file") }
    }

    // Supporting a format takes one reader registered in Formats; nothing else may pick formats by hand.
    @Test
    fun `format readers are objects named after their format`() {
        core.objects()
            .filter { it.hasParentWithName("FormatReader") }
            .assertTrue { it.hasInternalModifier && it.name.endsWith("Format") }
    }

    @Test
    fun `parsers do not depend on the viewer`() {
        core.files.assertFalse { file ->
            file.imports.any { it.name.startsWith(VIEWER_PACKAGE) && !it.name.startsWith(CORE_PACKAGE) }
        }
    }

    @Test
    fun `top-level declarations in internal packages are not public`() {
        viewer.classesAndInterfacesAndObjects()
            .filter { it.isTopLevel && it.resideInPackage("..internal..") }
            .assertTrue { it.hasInternalModifier || it.hasPrivateModifier }
    }

    @Test
    fun `viewer controllers are internal and named after their role`() {
        viewer.classes()
            .filter { it.hasParentWithName("ViewerController") }
            .assertTrue { it.resideInPackage("..internal..") && it.name.endsWith("Controller") }
    }

    @Test
    fun `exception types are named as exceptions`() {
        (core.classes() + viewer.classes())
            .filter { declaration -> declaration.parents(indirectParents = true).any { it.name.endsWith("Exception") } }
            .assertTrue { it.name.endsWith("Exception") }
    }

    @Test
    fun `parser entry points are stateless objects`() {
        core.objects()
            .filter { it.name.endsWith("Parser") }
            .assertTrue { it.hasPublicOrDefaultModifier && it.properties().none { property -> property.isVar } }
    }

    private fun KoFileDeclaration.importsAny(vararg prefixes: String): Boolean =
        imports.any { import -> prefixes.any { import.name.startsWith(it) } }

    private companion object {
        const val VIEWER_PACKAGE = "io.github.volovatovta.docviewer."
        const val CORE_PACKAGE = "io.github.volovatovta.docviewer.core"
    }
}
