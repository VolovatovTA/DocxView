package io.github.volovatovta.docviewer.core

import java.io.File

internal fun resource(name: String): File {
    val url = requireNotNull(DocxFixtures::class.java.classLoader.getResource(name)) { "missing test resource $name" }
    return File(url.toURI())
}
