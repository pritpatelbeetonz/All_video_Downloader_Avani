package app.messages.ui.thread

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.core.content.FileProvider
import java.io.File

private fun sharedDir(context: Context): File =
    File(context.cacheDir, "shared").apply { mkdirs() }

private fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** A FileProvider uri the camera app can write a captured photo into. */
fun createImageCaptureUri(context: Context): Uri =
    uriFor(context, File(sharedDir(context), "photo-${System.currentTimeMillis()}.jpg"))

/** Writes [bytes] to a temp file in the shared dir, returning its FileProvider uri. */
fun saveSharedBytes(context: Context, fileName: String, bytes: ByteArray): Uri =
    uriFor(context, File(sharedDir(context), fileName).apply { writeBytes(bytes) })

/**
 * Queries the picked contact's name and phone numbers, returning them formatted as plain text.
 */
fun getContactAsText(context: Context, contactUri: Uri): String? {
    val projection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME,
    )
    val info = context.contentResolver.query(contactUri, projection, null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) to (c.getString(1) ?: "Contact") else null
    } ?: return null
    val (contactId, name) = info
    if (contactId.isNullOrBlank()) return null

    val phoneNumbers = mutableListOf<String>()
    val phoneCursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(contactId),
        null
    )
    phoneCursor?.use { cursor ->
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val number = cursor.getString(numberIndex)
            if (!number.isNullOrBlank() && number !in phoneNumbers) {
                phoneNumbers.add(number)
            }
        }
    }

    return if (phoneNumbers.isNotEmpty()) {
        "Name: $name\nPhone: ${phoneNumbers.joinToString(", ")}"
    } else {
        "Name: $name"
    }
}

/**
 * Records a short voice clip as **AMR-NB in a 3GP container** — the most
 * universally MMS-compatible audio format, and small. The recorded file is
 * exposed as a FileProvider uri for the MMS send path.
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    /** Starts a fresh recording. Returns false if the recorder couldn't start. */
    fun start(): Boolean {
        cancel()
        val out = File(sharedDir(context), "voice-${System.currentTimeMillis()}.3gp")
        val rec = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(out.absolutePath)
        }
        return runCatching {
            rec.prepare()
            rec.start()
            recorder = rec
            file = out
            true
        }.getOrElse {
            runCatching { rec.release() }
            false
        }
    }

    /** Stops recording and returns the clip uri, or null if nothing usable was captured. */
    fun stop(): Uri? {
        recorder?.let { rec ->
            recorder = null
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        val f = file ?: return null
        return if (f.exists() && f.length() > 0L) uriFor(context, f) else null
    }

    /** Aborts + deletes the current recording. */
    fun cancel() {
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null
        file?.delete()
        file = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
}
