package io.github.volovatovta.docviewer.core.ooxml

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The DOCTYPE check that Android's own parser cannot be trusted with, tested without any parser.
class PrologTest {

    private val declaration = "<?xml version=\"1.0\"?>"

    @Test
    fun `ordinary prologs reach the root element`() {
        val prologs = listOf(
            "<w:document/>",
            "﻿$declaration\r\n<!-- note --><w:document>",
            "$declaration<?mso-application progid=\"Word.Document\"?><a>",
        )
        prologs.forEach { assertTrue(Prolog.endsBeforeAnyDoctype(it), it) }
    }

    @Test
    fun `a DOCTYPE anywhere before the root is refused`() {
        val prologs = listOf(
            "<!DOCTYPE d><d/>",
            "$declaration<!-- <a> --><!DOCTYPE d><d/>",
            "$declaration\n  <!DOCTYPE d [<!ENTITY a \"x\">]><d/>",
        )
        prologs.forEach { assertFalse(Prolog.endsBeforeAnyDoctype(it), it) }
    }

    @Test
    fun `a prolog that never ends within the bytes read ahead is refused`() {
        assertFalse(Prolog.endsBeforeAnyDoctype("<!-- never closed"))
        assertFalse(Prolog.endsBeforeAnyDoctype("<!--" + " ".repeat(Prolog.LIMIT)))
    }
}
