package com.freellm.chat.domain

import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object XmlCheck {
    private val doctype = Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)

    fun hasDoctype(text: String): Boolean = doctype.containsMatchIn(text)

    private fun factory(): DocumentBuilderFactory {
        val f = DocumentBuilderFactory.newInstance()
        for ((name, value) in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
        )) {
            try {
                f.setFeature(name, value)
            } catch (e: Exception) {
            }
        }
        try {
            f.isXIncludeAware = false
            f.isExpandEntityReferences = false
        } catch (e: Exception) {
        } catch (e: UnsupportedOperationException) {
        }
        return f
    }

    fun check(text: String): String? {
        if (hasDoctype(text)) return "DOCTYPE and entity declarations are not allowed"
        return try {
            factory().newDocumentBuilder().parse(InputSource(StringReader(text)))
            null
        } catch (e: SAXParseException) {
            "line " + e.lineNumber + ", column " + e.columnNumber + ": " + (e.message ?: "invalid xml").take(160)
        } catch (e: Exception) {
            (e.message ?: "invalid xml").take(200)
        }
    }
}
