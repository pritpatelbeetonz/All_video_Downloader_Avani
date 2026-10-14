package app.messages.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.data.export.ExportRepository
import app.messages.monetization.ads.AdaptiveBanner
import app.messages.ui.components.ScreenScaffold
import app.messages.ui.settings.SectionLabel
import app.messages.ui.settings.SettingRow
import app.messages.ui.settings.SettingsGroup
import app.messages.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backup / Restore / Export (§F14/§F15). */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onPremium: () -> Unit,
    vm: BackupViewModel = hiltViewModel(),
) {
    val isPremium by vm.isPremium.collectAsStateWithLifecycle()
    val showBanner by vm.showBanner.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val font = MaterialTheme.typography.titleMedium.fontFamily

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    var pendingPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var showBackupPass by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val p = pendingPassphrase
        if (uri != null && p != null) vm.backup(uri, p)
        pendingPassphrase = null
    }
    val openRestore = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) restoreUri = uri }
    val exportTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) vm.export(uri, ExportRepository.Format.TXT) }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) vm.export(uri, ExportRepository.Format.CSV) }
    val exportPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> if (uri != null) vm.export(uri, ExportRepository.Format.PDF) }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenScaffold(title = "Backup & Restore", onBack = onBack) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                SectionLabel("Backup", font)
                SettingsGroup {
                    SettingRow(
                        Icons.Outlined.CloudUpload, "Back up now", font,
                        subtitle = "Encrypted backup to a file you choose",
                        onClick = { showBackupPass = true }, divider = true,
                    )
                    SettingRow(
                        Icons.Outlined.CloudDownload, "Restore from backup", font,
                        subtitle = "Import messages from a backup file",
                        onClick = { openRestore.launch(arrayOf("application/octet-stream", "*/*")) },
                    )
                }

                SectionLabel("Export", font)
                SettingsGroup {
                    SettingRow(
                        Icons.Outlined.Description, "Export as TXT", font,
                        subtitle = "Plain-text transcript",
                        onClick = { exportTxt.launch("messages.txt") }, divider = true,
                    )
                    SettingRow(
                        Icons.Outlined.TableChart, "Export as CSV", font,
                        subtitle = "Spreadsheet-friendly",
                        onClick = { exportCsv.launch("messages.csv") }, divider = true,
                    )
                    SettingRow(
                        Icons.Outlined.PictureAsPdf, "Export as PDF", font,
                        subtitle = "Formatted document",
                        onClick = { exportPdf.launch("messages.pdf") },
                    )
                }

                SectionLabel("Automatic", font)
                SettingsGroup {
                    SettingRow(
                        Icons.Outlined.Schedule, "Daily auto-backup", font,
                        subtitle = "Back up to your Google Drive",
                        onClick = { /* TODO: implement auto-backup */ },
                    )
                }

                if (showBanner) {
                    Spacer(Modifier.height(Spacing.lg))
                    AdaptiveBanner(unitId = vm.bannerUnitId)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))

        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {}
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    if (showBackupPass) {
        PassphraseDialog(
            title = "Set a backup password",
            confirmAction = "Back up",
            requireConfirmField = true,
            onDismiss = { showBackupPass = false },
            onConfirm = { pass ->
                pendingPassphrase = pass
                showBackupPass = false
                createBackup.launch(backupFileName())
            },
        )
    }
    restoreUri?.let { uri ->
        PassphraseDialog(
            title = "Enter the backup password",
            confirmAction = "Restore",
            requireConfirmField = false,
            onDismiss = { restoreUri = null },
            onConfirm = { pass ->
                vm.restore(uri, pass)
                restoreUri = null
            },
        )
    }
}

private fun backupFileName(): String =
    "messages-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".msgbak"

@Composable
private fun PassphraseDialog(
    title: String,
    confirmAction: String,
    requireConfirmField: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val valid = p1.length >= 4 && (!requireConfirmField || p1 == p2)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = p1,
                    onValueChange = { p1 = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (requireConfirmField) {
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = p2,
                        onValueChange = { p2 = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (p2.isNotEmpty() && p1 != p2) {
                        Text(
                            "Passwords don't match",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "At least 4 characters. You'll need this exact password to restore — it can't be recovered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(p1.toCharArray()) }) {
                Text(confirmAction)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
