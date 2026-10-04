package com.agentos.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsMotion
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/**
 * Minimal markdown renderer for AI responses — no external dependency.
 * Supports: fenced code blocks with lightweight syntax highlighting,
 * inline code, bold, strikethrough, headings, bullets, links and
 * paragraph spacing. Everything is selectable.
 */

private const val URL_TAG = "url"

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val c = osColors()
    val blocks = remember(markdown) { parseBlocks(markdown) }
    SelectionContainer {
        Column(modifier) {
            blocks.forEachIndexed { i, block ->
                when (block) {
                    is MdBlock.Code -> CodeBlock(block, c)
                    is MdBlock.Heading -> Text(
                        block.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = if (i > 0) 10.dp else 0.dp, bottom = 3.dp)
                    )
                    is MdBlock.Bullet -> Row(Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", color = c.accent, style = MaterialTheme.typography.bodyMedium)
                        InlineMd(block.text, streaming)
                    }
                    is MdBlock.Paragraph -> InlineMd(block.text, streaming, Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun InlineMd(text: String, streaming: Boolean, modifier: Modifier = Modifier) {
    val c = osColors()
    val uri = LocalUriHandler.current
    val annotated = remember(text, c) { buildInline(text, c) }
    val style = MaterialTheme.typography.bodyMedium.let {
        if (streaming) it.copy(color = c.textPrimary) else it
    }
    Text(
        annotated,
        style = style,
        color = c.textPrimary,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { offset ->
                val pos = annotated.getStringAnnotations(URL_TAG, offset.x.toInt(), offset.x.toInt())
                pos.firstOrNull()?.item?.let { runCatching { uri.openUri(it) } }
            }
        }
    )
}

/* ------------------------------------------------------------------ */
/* Code block — header with language + copy, highlighted body.          */
/* ------------------------------------------------------------------ */

@Composable
private fun CodeBlock(block: MdBlock.Code, c: com.agentos.app.ui.theme.AgentOsColors) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1600)
            copied = false
        }
    }
    val highlighted = remember(block.code, block.language, c) {
        highlightCode(block.code, block.language, c)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(OsShapes.code)
            .background(c.codeBg)
            .border(1.dp, c.border, OsShapes.code)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.codeBgHeader)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                block.language.ifBlank { "code" },
                style = MonoStyle.copy(fontSize = 10.sp),
                color = c.textMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (copied) "Copied ✓" else "Copy",
                style = MonoStyle.copy(fontSize = 10.sp),
                color = if (copied) c.success else c.accent,
                modifier = Modifier
                    .clip(OsShapes.pill)
                    .pointerInput(block.code) {
                        detectTapGestures {
                            clipboard.setText(AnnotatedString(block.code))
                            copied = true
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                highlighted,
                style = MonoStyle,
                color = Color(0xFFD7DEED),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Lightweight syntax highlighting (regex tokenizer, zero deps).        */
/* Handles the common cases: strings, comments, numbers, keywords,      */
/* annotations/decorators. Good enough for chat code blocks.            */
/* ------------------------------------------------------------------ */

private val KEYWORDS = setOf(
    // kotlin / java / c-like
    "abstract", "as", "break", "case", "catch", "class", "const", "continue", "data", "def",
    "default", "do", "elif", "else", "enum", "extends", "false", "final", "finally", "for",
    "fun", "function", "if", "import", "in", "init", "interface", "is", "let", "new", "null",
    "object", "override", "package", "private", "protected", "public", "return", "sealed",
    "static", "struct", "super", "suspend", "switch", "this", "throw", "true", "try", "type",
    "val", "var", "when", "while", "with", "yield", "and", "or", "not", "None", "nil",
    "echo", "then", "fi", "esac", "local", "export", "async", "await", "from", "lambda",
    "volatile", "transient", "implements", "instanceof", "throws", "native"
)

private val TOKEN_REGEX = Regex(
    "(?<comment>//[^(\\n)]*|#[^(\\n)]*|--[^(\\n)]*)" +          // 1 comments
        "|(?<string>\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`)" + // 2 strings
        "|(?<annotation>@[A-Za-z_][A-Za-z0-9_.]*)" +              // 3 annotations
        "|(?<number>\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?[fFlL]?)\\b)" + // 4 numbers
        "|(?<word>[A-Za-z_][A-Za-z0-9_]*)"                        // 5 words
)

private fun highlightCode(code: String, language: String, c: com.agentos.app.ui.theme.AgentOsColors): AnnotatedString {
    val isMarkup = language.equals("html", true) || language.equals("xml", true)
    val commentColor = Color(0xFF5A6478)
    val stringColor = Color(0xFF9ECE8C)
    val numberColor = Color(0xFFE5B567)
    val keywordColor = c.accent
    val annotationColor = c.accentViolet
    val keywordStyle = SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)
    val stringStyle = SpanStyle(color = stringColor)
    val commentStyle = SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)
    val numberStyle = SpanStyle(color = numberColor)
    val annotationStyle = SpanStyle(color = annotationColor)

    return buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            val m = TOKEN_REGEX.find(code, i)
            if (m == null) {
                append(code.substring(i)); break
            }
            if (m.range.first > i) append(code.substring(i, m.range.first))
            when {
                !isMarkup && m.groups["comment"] != null -> withStyle(commentStyle) { append(m.value) }
                m.groups["string"] != null -> withStyle(stringStyle) { append(m.value) }
                !isMarkup && m.groups["annotation"] != null -> withStyle(annotationStyle) { append(m.value) }
                m.groups["number"] != null -> withStyle(numberStyle) { append(m.value) }
                m.groups["word"] != null -> {
                    if (m.value in KEYWORDS) withStyle(keywordStyle) { append(m.value) } else append(m.value)
                }
                else -> append(m.value)
            }
            i = m.range.last + 1
        }
    }
}

/* ------------------------------------------------------------------ */
/* Block parser                                                         */
/* ------------------------------------------------------------------ */

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Code(val code: String, val language: String) : MdBlock()
}

private fun parseBlocks(md: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = md.lines()
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotBlank()) {
            blocks += MdBlock.Paragraph(para.toString().trim())
            para.clear()
        }
    }
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.appendLine(lines[i]); i++
            }
            blocks += MdBlock.Code(code.toString().trimEnd(), lang)
        } else if (trimmed.startsWith("#")) {
            flushPara()
            blocks += MdBlock.Heading(trimmed.trimStart('#', ' '))
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("* ")) {
            flushPara()
            blocks += MdBlock.Bullet(trimmed.substring(2).trim())
        } else if (trimmed.isEmpty()) {
            flushPara()
        } else {
            para.appendLine(trimmed)
        }
        i++
    }
    flushPara()
    return blocks
}

/** Inline markdown: **bold**, ~~strike~~, `code`, URL links. */
private fun buildInline(text: String, c: com.agentos.app.ui.theme.AgentOsColors): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 1) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                background = c.surfaceInteractive,
                                color = c.accent
                            )
                        ) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                text.startsWith("http://", i) || text.startsWith("https://", i) -> {
                    val end = text.substring(i).split(' ', ')', '\n').first().length
                    val url = text.substring(i, i + end)
                    pushStringAnnotation(URL_TAG, url)
                    withStyle(SpanStyle(color = c.accent, textDecoration = TextDecoration.Underline)) { append(url) }
                    pop()
                    i += end
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
