package app.messages.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ComposeSmsService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
