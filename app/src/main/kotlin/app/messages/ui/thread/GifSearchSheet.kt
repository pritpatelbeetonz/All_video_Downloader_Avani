package app.messages.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.messages.data.gif.TenorClient
import app.messages.data.gif.TenorGif
import app.messages.ui.theme.Spacing
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class GifSearchViewModel @Inject constructor(
    private val tenor: TenorClient,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<TenorGif>>(emptyList())
    val results: StateFlow<List<TenorGif>> = _results

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        viewModelScope.launch {
            _query.debounce(350).collectLatest { q ->
                if (q.isBlank()) {
                    _results.value = emptyList()
                    return@collectLatest
                }
                _loading.value = true
                _results.value = tenor.search(q)
                _loading.value = false
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    suspend fun download(gif: TenorGif): ByteArray? = tenor.download(gif.gifUrl)
}

/**
 * Tenor GIF search (§F5). Search field + a result grid; tapping a GIF downloads
 * it and stages it as an `image/gif` attachment (sent raw, so it stays animated).
 * "Choose from device" falls back to the local picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifSearchSheet(
    onDismiss: () -> Unit,
    onPicked: (PendingAttachment) -> Unit,
    onPickFromDevice: () -> Unit,
    vm: GifSearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search GIFs") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            TextButton(onClick = onPickFromDevice) { Text("Choose from device instead") }

            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 460.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    downloading || loading -> CircularProgressIndicator()
                    results.isEmpty() && query.isNotBlank() ->
                        Hint("No GIFs found for \"$query\"")
                    results.isEmpty() ->
                        Hint("Search Tenor for the perfect GIF 🔍")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(vertical = Spacing.sm),
                    ) {
                        items(results, key = { it.id }) { gif ->
                            AsyncImage(
                                model = gif.previewUrl,
                                contentDescription = "GIF",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(Spacing.sm))
                                    .clickable {
                                        downloading = true
                                        scope.launch {
                                            val bytes = vm.download(gif)
                                            downloading = false
                                            if (bytes != null) {
                                                val uri = saveSharedBytes(
                                                    context, "gif-${System.currentTimeMillis()}.gif", bytes,
                                                )
                                                onPicked(
                                                    PendingAttachment(uri, "image/gif", AttachmentKind.Gif),
                                                )
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(Spacing.xl),
    )
}
