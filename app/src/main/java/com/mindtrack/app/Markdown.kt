package com.mindtrack.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

private val bulletLine = Regex("""^\s*[-*•]\s+(.*)$""")
private val numberedLine = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
private val headingLine = Regex("""^\s*#{1,6}\s+(.*)$""")
private val inline = Regex(
    """\*\*(?=\S)(.+?)(?<=\S)\*\*|\*(?=\S)(.+?)(?<=\S)\*|\[([^\]]+)]\((https?://[^)\s]+)\)|(https?://[^\s)]+)"""
)

/**
 * A small, safe Markdown subset for AI replies: **bold**, *italic*, bullets, numbered lists, headings and
 * http(s) links. Anything it doesn't recognise (including half-finished markup while a reply is still
 * streaming) is shown as plain text.
 */
internal fun markdownToAnnotated(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.replace("\r\n", "\n").split("\n")
    lines.forEachIndexed { index, line ->
        val bullet = bulletLine.matchEntire(line)
        val numbered = numberedLine.matchEntire(line)
        val heading = headingLine.matchEntire(line)
        when {
            bullet != null -> { append("•  "); appendInline(bullet.groupValues[1], linkColor) }
            numbered != null -> { append("${numbered.groupValues[1]}.  "); appendInline(numbered.groupValues[2], linkColor) }
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(heading.groupValues[1], linkColor) }
            else -> appendInline(line, linkColor)
        }
        if (index < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendInline(text: String, linkColor: Color) {
    var last = 0
    for (m in inline.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val bold = m.groups[1]?.value
        val italic = m.groups[2]?.value
        val label = m.groups[3]?.value
        val url = m.groups[4]?.value ?: m.groups[5]?.value
        when {
            bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            italic != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            url != null -> withLink(
                LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))
            ) { append(label ?: url) }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}
