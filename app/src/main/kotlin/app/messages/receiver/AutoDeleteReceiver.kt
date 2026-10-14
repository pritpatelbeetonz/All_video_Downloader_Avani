package app.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import app.messages.preferences.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AutoDeleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = PreferenceHelper.getInstance(context)
                val retentionDays = prefs.getAutoDeletePeriod()
                
                if (retentionDays > 0) {
                    val cutoffCalendar = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -retentionDays)
                    }
                    val cutoffTimestamp = cutoffCalendar.timeInMillis
                    
                    val contentResolver = context.contentResolver
                    
                    // Delete from SMS
                    val smsUri = Telephony.Sms.CONTENT_URI
                    val smsWhere = "${Telephony.Sms.DATE} < ?"
                    val smsWhereArgs = arrayOf(cutoffTimestamp.toString())
                    
                    val smsDeleted = contentResolver.delete(smsUri, smsWhere, smsWhereArgs)
                    // Delete from MMS
                    val mmsUri = Telephony.Mms.CONTENT_URI
                    val mmsWhere = "${Telephony.Mms.DATE} < ?"
                    // MMS date is typically stored in seconds, not milliseconds in some OS versions,
                    // but Telephony.Mms.DATE is standardized to seconds.
                    val mmsWhereArgs = arrayOf((cutoffTimestamp / 1000).toString())
                    
                    val mmsDeleted = contentResolver.delete(mmsUri, mmsWhere, mmsWhereArgs)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
