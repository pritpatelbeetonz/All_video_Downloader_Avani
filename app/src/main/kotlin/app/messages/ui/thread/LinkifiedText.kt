package app.messages.ui.thread

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

/**
 * Message text with URLs, emails, and phone numbers detected and made tappable
 * — per build doc §F3. Detection is regex-based (not `Linkify`, which is a
 * View-era API); matches are styled with [linkColor] and open via implicit
 * intents through Compose's `LinkAnnotation` API.
 */
@Composable
fun LinkifiedText(
    text: String,
    linkColor: Color,
    baseStyle: TextStyle = LocalTextStyle.current,
    modifier: Modifier = Modifier,
) {
    Text(
        text = rememberLinkified(text, linkColor),
        style = baseStyle,
        modifier = modifier,
    )
}

private const val TAG_URL = "url"
private const val TAG_EMAIL = "email"
private const val TAG_PHONE = "phone"

private val urlRegex =
    Regex("""(https?://|www\.)[^\s]+""", RegexOption.IGNORE_CASE)
private val emailRegex =
    Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
private val phoneRegex =
    Regex("""(?<!\d)(\+?\d[\d\-\s]{7,}\d)(?!\d)""")

@Composable
private fun rememberLinkified(text: String, linkColor: Color): AnnotatedString {
    val context = LocalContext.current
    return buildAnnotatedString {
        append(text)
        val spans = mutableListOf<MatchSpan>()
        urlRegex.findAll(text).forEach {
            val raw = it.value
            val href = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            spans += MatchSpan(it.range.first, it.range.last + 1, TAG_URL, href)
        }
        emailRegex.findAll(text).forEach {
            spans += MatchSpan(it.range.first, it.range.last + 1, TAG_EMAIL, it.value)
        }
        phoneRegex.findAll(text).forEach { m ->
            // Skip if this range overlaps an already-claimed URL/email span.
            val overlaps = spans.any { m.range.first < it.end && it.start < m.range.last + 1 }
            if (!overlaps) {
                spans += MatchSpan(
                    m.range.first, m.range.last + 1, TAG_PHONE,
                    m.value.replace(Regex("""[\s\-]"""), ""),
                )
            }
        }
        val styles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        )
        spans.sortedBy { it.start }.forEach { span ->
            val link = LinkAnnotation.Clickable(tag = span.tag, styles = styles) {
                open(context, span.tag, span.item)
            }
            addLink(link, span.start, span.end)
        }
    }
}

private fun open(context: Context, tag: String, item: String) {
    val intent = when (tag) {
        TAG_URL -> Intent(Intent.ACTION_VIEW, Uri.parse(item))
        TAG_EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$item"))
        TAG_PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$item"))
        else -> return
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        }
}

private data class MatchSpan(
    val start: Int,
    val end: Int,
    val tag: String,
    val item: String,
)
