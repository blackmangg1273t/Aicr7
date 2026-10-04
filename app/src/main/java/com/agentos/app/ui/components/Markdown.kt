package com.agentos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/**
 * Minimal markdown renderer for AI responses — no external dependency.
 * Supports: fenced code blocks, inline code, bold, headings, bullets,
 * links, and paragraph spacing. Everything is selectable.
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
    val annotated = remember(text) { buildInline(text, c) }
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

@Composable
private fun CodeBlock(block: MdBlock.Code, c: com.agentos.app.ui.theme.AgentOsColors) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0E15))
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF121623))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                block.language.ifBlank { "code" },
                style = MonoStyle.copy(fontSize = 10.sp),
                color = c.textMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Copy",
                style = MonoStyle.copy(fontSize = 10.sp),
                color = c.accent,
                modifier = Modifier
                    .clip(OsShapes.pill)
                    .pointerInput(block.code) { detectTapGestures { clipboard.setText(AnnotatedString(block.code)) } }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                block.code,
                style = MonoStyle,
                color = Color(0xFFD7DEED),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

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

/** Inline markdown: **bold**, `code`, URL links. */
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
                    withStyle(SpanStyle(color = c.accent, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) { append(url) }
                    pop()
                    i += end
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
