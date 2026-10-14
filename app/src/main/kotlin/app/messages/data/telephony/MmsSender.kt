package app.messages.data.telephony

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Sends an MMS (media + optional caption) via the klinker [Transaction] using the
 * **system** send path (`SmsManager.sendMultimediaMessage` under the hood), which
 * handles the carrier MMSC/APN automatically on API 21+.
 *
 * Media handling is type-aware (§F5):
 * - **Photos** (image types except gif) are downscaled + JPEG-compressed under
 *   [TARGET_BYTES] — carriers reject oversized MMS.
 * - **GIFs** are sent **raw** (re-encoding would kill the animation).
 * - **Audio / video / other** are sent raw, capped at [MAX_RAW_BYTES].
 */
@Singleton
class MmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Sends [uri] (typed by [mimeType]) as an MMS with an optional [caption]. */
    fun send(address: String, caption: String, uri: Uri, mimeType: String) {
        val media = prepare(uri, mimeType) ?: return
        val settings = Settings().apply {
            useSystemSending = true
            setDeliveryReports(false)
            setGroup(true)
        }
        val message = Message(caption, address)
        message.addMedia(media.bytes, media.contentType, media.name)
        Transaction(context, settings).sendNewMessage(message, Transaction.NO_THREAD_ID)
    }

    private class Media(val bytes: ByteArray, val contentType: String, val name: String)

    private fun prepare(uri: Uri, mimeType: String): Media? = when {
        mimeType == "image/gif" ->
            readRaw(uri)?.let { Media(it, "image/gif", "attachment.gif") }
        mimeType.startsWith("image/") ->
            compress(uri)?.let { Media(it, "image/jpeg", "image.jpg") }
        mimeType.startsWith("audio/") ->
            readRaw(uri)?.let { Media(it, mimeType, "audio${extFor(mimeType)}") }
        mimeType.startsWith("video/") ->
            readRaw(uri)?.let { Media(it, mimeType, "video${extFor(mimeType)}") }
        mimeType.contains("vcard") ->
            readRaw(uri)?.let { Media(it, mimeType, "contact.vcf") }
        else ->
            readRaw(uri)?.let { Media(it, mimeType, "attachment") }
    }

    /** Reads the whole stream, rejecting empties + anything over the raw cap. */
    private fun readRaw(uri: Uri): ByteArray? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() && it.size <= MAX_RAW_BYTES }

    private fun extFor(mimeType: String): String = when {
        mimeType.contains("3gpp") || mimeType.contains("amr") -> ".3gp"
        mimeType.contains("mp4") || mimeType.contains("aac") -> ".m4a"
        mimeType.contains("mpeg") -> ".mp3"
        mimeType.contains("ogg") -> ".ogg"
        else -> ""
    }

    /** Decode (downsampled), then JPEG-compress stepping quality down to fit. */
    private fun compress(uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0) return null

        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_DIMENSION) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        var quality = 85
        var out = jpeg(bitmap, quality)
        while (out.size > TARGET_BYTES && quality > 35) {
            quality -= 15
            out = jpeg(bitmap, quality)
        }
        bitmap.recycle()
        return out
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            .toByteArray()

    private companion object {
        const val MAX_DIMENSION = 1280
        const val TARGET_BYTES = 300 * 1024 // ~300KB — safe across carriers
        const val MAX_RAW_BYTES = 1024 * 1024 // 1MB cap for raw gif/audio/video
    }
}
