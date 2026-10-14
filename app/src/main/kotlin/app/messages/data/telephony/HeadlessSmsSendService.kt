package app.messages.data.telephony

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Required by ROLE_SMS — answers `ACTION_RESPOND_VIA_MESSAGE` intents from
 * the dialer when a user rejects an incoming call with a canned reply.
 *
 * M1 implements the minimum viable flow: extract recipient + body from the
 * intent and dispatch through [SmsSender]. No UI shown.
 */
@AndroidEntryPoint
class HeadlessSmsSendService : Service() {

    @Inject lateinit var sender: SmsSender

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY
        try {
            val recipient = intent.data?.schemeSpecificPart?.substringBefore('?')
            val body = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra("sms_body")
                ?: ""
            if (!recipient.isNullOrBlank() && body.isNotBlank()) {
                sender.send(recipient, body)
            }
        } catch (_: Throwable) {
            // Quick-reply failures must not crash the host (the dialer).
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
