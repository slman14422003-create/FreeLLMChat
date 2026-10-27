package com.freellm.chat.ui

import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freellm.chat.R

sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Para(val text: String) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Item(val indent: Int, val marker: String, val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    data object Rule : MdBlock()
}

private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
private val itemRe = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
private val ruleRe = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val sepRe = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split("|").map { it.trim() }

fun parseBlocks(src: String): List<MdBlock> {
    val lines = src.replace("\r", "").split("\n")
    val out = ArrayList<MdBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out.add(MdBlock.Para(para.toString().trim()))
        para.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        if (t.startsWith("```")) {
            flush()
            val lang = t.removePrefix("```").trim()
            val sb = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                sb.append(lines[i]).append('\n')
                i++
            }
            out.add(MdBlock.Code(lang, sb.toString().trimEnd('\n')))
            i++
            continue
        }
        val h = headingRe.find(t)
        val li = itemRe.find(line)
        when {
            t.isEmpty() -> flush()
            h != null -> {
                flush()
                out.add(MdBlock.Heading(h.groupValues[1].length, h.groupValues[2]))
            }
            ruleRe.matches(t) -> {
                flush()
                out.add(MdBlock.Rule)
            }
            t.startsWith("|") && i + 1 < lines.size && sepRe.matches(lines[i + 1].trim()) -> {
                flush()
                val header = splitRow(t)
                val rows = ArrayList<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows.add(splitRow(lines[i]))
                    i++
                }
                out.add(MdBlock.Table(header, rows))
                continue
            }
            li != null -> {
                flush()
                out.add(MdBlock.Item(li.groupValues[1].length / 2, li.groupValues[2], li.groupValues[3]))
            }
            t.startsWith(">") -> {
                flush()
                out.add(MdBlock.Quote(t.removePrefix(">").trim()))
            }
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(t)
            }
        }
        i++
    }
    flush()
    return out
}

private val inlineRe = Regex(
    "\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)|(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])|~~(.+?)~~"
)

fun inlineMd(text: String, codeBg: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlineRe.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val g = m.groups
        when {
            g[1] != null || g[2] != null ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append((g[1] ?: g[2])!!.value) }
            g[3] != null ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(g[3]!!.value) }
            g[4] != null -> {
                pushStringAnnotation("URL", g[5]!!.value)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(g[4]!!.value)
                }
                pop()
            }
            g[6] != null ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]!!.value) }
            g[7] != null ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[7]!!.value) }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

private data class CodeColors(val kw: Color, val str: Color, val num: Color, val com: Color)

private val keywords = setOf(
    "fun", "val", "var", "if", "else", "for", "while", "return", "class", "object", "interface",
    "import", "package", "when", "in", "is", "null", "true", "false", "def", "function", "const",
    "let", "new", "this", "self", "from", "as", "try", "catch", "except", "finally", "throw",
    "public", "private", "static", "void", "int", "string", "async", "await", "switch", "case",
    "break", "continue", "override", "data", "enum", "struct", "None", "True", "False", "lambda",
    "elif", "print", "fn", "mut", "impl", "trait", "pub", "use", "mod", "match", "func", "defer",
    "type", "extends", "implements", "abstract", "final", "suspend", "companion", "sealed", "export",
    "default", "do", "with", "yield", "pass", "and", "or", "not", "boolean", "double", "float", "long"
)

private val tokenRe = Regex(
    "(/\\*[\\s\\S]*?\\*/|//[^\\n]*|#[^\\n]*)|(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')|(\\b\\d+(?:\\.\\d+)?\\b)|(\\b[A-Za-z_][A-Za-z0-9_]*\\b)"
)

private fun highlight(code: String, c: CodeColors): AnnotatedString = buildAnnotatedString {
    append(code)
    for (m in tokenRe.findAll(code)) {
        val color = when {
            m.groups[1] != null -> c.com
            m.groups[2] != null -> c.str
            m.groups[3] != null -> c.num
            m.value in keywords -> c.kw
            else -> null
        }
        if (color != null) addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
    }
}

private fun libLanguage(lang: String): SyntaxLanguage? = when (lang.trim().lowercase()) {
    "kotlin", "kt" -> SyntaxLanguage.KOTLIN
    "java" -> SyntaxLanguage.JAVA
    "python", "py" -> SyntaxLanguage.PYTHON
    "javascript", "js" -> SyntaxLanguage.JAVASCRIPT
    "typescript", "ts" -> SyntaxLanguage.TYPESCRIPT
    "c" -> SyntaxLanguage.C
    "cpp", "c++" -> SyntaxLanguage.CPP
    "rust", "rs" -> SyntaxLanguage.RUST
    "go", "golang" -> SyntaxLanguage.GO
    "swift" -> SyntaxLanguage.SWIFT
    "php" -> SyntaxLanguage.PHP
    "ruby", "rb" -> SyntaxLanguage.RUBY
    "bash", "sh", "shell" -> SyntaxLanguage.SHELL
    "dart" -> SyntaxLanguage.DART
    else -> null
}

private fun libHighlight(lang: String, code: String, dark: Boolean): AnnotatedString? {
    val l = libLanguage(lang) ?: return null
    return try {
        val engine = Highlights.Builder().code(code).language(l).theme(SyntaxThemes.monokai(dark)).build()
        buildAnnotatedString {
            append(code)
            for (h in engine.getHighlights()) {
                if (h is ColorHighlight) {
                    val a = h.location.start
                    val b = h.location.end
                    if (a >= 0 && b > a && b <= code.length) {
                        addStyle(SpanStyle(color = Color(0xFF000000.toInt() or h.rgb)), a, b)
                    }
                }
            }
        }
    } catch (e: Throwable) {
        null
    }
}

@Composable
private fun InlineText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val annotated = remember(text, cs.tertiary, cs.surfaceVariant) {
        inlineMd(text, cs.surfaceVariant, cs.tertiary)
    }
    val hasLink = annotated.getStringAnnotations("URL", 0, annotated.length).isNotEmpty()
    if (hasLink) {
        val uri = LocalUriHandler.current
        ClickableText(
            text = annotated,
            modifier = modifier,
            style = style.copy(color = cs.onBackground, textAlign = TextAlign.Start),
            onClick = { off ->
                annotated.getStringAnnotations("URL", off, off).firstOrNull()?.let {
                    runCatching { uri.openUri(it.item) }
                }
            }
        )
    } else {
        Text(annotated, modifier, style = style, textAlign = TextAlign.Start)
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val colors = remember(dark) {
        if (dark) CodeColors(Color(0xFFC792EA), Color(0xFFC3E88D), Color(0xFFF78C6C), Color(0xFF8A8A8A))
        else CodeColors(Color(0xFF7B3FBF), Color(0xFF2E7D32), Color(0xFFD35400), Color(0xFF8A8A8A))
    }
    val annotated = remember(code, lang, dark) { libHighlight(lang, code, dark) ?: highlight(code, colors) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corners.sm))
                .background(cs.surfaceVariant)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, end = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    lang.ifEmpty { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { clip.setText(AnnotatedString(code)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy), Modifier.size(16.dp))
                }
            }
            Text(
                annotated,
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
                softWrap = false
            )
        }
    }
}

@Composable
private fun RenderBlock(b: MdBlock) {
    val body = MaterialTheme.typography.bodyLarge
    val cs = MaterialTheme.colorScheme
    when (b) {
        is MdBlock.Heading -> {
            val size = when (b.level) {
                1 -> 22.sp
                2 -> 20.sp
                3 -> 18.sp
                else -> 16.sp
            }
            InlineText(b.text, body.copy(fontSize = size, lineHeight = size * 1.4f, fontWeight = FontWeight.Bold))
        }
        is MdBlock.Para -> InlineText(b.text, body)
        is MdBlock.Code -> CodeBlock(b.lang, b.code)
        is MdBlock.Item -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
            val marker = if (b.marker.first().isDigit()) b.marker else "•"
            Text(marker, style = body, modifier = Modifier.padding(end = Spacing.sm))
            InlineText(b.text, body, Modifier.weight(1f))
        }
        is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .padding(end = Spacing.md)
                    .size(width = 3.dp, height = 22.dp)
                    .background(cs.outline)
            ) {}
            InlineText(b.text, body.copy(color = cs.onSurfaceVariant), Modifier.weight(1f))
        }
        is MdBlock.Table -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corners.sm))
                .background(cs.surfaceVariant)
        ) {
            val cols = b.header.size
            Row(Modifier.fillMaxWidth()) {
                b.header.forEach {
                    InlineText(
                        it,
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        Modifier.weight(1f).padding(Spacing.sm)
                    )
                }
            }
            b.rows.forEach { row ->
                HorizontalDivider(color = cs.outline)
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until cols) {
                        InlineText(
                            row.getOrElse(c) { "" },
                            MaterialTheme.typography.bodyMedium,
                            Modifier.weight(1f).padding(Spacing.sm)
                        )
                    }
                }
            }
        }
        MdBlock.Rule -> HorizontalDivider(color = cs.outline)
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val base = LocalLayoutDirection.current
    val dir = detectDirection(text) ?: base
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        val blocks = remember(text) { parseBlocks(text) }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            blocks.forEach { RenderBlock(it) }
        }
    }
}
