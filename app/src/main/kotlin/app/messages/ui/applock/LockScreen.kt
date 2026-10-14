package app.messages.ui.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.ui.theme.Spacing

/**
 * Full-screen lock gate (§F16). Opaque, so it covers the conversation list while
 * shown. Offers the PIN pad and — when enabled + available — auto-prompts for
 * biometrics, with a keypad button to re-trigger it.
 */
@Composable
fun LockScreen(vm: AppLockViewModel = hiltViewModel()) {
    val biometricEnabled by vm.biometricEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun tryBiometric() {
        val act = activity ?: return
        if (biometricEnabled && BiometricAuth.canAuthenticate(act)) {
            BiometricAuth.prompt(act, onSuccess = { vm.unlocked() })
        }
    }

    // Auto-offer biometrics when the lock first appears.
    LaunchedEffect(biometricEnabled) { tryBiometric() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(Spacing.lg))
            Text("Messages is locked", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(Spacing.xs))
            Text(
                if (error) "Wrong PIN, try again" else "Enter your PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
            )
            Spacer(Modifier.size(Spacing.xl))
            PinDots(filled = pin.length, error = error)
            Spacer(Modifier.size(Spacing.xl))
            PinPad(
                onDigit = { d ->
                    if (pin.length < PIN_LENGTH) {
                        error = false
                        pin += d.toString()
                    }
                    if (pin.length == PIN_LENGTH) {
                        val attempt = pin
                        vm.verifyPin(attempt) { ok ->
                            if (!ok) {
                                error = true
                                pin = ""
                            }
                        }
                    }
                },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                trailing = if (biometricEnabled) {
                    {
                        IconButton(onClick = { tryBiometric() }) {
                            Icon(
                                Icons.Outlined.Fingerprint,
                                contentDescription = "Use biometrics",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}
