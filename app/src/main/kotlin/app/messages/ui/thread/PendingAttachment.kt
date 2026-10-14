package app.messages.ui.thread

import android.content.Context
import android.net.Uri

/** What kind of media is staged in the composer — drives the preview + send path. */
enum class AttachmentKind { Image, Gif, Audio, Contact }

/**
 * A media attachment staged in the composer, ready to send as MMS. [label] is an
 * optional chip caption (e.g. the contact's name) for non-image kinds.
 */
data class PendingAttachment(
    val uri: Uri,
    val mimeType: String,
    val kind: AttachmentKind,
    val label: String? = null,
)

/**
 * Builds an attachment from a picked/captured image or GIF uri, resolving the
 * real mime so an animated GIF takes the raw send path (not JPEG re-encode).
 */
fun imageAttachmentOf(context: Context, uri: Uri): PendingAttachment {
    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
    val kind = if (mime == "image/gif") AttachmentKind.Gif else AttachmentKind.Image
    return PendingAttachment(uri, mime, kind)
}
