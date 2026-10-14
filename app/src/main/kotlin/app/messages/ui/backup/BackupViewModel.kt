package app.messages.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.backup.BackupRepository
import app.messages.data.export.ExportRepository
import app.messages.monetization.ads.AdsController
import app.messages.monetization.iap.EntitlementController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Backs the Backup/Restore/Export screen (§F14/§F15). */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val export: ExportRepository,
    entitlement: EntitlementController,
    adsController: AdsController,
    private val remoteConfig: app.messages.monetization.ads.AdsRemoteConfig,
) : ViewModel() {

    val bannerUnitId: String get() = remoteConfig.bannerUnitId

    val isPremium: StateFlow<Boolean> = entitlement.isPremium
    val showBanner: StateFlow<Boolean> = adsController.bannerAdsEnabled

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun backup(uri: Uri, passphrase: CharArray) = run {
        _busy.value = true
        viewModelScope.launch {
            backup.backupTo(uri, passphrase)
                .onSuccess { _events.tryEmit("Backed up ${it.messageCount} messages") }
                .onFailure { _events.tryEmit("Backup failed: ${it.message}") }
            _busy.value = false
        }
    }

    fun restore(uri: Uri, passphrase: CharArray) = run {
        _busy.value = true
        viewModelScope.launch {
            backup.restoreFrom(uri, passphrase)
                .onSuccess {
                    _events.tryEmit("Restored ${it.imported} messages (${it.skipped} already present)")
                }
                .onFailure { _events.tryEmit("Restore failed — wrong password or corrupt file") }
            _busy.value = false
        }
    }

    fun export(uri: Uri, format: ExportRepository.Format) = run {
        _busy.value = true
        viewModelScope.launch {
            export.export(uri, format, threadId = null, title = "All messages")
                .onSuccess { _events.tryEmit("Exported $it messages") }
                .onFailure { _events.tryEmit("Export failed: ${it.message}") }
            _busy.value = false
        }
    }
}
