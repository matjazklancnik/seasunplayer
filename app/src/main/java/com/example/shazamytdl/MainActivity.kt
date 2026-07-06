package com.example.shazamytdl

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.shazamytdl.data.Track
import com.example.shazamytdl.data.TrackRepository
import com.example.shazamytdl.data.TrackStatus
import com.example.shazamytdl.download.DownloadQueueEvent
import com.example.shazamytdl.download.DownloadQueueManager
import com.example.shazamytdl.download.YoutubeDlBridge
import com.example.shazamytdl.importer.ShazamCsvImporter
import com.example.shazamytdl.player.PlayerHolder
import com.example.shazamytdl.ui.theme.ShazamYtdlTheme
import com.example.shazamytdl.util.stableTrackId
import com.example.shazamytdl.youtube.YouTubeApiClient
import com.example.shazamytdl.youtube.YouTubePlaylist
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playerHolder = (application as ShazamYtdlApp).playerHolder
        setContent {
            ShazamYtdlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(playerHolder = playerHolder)
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    repository: TrackRepository = TrackRepository(LocalContext.current),
    importer: ShazamCsvImporter = ShazamCsvImporter(LocalContext.current),
    playerHolder: PlayerHolder? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val progressByTrack by DownloadQueueManager.progress.collectAsState()

    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var searchQuery by remember { mutableStateOf("") }
    var errorToShow by remember { mutableStateOf<String?>(null) }
    var trackToEditSource by remember { mutableStateOf<Track?>(null) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var isYouTubeLoading by remember { mutableStateOf(false) }
    var youtubeAccessToken by remember { mutableStateOf<String?>(null) }
    var youtubePlaylists by remember { mutableStateOf<List<YouTubePlaylist>?>(null) }
    var selectedYoutubePlaylistIds by remember { mutableStateOf(emptySet<String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var playingTrackId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playerPositionMs by remember { mutableStateOf(0L) }
    var playerDurationMs by remember { mutableStateOf(0L) }

    val visibleTracks = remember(tracks, searchQuery) {
        val query = searchQuery.normalizedForSearch()
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter { track ->
                track.title.normalizedForSearch().contains(query) ||
                    track.artist.normalizedForSearch().contains(query)
            }
        }
    }

    LaunchedEffect(playerHolder) {
        while (isActive) {
            playerHolder?.let { holder ->
                playingTrackId = holder.currentTrackId.value
                isPlaying = holder.isPlaying
                playerPositionMs = holder.currentPosition
                playerDurationMs = holder.duration
            }
            delay(250)
        }
    }

    fun refreshTracks() {
        tracks = repository.list()
    }

    fun loadYouTubePlaylists(authorizationResult: AuthorizationResult) {
        val accessToken = authorizationResult.accessToken
        if (accessToken.isNullOrBlank()) {
            youtubeAccessToken = null
            isYouTubeLoading = false
            message = "Google ni vrnil dostopnega žetona."
            return
        }
        youtubeAccessToken = accessToken
        scope.launch(Dispatchers.IO) {
            runCatching {
                YouTubeApiClient().listMyPlaylists(accessToken)
            }.onSuccess { playlists ->
                withContext(Dispatchers.Main) {
                    isYouTubeLoading = false
                    if (playlists.isEmpty()) {
                        youtubeAccessToken = null
                        message = "V izbranem YouTube računu ni seznamov predvajanja."
                    } else {
                        selectedYoutubePlaylistIds = emptySet()
                        youtubePlaylists = playlists
                    }
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    youtubeAccessToken = null
                    isYouTubeLoading = false
                    message = "YouTube API napaka: ${error.message}"
                }
            }
        }
    }

    val youtubeAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        runCatching {
            Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(activityResult.data)
        }.onSuccess(::loadYouTubePlaylists)
            .onFailure { error ->
                youtubeAccessToken = null
                isYouTubeLoading = false
                message = "YouTube avtorizacija ni uspela: ${error.message}"
            }
    }

    fun authorizeYouTube() {
        val activity = context as? ComponentActivity
        if (activity == null) {
            message = "YouTube avtorizacije v tem kontekstu ni mogoče odpreti."
            return
        }
        isYouTubeLoading = true
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YouTubeApiClient.READ_ONLY_SCOPE)))
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener(activity) { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        youtubeAccessToken = null
                        isYouTubeLoading = false
                        message = "Google ni vrnil avtorizacijskega okna."
                    } else {
                        youtubeAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    loadYouTubePlaylists(result)
                }
            }
            .addOnFailureListener(activity) { error ->
                youtubeAccessToken = null
                isYouTubeLoading = false
                message = "YouTube avtorizacija ni uspela: ${error.message}"
            }
    }

    LaunchedEffect(Unit) {
        refreshTracks()
        launch(Dispatchers.IO) {
            YoutubeDlBridge.updateYoutubeDL(context)
        }
    }

    LaunchedEffect(Unit) {
        DownloadQueueManager.events.collect { event ->
            refreshTracks()
            message = when (event) {
                is DownloadQueueEvent.Completed -> "Preneseno: ${event.fileName}"
                is DownloadQueueEvent.Failed -> "Download napaka: ${event.message}"
                is DownloadQueueEvent.StatusChanged -> null
            }
        }
    }


    message?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            message = null
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                val imported = importer.import(uri)
                repository.upsertAll(imported)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    refreshTracks()
                    message = if (result.added == 0 && result.existing == 0) {
                        "V datoteki ni bilo prepoznanih skladb."
                    } else {
                        "Dodanih: ${result.added}, že na seznamu: ${result.existing}"
                    }
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    message = "CSV import napaka: ${error.message}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "SunSea Player",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { csvLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel")) },
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text("CSV", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text("Dodaj", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = ::authorizeYouTube,
                enabled = !isYouTubeLoading,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                if (isYouTubeLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("YouTube", fontSize = 12.sp)
                }
            }
            IconButton(
                onClick = { showClearDialog = true },
                enabled = tracks.isNotEmpty() && !isClearing,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Počisti seznam in prenesene datoteke",
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            placeholder = { Text("Išči po naslovu ali izvajalcu") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Počisti iskanje")
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))

        if (tracks.isEmpty()) {
            EmptyState()
        } else if (visibleTracks.isEmpty()) {
            Text(
                text = "Ni skladb, ki ustrezajo iskanju.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleTracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        onSelect = if (searchQuery.isNotBlank()) {
                            { searchQuery = "" }
                        } else {
                            null
                        },
                        progressText = progressByTrack[track.id],
                        onDownload = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                            scope.launch(Dispatchers.IO) {
                                val added = DownloadQueueManager.enqueue(track.id)
                                withContext(Dispatchers.Main) {
                                    refreshTracks()
                                    if (!added) {
                                        message = "Skladba je že v čakalni vrsti."
                                    }
                                }
                            }
                        },
                        onPlay = {
                            val path = track.localPath
                            if (path.isNullOrBlank()) {
                                message = "Najprej prenesi ali poveži lokalno datoteko."
                            } else {
                                runCatching {
                                    playerHolder?.togglePlayback(
                                        track.id,
                                        path,
                                        track.title,
                                        track.artist,
                                        track.artworkPath
                                    )
                                }
                                    .onFailure { message = "Player napaka: ${it.message}" }
                            }
                        },
                        isActive = playingTrackId == track.id,
                        isPlaying = playingTrackId == track.id && isPlaying,
                        playerPositionMs = if (playingTrackId == track.id) {
                            playerPositionMs
                        } else {
                            playerHolder?.positionFor(track.id) ?: 0L
                        },
                        playerDurationMs = if (playingTrackId == track.id) playerDurationMs else 0L,
                        onSeek = { positionMs ->
                            val path = track.localPath
                            if (!path.isNullOrBlank()) {
                                runCatching {
                                    if (playingTrackId != track.id) {
                                        playerHolder?.playLocalFile(
                                            track.id,
                                            path,
                                            track.title,
                                            track.artist,
                                            track.artworkPath
                                        )
                                        playerHolder?.pause()
                                    }
                                    playerHolder?.seekTo(positionMs)
                                }.onFailure { message = "Player napaka: " + it.message }
                            }
                        },
                        onStop = { playerHolder?.stop() },
                        onEditSource = { trackToEditSource = track },
                        onPromote = {
                            val promoted = DownloadQueueManager.promote(track.id)
                            message = if (promoted) {
                                "Skladba bo prenesena naslednja."
                            } else {
                                "Skladbe ni bilo mogoče premakniti v vrsti."
                            }
                        },
                        onDelete = { trackToDelete = track },
                        onShowError = {
                            errorToShow = it
                        }
                    )
                }
            }
        }
    }

    errorToShow?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { errorToShow = null },
            title = { Text("Podrobnosti napake") },
            text = {
                Column {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Napaka", errorMsg)
                    clipboard.setPrimaryClip(clip)
                    message = "Napaka kopirana v odložišče"
                    errorToShow = null
                }) { Text("Kopiraj") }
            },
            dismissButton = {
                TextButton(onClick = { errorToShow = null }) { Text("Zapri") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Počisti celoten seznam?") },
            text = {
                Text(
                    "Izbrisane bodo vse skladbe s seznama in vse zvočne datoteke, " +
                        "ki jih je prenesla aplikacija. Tega ni mogoče razveljaviti."
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        showClearDialog = false
                        isClearing = true
                        playerHolder?.stop()
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                DownloadQueueManager.invalidateForLibraryClear()
                                repository.clearAll()
                                val musicDir = File(context.filesDir, "music")
                                check(!musicDir.exists() || musicDir.deleteRecursively()) {
                                    "Vseh prenesenih datotek ni bilo mogoče izbrisati."
                                }
                            }.onSuccess {
                                withContext(Dispatchers.Main) {
                                    refreshTracks()
                                    isClearing = false
                                    message = "Seznam in prenesene zvočne datoteke so izbrisane."
                                }
                            }.onFailure { error ->
                                withContext(Dispatchers.Main) {
                                    refreshTracks()
                                    isClearing = false
                                    message = "Napaka pri čiščenju: ${error.message}"
                                }
                            }
                        }
                    }
                ) { Text("Izbriši vse") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Prekliči") }
            }
        )
    }

    youtubePlaylists?.let { playlists ->
        YouTubePlaylistDialog(
            playlists = playlists,
            selectedIds = selectedYoutubePlaylistIds,
            onSelectedIdsChange = { selectedYoutubePlaylistIds = it },
            onDismiss = {
                youtubePlaylists = null
                youtubeAccessToken = null
            },
            onImport = {
                val token = youtubeAccessToken
                val selected = playlists.filter { it.id in selectedYoutubePlaylistIds }
                youtubePlaylists = null
                if (token.isNullOrBlank() || selected.isEmpty()) {
                    youtubeAccessToken = null
                    return@YouTubePlaylistDialog
                }

                isYouTubeLoading = true
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val client = YouTubeApiClient()
                        val importedTracks = selected.flatMap { playlist ->
                            runCatching {
                                client.listPlaylistTracks(token, playlist.id)
                            }.getOrElse { error ->
                                throw IllegalStateException("${playlist.title}: ${error.message}", error)
                            }
                        }
                        repository.upsertAll(importedTracks)
                    }.onSuccess { result ->
                        withContext(Dispatchers.Main) {
                            refreshTracks()
                            youtubeAccessToken = null
                            isYouTubeLoading = false
                            message = "YouTube uvoz: dodanih ${result.added}, " +
                                "že obstoječih ${result.existing}."
                        }
                    }.onFailure { error ->
                        withContext(Dispatchers.Main) {
                            youtubeAccessToken = null
                            isYouTubeLoading = false
                            message = "YouTube uvoz ni uspel: ${error.message}"
                        }
                    }
                }
            }
        )
    }


    if (showAddDialog) {
        AddTrackDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { artist, title ->
                repository.upsert(
                    Track(
                        id = stableTrackId(title, artist),
                        title = title.trim(),
                        artist = artist.trim(),
                        status = TrackStatus.NEW
                    )
                )
                refreshTracks()
                showAddDialog = false
            }
        )
    }

    trackToEditSource?.let { track ->
        SourceUrlDialog(
            track = track,
            onDismiss = { trackToEditSource = null },
            onSave = { url ->
                repository.updateSourceUrl(track.id, url)
                refreshTracks()
                trackToEditSource = null
                message = if (url == null) {
                    "Vir ponastavljen na samodejno YouTube iskanje."
                } else {
                    "YouTube vir je shranjen. Za uporabo novega vira znova prenesi skladbo."
                }
            }
        )
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Odstrani skladbo?") },
            text = {
                Text(
                    "${track.artist} – ${track.title}\n\n" +
                        "Izbrisana bo tudi njena lokalna zvočna datoteka."
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        trackToDelete = null
                        if (playingTrackId == track.id) playerHolder?.stop()
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                check(DownloadQueueManager.removeQueued(track.id)) {
                                    "Aktivnega prenosa ni mogoče odstraniti. Počakaj na zaključek ali timeout."
                                }
                                val trackDir = File(File(context.filesDir, "music"), track.id)
                                check(!trackDir.exists() || trackDir.deleteRecursively()) {
                                    "Lokalne datoteke ni bilo mogoče izbrisati."
                                }
                                repository.delete(track.id)
                            }.onSuccess {
                                withContext(Dispatchers.Main) {
                                    refreshTracks()
                                    message = "Skladba je odstranjena."
                                }
                            }.onFailure { error ->
                                withContext(Dispatchers.Main) {
                                    refreshTracks()
                                    message = "Napaka pri odstranjevanju: ${error.message}"
                                }
                            }
                        }
                    }
                ) { Text("Odstrani") }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) { Text("Prekliči") }
            }
        )
    }
}

@Composable
private fun YouTubePlaylistDialog(
    playlists: List<YouTubePlaylist>,
    selectedIds: Set<String>,
    onSelectedIdsChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    val allSelected = playlists.isNotEmpty() && selectedIds.size == playlists.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Izberi YouTube sezname") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Izbranih: ${selectedIds.size}/${playlists.size}")
                    TextButton(
                        onClick = {
                            onSelectedIdsChange(
                                if (allSelected) emptySet() else playlists.map { it.id }.toSet()
                            )
                        }
                    ) {
                        Text(if (allSelected) "Počisti izbor" else "Izberi vse")
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val selected = playlist.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectedIdsChange(
                                        if (selected) selectedIds - playlist.id
                                        else selectedIds + playlist.id
                                    )
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    onSelectedIdsChange(
                                        if (checked) selectedIds + playlist.id
                                        else selectedIds - playlist.id
                                    )
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val privacy = when (playlist.privacyStatus) {
                                    "private" -> "zasebno"
                                    "unlisted" -> "nenavedeno"
                                    "public" -> "javno"
                                    else -> playlist.privacyStatus
                                }
                                Text(
                                    text = "${playlist.itemCount} videov" +
                                        privacy.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedIds.isNotEmpty(),
                onClick = onImport
            ) {
                Text("Uvozi izbrane")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Prekliči") }
        }
    )
}

@Composable
private fun EmptyState() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ni še skladb.", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Uvozi Shazam CSV, YouTube/Google Takeout CSV ali dodaj skladbo ročno.")
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    onSelect: (() -> Unit)?,
    progressText: String?,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    isActive: Boolean,
    isPlaying: Boolean,
    playerPositionMs: Long,
    playerDurationMs: Long,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onEditSource: () -> Unit,
    onPromote: () -> Unit,
    onDelete: () -> Unit,
    onShowError: (String) -> Unit
) {
    var fileDurationMs by remember(track.localPath) { mutableStateOf(0L) }
    var draggedPositionMs by remember(track.id) { mutableStateOf<Long?>(null) }

    LaunchedEffect(track.localPath) {
        fileDurationMs = withContext(Dispatchers.IO) {
            val path = track.localPath ?: return@withContext 0L
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    retriever.release()
                }
            }.getOrDefault(0L)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(track.artworkPath)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!track.localPath.isNullOrBlank()) {
                    IconButton(onClick = onPlay) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusText = when (track.status) {
                    TrackStatus.DOWNLOADED -> "Shranjeno lokalno"
                    TrackStatus.QUEUED -> "V čakalni vrsti"
                    TrackStatus.DOWNLOADING -> "Prenašanje..."
                    TrackStatus.ERROR -> "Napaka"
                    else -> "Ni prenešeno"
                }

                if (track.status == TrackStatus.DOWNLOADED) {
                    Icon(
                        imageVector = Icons.Default.OfflinePin,
                        contentDescription = "Offline",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Text(
                    text = progressText ?: statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (track.status == TrackStatus.ERROR) MaterialTheme.colorScheme.error else Color.Unspecified
                )

                if (track.status == TrackStatus.ERROR && track.lastError != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onShowError(track.lastError) }) {
                        Text("Podrobnosti", fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!track.localPath.isNullOrBlank()) {
                val durationMs = if (isActive && playerDurationMs > 0) playerDurationMs else fileDurationMs
                val positionMs = draggedPositionMs ?: playerPositionMs
                Slider(
                    value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                    onValueChange = { draggedPositionMs = it.toLong() },
                    onValueChangeFinished = {
                        draggedPositionMs?.let(onSeek)
                        draggedPositionMs = null
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    enabled = durationMs > 0,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatTime(positionMs) + " / " + formatTime(durationMs), style = MaterialTheme.typography.bodySmall)
                    Row {
                        IconButton(onClick = onPlay) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pavza" else "Predvajaj"
                            )
                        }
                        IconButton(onClick = onStop, enabled = isActive) {
                            Icon(Icons.Default.Stop, contentDescription = "Ustavi")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEditSource,
                    enabled = track.status != TrackStatus.QUEUED &&
                        track.status != TrackStatus.DOWNLOADING,
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vir", fontSize = 12.sp)
                }
                if (track.status == TrackStatus.QUEUED) {
                    TextButton(
                        onClick = onPromote,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF9800)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("Naslednja", fontSize = 12.sp)
                    }
                }
                IconButton(
                    onClick = onDelete,
                    enabled = track.status != TrackStatus.DOWNLOADING,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Odstrani skladbo",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                if (track.status != TrackStatus.DOWNLOADED &&
                    track.status != TrackStatus.QUEUED &&
                    track.status != TrackStatus.DOWNLOADING
                ) {
                    Button(
                        onClick = onDownload,
                        enabled = track.status != TrackStatus.QUEUED && track.status != TrackStatus.DOWNLOADING,
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Prenesi", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private val diacriticsRegex = "\\p{Mn}+".toRegex()

private fun String.normalizedForSearch(): String = Normalizer
    .normalize(this, Normalizer.Form.NFD)
    .replace(diacriticsRegex, "")
    .lowercase(Locale.ROOT)

@Composable
private fun TrackArtwork(path: String?) {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(path) {
        image = withContext(Dispatchers.IO) {
            path?.let { artworkPath ->
                runCatching { BitmapFactory.decodeFile(artworkPath)?.asImageBitmap() }
                    .getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val loadedImage = image
        if (loadedImage != null) {
            Image(
                bitmap = loadedImage,
                contentDescription = "Naslovnica",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Album,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceUrlDialog(
    track: Track,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var url by remember(track.id, track.sourceUrl) { mutableStateOf(track.sourceUrl.orEmpty()) }
    val trimmedUrl = url.trim()
    val isValid = trimmedUrl.isBlank() || isYoutubeUrl(trimmedUrl)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("YouTube vir") },
        text = {
            Column {
                Text(
                    "Prilepi URL želenega YouTube posnetka. Prazno polje uporabi prvi rezultat " +
                        "samodejnega iskanja.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("YouTube URL") },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") },
                    singleLine = true,
                    isError = !isValid,
                    supportingText = if (!isValid) {
                        { Text("Vnesi veljaven youtube.com ali youtu.be URL.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = { onSave(trimmedUrl.takeIf { it.isNotBlank() }) }
            ) {
                Text("Shrani")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Prekliči") }
        }
    )
}

private fun isYoutubeUrl(value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    if (uri.scheme != "https" && uri.scheme != "http") return false
    val host = uri.host?.lowercase() ?: return false
    return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}


@Composable
private fun AddTrackDialog(onDismiss: () -> Unit, onAdd: (artist: String, title: String) -> Unit) {
    var artist by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    val canSave = artist.isNotBlank() && title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj skladbo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(enabled = canSave, onClick = { onAdd(artist, title) }) { Text("Dodaj") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Prekliči") }
        }
    )
}
