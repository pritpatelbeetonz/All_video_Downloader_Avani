package app.messages.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.ui.theme.appScreenBg

/**
 * In-app Notification settings (§F7), styled like the main Settings (Figma
 * 43:1213) via the shared [SettingsComponents] rows. App-level toggles cover what
 * the app itself controls — the master message gate and the lock-screen / shade
 * preview, backed by [AppPrefs] and applied live by MessageNotifier.
 *
 * Per-channel sound/vibration/importance live in the OS (Android 8+) and can only
 * be edited on Android's own un-styleable channel screen. Rather than bounce the
 * user into that bland system UI from prominent rows, everything stays on our
 * polished surface with a single, clearly-labelled "system settings" link.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    vm: NotificationSettingsViewModel = hiltViewModel(),
) {
    val font = MaterialTheme.typography.titleMedium.fontFamily
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val notificationsEnabled by vm.notificationsEnabled.collectAsStateWithLifecycle()
    val messagePreview by vm.messagePreview.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appScreenBg())
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", tint = scheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(2.dp))
            Text("Notifications", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 22.sp, color = scheme.onSurface)
        }

        SectionLabel("General", font)
        SettingsGroup {
            SettingRow(
                Icons.Outlined.Notifications, "Message notifications", font,
                subtitle = "Alert me when a new message arrives",
                toggle = notificationsEnabled, onToggle = vm::setNotificationsEnabled, divider = true,
            )
            SettingRow(
                Icons.Outlined.Visibility, "Message preview", font,
                subtitle = "Show sender and text in notifications",
                toggle = messagePreview, onToggle = vm::setMessagePreview,
            )
        }

        SectionLabel("Sound & more", font)
        SettingsGroup {
            SettingRow(
                Icons.Outlined.Tune, "All notification settings", font,
                subtitle = "Sound, vibration & importance in system settings",
                onClick = { openAppNotificationSettings(context) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Opens the system per-app notification page (channels, sound, vibration). */
private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
