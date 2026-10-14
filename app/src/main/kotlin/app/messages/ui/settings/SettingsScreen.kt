package app.messages.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.core.prefs.SwipeAction
import app.messages.core.prefs.ThemeMode
import app.messages.preferences.PreferenceHelper
import app.messages.ui.theme.appScreenBg

/**
 * Settings (§F12) — Figma 43:1213: an "Upgrade to Plus" gradient banner, then
 * labelled groups of rounded cards with brand-tinted icons. Colors read from the
 * theme `colorScheme`, so it matches the design in light mode and renders
 * correctly in dark mode. State stays DataStore-backed via [SettingsViewModel].
 * The shared card rows live in [SettingsComponents].
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPremium: () -> Unit,
    onAppLock: () -> Unit,
    onBackup: () -> Unit,
    onNotificationSettings: () -> Unit,
    onPrivacy: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val font = MaterialTheme.typography.titleMedium.fontFamily
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val swipeAction by vm.swipeAction.collectAsStateWithLifecycle()
    val deliveryReports by vm.deliveryReports.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSwipeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appScreenBg())
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        // Header
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
            Text("Settings", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 22.sp, color = scheme.onSurface)
        }

        Spacer(Modifier.height(10.dp))

        SectionLabel("Appearance", font)
        SettingsGroup {
            SettingRow(Icons.Outlined.LightMode, "Mode", font, value = themeMode.label, onClick = { showThemeDialog = true })
        }

        SectionLabel("Conversation", font)
        SettingsGroup {
            SettingRow(Icons.Outlined.Swipe, "Swipe action", font, value = swipeAction.label, onClick = { showSwipeDialog = true })
        }

        SectionLabel("Send", font)
        SettingsGroup {
            SettingRow(
                Icons.Outlined.DoneAll, "Delivery reports", font,
                subtitle = "Request a delivery confirmation for sent SMS",
                toggle = deliveryReports, onToggle = vm::setDeliveryReports,
            )
        }

        SectionLabel("Notifications", font)
        SettingsGroup {
            SettingRow(
                Icons.Outlined.Notifications, "Notifications", font,
                subtitle = "Alerts & preview",
                onClick = onNotificationSettings,
            )
        }

        SectionLabel("Privacy & Data", font)
        SettingsGroup {
            SettingRow(Icons.Outlined.Lock, "App lock", font, subtitle = "PIN or biometric", onClick = onAppLock, divider = true)
            SettingRow(Icons.Outlined.Backup, "Backup & Restore", font, onClick = onBackup, divider = true)
            SettingRow(Icons.Outlined.PrivacyTip, "Privacy policy", font, onClick = onPrivacy)
        }



        Spacer(Modifier.height(24.dp))
    }

    if (showThemeDialog) {
        ChoiceDialog("Mode", ThemeMode.entries, themeMode, { it.label }, { vm.setThemeMode(it); showThemeDialog = false }, { showThemeDialog = false })
    }
    if (showSwipeDialog) {
        ChoiceDialog("Swipe action", SwipeAction.entries, swipeAction, { it.label }, { vm.setSwipeAction(it); showSwipeDialog = false }, { showSwipeDialog = false })
    }
}

/** Opens a URL in the browser (placeholder target for the privacy policy). */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun UpgradeBanner(font: androidx.compose.ui.text.font.FontFamily?, onUpgrade: () -> Unit) {
    // Fixed brand-blue gradient accent (same as the paywall best-value card) —
    // reads well in both light and dark, so it stays a constant.
    val onPill = Color(0xFF0057CC)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2C77E8), Color(0xFF0043A8))))
            .clickable(onClick = onUpgrade)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Upgrade to Plus", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 18.sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Remove ads, unlock scheduled messages, private folder & all themes",
                    fontFamily = font, fontWeight = FontWeight.W500, fontSize = 12.sp,
                    color = Color(0xCCFFFFFF), lineHeight = 16.sp,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .clickable(onClick = onUpgrade)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NorthEast, null, tint = onPill, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Upgrade", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 13.sp, color = onPill)
            }
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.System -> "System"
        ThemeMode.Light -> "Light"
        ThemeMode.Dark -> "Dark"
    }

private val SwipeAction.label: String
    get() = when (this) {
        SwipeAction.Archive -> "Archive"
        SwipeAction.Delete -> "Delete"
        SwipeAction.MarkRead -> "Mark as read"
    }

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, onClick = { onSelect(option) })
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
