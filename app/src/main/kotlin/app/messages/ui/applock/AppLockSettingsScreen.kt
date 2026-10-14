package app.messages.ui.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.ui.components.ScreenScaffold
import app.messages.ui.settings.SectionLabel
import app.messages.ui.settings.SettingRow
import app.messages.ui.settings.SettingsGroup
import app.messages.ui.theme.Spacing

/** App-lock setup + management (§F16). */
@Composable
fun AppLockSettingsScreen(
    onBack: () -> Unit,
    vm: AppLockSettingsViewModel = hiltViewModel(),
) {
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()
    val biometricEnabled by vm.biometricEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val biometricAvailable = remember(activity) {
        activity != null && BiometricAuth.canAuthenticate(activity)
    }
    var forceSetup by remember { mutableStateOf(false) }

    ScreenScaffold(title = "App lock", onBack = onBack) {
        if (!lockEnabled || forceSetup) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PinSetup(
                    onComplete = { pin ->
                        vm.setPin(pin)
                        forceSetup = false
                    },
                    onCancel = if (forceSetup) ({ forceSetup = false }) else null,
                )
            }
        } else {
            EnabledControls(
                biometricEnabled = biometricEnabled,
                biometricAvailable = biometricAvailable,
                onToggleBiometric = vm::setBiometric,
                onChangePin = { forceSetup = true },
                onDisable = vm::disable,
            )
        }
    }
}

@Composable
private fun PinSetup(onComplete: (String) -> Unit, onCancel: (() -> Unit)?) {
    var stage by remember { mutableIntStateOf(0) } // 0 = create, 1 = confirm
    var first by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (stage == 0) "Create a PIN" else "Re-enter to confirm",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            when {
                mismatch -> "PINs didn't match — start again"
                else -> "Choose a $PIN_LENGTH-digit PIN"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (mismatch) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            },
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(Spacing.xl))
        PinDots(filled = entry.length, error = mismatch)
        Spacer(Modifier.size(Spacing.xl))
        PinPad(
            onDigit = { d ->
                if (entry.length < PIN_LENGTH) {
                    mismatch = false
                    entry += d.toString()
                }
                if (entry.length == PIN_LENGTH) {
                    if (stage == 0) {
                        first = entry
                        entry = ""
                        stage = 1
                    } else {
                        if (entry == first) {
                            onComplete(first)
                        } else {
                            mismatch = true
                            entry = ""
                            first = ""
                            stage = 0
                        }
                    }
                }
            },
            onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
        )
        if (onCancel != null) {
            Spacer(Modifier.size(Spacing.lg))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun EnabledControls(
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onDisable: () -> Unit,
) {
    val font = MaterialTheme.typography.titleMedium.fontFamily
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        SectionLabel("Security", font)
        SettingsGroup {
            SettingRow(
                Icons.Outlined.Fingerprint, "Unlock with biometrics", font,
                subtitle = if (biometricAvailable) {
                    "Use fingerprint or face to unlock"
                } else {
                    "No biometrics enrolled on this device"
                },
                toggle = biometricEnabled && biometricAvailable,
                onToggle = onToggleBiometric,
                divider = true,
            )
            SettingRow(Icons.Outlined.Password, "Change PIN", font, onClick = onChangePin, divider = true)
            SettingRow(
                Icons.Outlined.LockOpen, "Turn off app lock", font,
                onClick = onDisable, destructive = true,
            )
        }
    }
}
