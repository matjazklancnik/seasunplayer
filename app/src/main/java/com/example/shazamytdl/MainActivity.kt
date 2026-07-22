package com.example.shazamytdl

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.shazamytdl.data.Track
import com.example.shazamytdl.data.TrackRepository
import com.example.shazamytdl.data.TrackStatus
import com.example.shazamytdl.download.DownloadErrorFormatter
import com.example.shazamytdl.download.DownloadQueueEvent
import com.example.shazamytdl.download.DownloadQueueManager
import com.example.shazamytdl.download.YoutubeDlBridge
import com.example.shazamytdl.download.YouTubeSearchResult
import com.example.shazamytdl.importer.ShazamCsvImporter
import com.example.shazamytdl.player.PlaybackService
import com.example.shazamytdl.player.PlayerHolder
import com.example.shazamytdl.recognition.AudioSampleRecorder
import com.example.shazamytdl.recognition.SongRecognitionClient
import com.example.shazamytdl.recognition.SongRecognitionResult
import com.example.shazamytdl.recognition.SongRecognitionSettings
import com.example.shazamytdl.ui.theme.AppVisualStyle
import com.example.shazamytdl.ui.theme.ShazamYtdlTheme
import com.example.shazamytdl.ui.theme.appBackgroundBrush
import com.example.shazamytdl.util.stableTrackId
import com.example.shazamytdl.youtube.YouTubeApiClient
import com.example.shazamytdl.youtube.YouTubePlaylist
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val playerHolder: PlayerHolder
        get() = (application as ShazamYtdlApp).playerHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appPlayerHolder = playerHolder
        setContent {
            val appearancePreferences = remember {
                getSharedPreferences("appearance", Context.MODE_PRIVATE)
            }
            var visualStyle by remember {
                mutableStateOf(
                    AppVisualStyle.fromStorage(
                        appearancePreferences.getString("visual_style", null)
                    )
                )
            }
            ShazamYtdlTheme(style = visualStyle) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    MainScreen(
                        playerHolder = appPlayerHolder,
                        visualStyle = visualStyle,
                        onVisualStyleChange = { selected ->
                            visualStyle = selected
                            appearancePreferences.edit {
                                putString("visual_style", selected.storageKey)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            playerHolder.stop()
            stopService(Intent(this, PlaybackService::class.java))
        }
        super.onDestroy()
    }
}

@Composable
private fun MainScreen(
    repository: TrackRepository = TrackRepository(LocalContext.current),
    importer: ShazamCsvImporter = ShazamCsvImporter(LocalContext.current),
    playerHolder: PlayerHolder? = null,
    visualStyle: AppVisualStyle = AppVisualStyle.SUNSET,
    onVisualStyleChange: (AppVisualStyle) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var isYouTubeLoading by remember { mutableStateOf(false) }
    var youtubeAccessToken by remember { mutableStateOf<String?>(null) }
    var youtubePlaylists by remember { mutableStateOf<List<YouTubePlaylist>?>(null) }
    var selectedYoutubePlaylistIds by remember { mutableStateOf(emptySet<String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var playingTrackId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playerPositionMs by remember { mutableLongStateOf(0L) }
    var playerDurationMs by remember { mutableLongStateOf(0L) }
    var playerQueueIndex by remember { mutableIntStateOf(0) }
    var playerQueueSize by remember { mutableIntStateOf(0) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var youtubeSearchResults by remember { mutableStateOf(emptyList<YouTubeSearchResult>()) }
    var isYoutubeSearchLoading by remember { mutableStateOf(false) }
    var youtubeSearchCompleted by remember { mutableStateOf(false) }
    var youtubeSearchError by remember { mutableStateOf<String?>(null) }
    var youtubeSearchRequested by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var isSongListening by remember { mutableStateOf(false) }
    val defaultSongRecognitionEndpoint = remember {
        BuildConfig.DEFAULT_RECOGNITION_ENDPOINT.trim()
    }
    var songRecognitionEndpoint by remember {
        mutableStateOf(
            SongRecognitionSettings.endpoint(context)
                ?: defaultSongRecognitionEndpoint
        )
    }
    var songRecognitionApiToken by remember {
        mutableStateOf(
            SongRecognitionSettings.auddApiToken(context)
                ?: SongRecognitionSettings.DEFAULT_AUDD_API_TOKEN
        )
    }
    var showSongRecognitionSettings by remember { mutableStateOf(false) }
    var songRecognitionJob by remember { mutableStateOf<Job?>(null) }
    var songRecognitionResult by remember { mutableStateOf<SongRecognitionResult?>(null) }
    var songRecognitionSearchResults by remember { mutableStateOf(emptyList<YouTubeSearchResult>()) }
    var isSongRecognitionSearchLoading by remember { mutableStateOf(false) }
    var songRecognitionSearchCompleted by remember { mutableStateOf(false) }
    var songRecognitionSearchError by remember { mutableStateOf<String?>(null) }
    var videoPreview by rememberSaveable(stateSaver = YouTubeVideoPreviewSaver) {
        mutableStateOf<YouTubeVideoPreview?>(null)
    }
    var videoPreviewFullscreen by rememberSaveable { mutableStateOf(false) }
    var videoPreviewLoadingTrackId by remember { mutableStateOf<String?>(null) }
    var videoPreviewPositionMs by rememberSaveable { mutableStateOf(0L) }
    var videoPreviewIsPlaying by rememberSaveable { mutableStateOf(false) }

    fun deleteVideoPreview(path: String?) {
        if (path == null) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                file.parentFile?.deleteRecursively() ?: file.delete()
            }
        }
    }

    fun clearVideoPreview(
        syncPlayback: Boolean = true,
        resumePlayback: Boolean = false
    ) {
        val preview = videoPreview
        val oldPath = preview?.localVideoPath
        val shouldResumePlayback = resumePlayback &&
            syncPlayback &&
            preview != null &&
            playingTrackId == preview.trackId
        if (syncPlayback && preview != null && playingTrackId == preview.trackId) {
            playerHolder?.seekTo(videoPreviewPositionMs)
            playerPositionMs = videoPreviewPositionMs
        }
        videoPreview = null
        videoPreviewFullscreen = false
        videoPreviewLoadingTrackId = null
        videoPreviewPositionMs = 0L
        videoPreviewIsPlaying = false
        if (shouldResumePlayback) {
            playerHolder?.play()
            isPlaying = true
        }
        deleteVideoPreview(oldPath)
    }

    val voiceSearchController = remember(context) {
        VoiceSearchController(
            context = context,
            onResult = {
                searchQuery = it
                youtubeSearchRequested = false
            },
            onListeningChanged = { isVoiceListening = it },
            onErrorMessage = { message = it }
        )
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceSearchController.startListening()
        } else {
            message = "Za glasovno iskanje dovoli uporabo mikrofona."
        }
    }

    fun clearSongRecognition() {
        songRecognitionResult = null
        songRecognitionSearchResults = emptyList()
        songRecognitionSearchError = null
        songRecognitionSearchCompleted = false
        isSongRecognitionSearchLoading = false
    }

    fun startSongRecognition() {
        val endpoint = SongRecognitionSettings.normalizedEndpoint(songRecognitionEndpoint)
        if (songRecognitionEndpoint.isNotBlank() && endpoint == null) {
            showSongRecognitionSettings = true
            message = "Custom endpoint za prepoznavanje ni veljaven."
            return
        }
        if (isSongListening) return

        voiceSearchController.cancel()
        songRecognitionJob?.cancel()
        songRecognitionJob = scope.launch {
            var sample: File? = null
            try {
                isSongListening = true
                clearSongRecognition()
                message = "Poslušam skladbo..."
                val recordedSample = AudioSampleRecorder.record(context)
                sample = recordedSample
                val recognized = withContext(Dispatchers.IO) {
                    if (endpoint != null) {
                        SongRecognitionClient.recognize(endpoint, recordedSample)
                    } else {
                        SongRecognitionClient.recognize(recordedSample, songRecognitionApiToken)
                    }
                }
                songRecognitionResult = recognized
                searchQuery = recognized.searchQuery
                youtubeSearchRequested = false
                message = "Prepoznano: ${recognized.artist} - ${recognized.title}"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                message = "Prepoznavanje ni uspelo: ${error.message ?: error}"
            } finally {
                sample?.delete()
                isSongListening = false
                songRecognitionJob = null
            }
        }
    }

    val songRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSongRecognition()
        } else {
            message = "Za slušno prepoznavanje dovoli uporabo mikrofona."
        }
    }

    DisposableEffect(voiceSearchController) {
        onDispose { voiceSearchController.destroy() }
    }

    LaunchedEffect(voiceSearchController) {
        voiceSearchController.prepareLanguageModels()
    }

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

    LaunchedEffect(searchQuery, visibleTracks.isEmpty(), youtubeSearchRequested) {
        youtubeSearchResults = emptyList()
        youtubeSearchError = null
        youtubeSearchCompleted = false
        val query = searchQuery.trim()
        if (query.length < 2 || (visibleTracks.isNotEmpty() && !youtubeSearchRequested)) {
            isYoutubeSearchLoading = false
            return@LaunchedEffect
        }

        isYoutubeSearchLoading = true
        try {
            delay(600)
            val results = withContext(Dispatchers.IO) {
                YoutubeDlBridge.searchYouTube(context, query)
            }
            youtubeSearchResults = results
            youtubeSearchCompleted = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            youtubeSearchError = error.message ?: error.toString()
            youtubeSearchCompleted = true
        } finally {
            isYoutubeSearchLoading = false
        }
    }

    LaunchedEffect(songRecognitionResult) {
        val recognized = songRecognitionResult
        songRecognitionSearchResults = emptyList()
        songRecognitionSearchError = null
        songRecognitionSearchCompleted = false
        if (recognized == null) {
            isSongRecognitionSearchLoading = false
            return@LaunchedEffect
        }

        val directResult = recognized.youtubeVideoId?.let { videoId ->
            YouTubeSearchResult(
                videoId = videoId,
                title = recognized.title,
                channel = recognized.artist,
                url = "https://www.youtube.com/watch?v=$videoId",
                durationSeconds = null
            )
        }

        isSongRecognitionSearchLoading = true
        try {
            val results = withContext(Dispatchers.IO) {
                YoutubeDlBridge.searchYouTube(context, recognized.searchQuery)
            }
            songRecognitionSearchResults = (listOfNotNull(directResult) + results)
                .distinctBy { it.videoId }
                .take(5)
            songRecognitionSearchCompleted = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            songRecognitionSearchError = error.message ?: error.toString()
            songRecognitionSearchCompleted = true
        } finally {
            isSongRecognitionSearchLoading = false
        }
    }

    LaunchedEffect(playerHolder, videoPreview?.trackId) {
        while (isActive) {
            playerHolder?.let { holder ->
                val currentId = holder.currentTrackId.value
                val activePreview = videoPreview?.takeIf { it.trackId == currentId }
                playingTrackId = currentId
                if (activePreview != null) {
                    if (holder.isPlaying) holder.pause()
                    isPlaying = false
                    playerPositionMs = videoPreviewPositionMs
                } else {
                    isPlaying = holder.isPlaying
                    playerPositionMs = holder.currentPosition
                }
                playerDurationMs = holder.duration
                playerQueueIndex = holder.queueIndex
                playerQueueSize = holder.queueSize
                shuffleEnabled = holder.shuffleEnabled
                repeatMode = holder.repeatMode
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

    val currentTrack = remember(tracks, playingTrackId) {
        tracks.firstOrNull { it.id == playingTrackId }
    }
    val activeVideoPreview = currentTrack?.let { activeTrack ->
        videoPreview?.takeIf { it.trackId == activeTrack.id }
    }
    LaunchedEffect(currentTrack?.id, playingTrackId, videoPreview?.trackId) {
        val previewTrackId = videoPreview?.trackId ?: return@LaunchedEffect
        val activeTrackId = currentTrack?.id ?: playingTrackId ?: return@LaunchedEffect
        if (activeTrackId != previewTrackId) {
            clearVideoPreview(syncPlayback = false)
        }
    }
    val playableTracks = remember(visibleTracks) {
        visibleTracks.filter { it.localPath?.let(::File)?.isFile == true }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun cancelDownload(track: Track) {
        scope.launch(Dispatchers.IO) {
            val cancelled = DownloadQueueManager.cancel(track.id)
            withContext(Dispatchers.Main) {
                refreshTracks()
                message = if (cancelled) {
                    if (track.status == TrackStatus.DOWNLOADING) {
                        "Prenos je preklican."
                    } else {
                        "Skladba je odstranjena iz čakalne vrste."
                    }
                } else {
                    "Skladba ni več v čakalni vrsti."
                }
            }
        }
    }

    fun openVideoPreview(track: Track) {
        if (videoPreviewLoadingTrackId == track.id) return
        if (videoPreview?.trackId == track.id) return

        clearVideoPreview()
        videoPreviewLoadingTrackId = track.id
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sourceVideoId = track.sourceUrl?.let(::youtubeVideoIdFromUrl)
                    val videoId = sourceVideoId ?: YoutubeDlBridge
                        .searchYouTube(context, "${track.artist} - ${track.title}")
                        .firstOrNull()
                        ?.videoId
                        ?: error("YouTube preview za to skladbo ni najden.")
                    val previewFile = YoutubeDlBridge.downloadPreviewVideo(
                        context = context,
                        url = "https://www.youtube.com/watch?v=$videoId",
                        outputDir = File(File(context.cacheDir, "video-preview"), track.id)
                    )
                    DownloadedYouTubeVideoPreview(
                        videoId = videoId,
                        localVideoPath = previewFile.absolutePath
                    )
                }
            }
            if (videoPreviewLoadingTrackId != track.id) {
                result.getOrNull()?.localVideoPath?.let(::deleteVideoPreview)
                return@launch
            }

            videoPreviewLoadingTrackId = null
            result.onSuccess { downloadedPreview ->
                val startPositionMs = playerHolder?.positionFor(track.id) ?: playerPositionMs
                playerHolder?.pause()
                videoPreviewPositionMs = startPositionMs
                videoPreviewIsPlaying = true
                videoPreview = YouTubeVideoPreview(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    videoId = downloadedPreview.videoId,
                    localVideoPath = downloadedPreview.localVideoPath,
                    startPositionMs = startPositionMs
                )
                videoPreviewFullscreen = false
            }.onFailure { error ->
                message = "YouTube preview ni uspel: ${DownloadErrorFormatter.userMessage(error)}"
            }
        }
    }

    fun togglePlaybackFor(activeTrack: Track) {
        if (videoPreview?.trackId == activeTrack.id) {
            playerHolder?.pause()
        } else {
            activeTrack.localPath?.let { path ->
                playerHolder?.togglePlayback(
                    activeTrack.id,
                    path,
                    activeTrack.title,
                    activeTrack.artist,
                    activeTrack.artworkPath
                )
            }
        }
    }

    fun seekActiveTrack(activeTrack: Track, positionMs: Long) {
        playerHolder?.seekTo(positionMs)
        playerPositionMs = positionMs
        if (videoPreview?.trackId == activeTrack.id) {
            videoPreviewPositionMs = positionMs
        }
    }

    fun enqueueYouTubeResult(
        result: YouTubeSearchResult,
        clearTextSearch: Boolean,
        clearRecognizedSong: Boolean
    ) {
        requestNotificationPermissionIfNeeded()
        val track = Track(
            id = stableTrackId(result.title, result.channel),
            title = result.title,
            artist = result.channel,
            sourceUrl = result.url,
            status = TrackStatus.URL_SET
        )
        scope.launch(Dispatchers.IO) {
            repository.upsert(track)
            val added = DownloadQueueManager.enqueue(track.id)
            withContext(Dispatchers.Main) {
                refreshTracks()
                if (clearTextSearch) {
                    searchQuery = ""
                    youtubeSearchRequested = false
                }
                if (clearRecognizedSong) clearSongRecognition()
                message = if (added) {
                    "YouTube skladba je dodana v čakalno vrsto."
                } else {
                    "Skladba je že v čakalni vrsti."
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush(visualStyle))
    ) {
        if (isLandscape && currentTrack != null && activeVideoPreview != null) {
            LandscapeVideoPreviewScreen(
                track = currentTrack,
                preview = activeVideoPreview,
                isPlaying = videoPreviewIsPlaying,
                positionMs = playerPositionMs,
                durationMs = playerDurationMs,
                queueIndex = playerQueueIndex,
                queueSize = playerQueueSize,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                isFullscreen = videoPreviewFullscreen,
                onVideoPlayPause = { videoPreviewIsPlaying = !videoPreviewIsPlaying },
                onPrevious = { playerHolder?.skipToPrevious() },
                onNext = { playerHolder?.skipToNext() },
                onShuffle = { playerHolder?.toggleShuffle() },
                onRepeat = { playerHolder?.cycleRepeatMode() },
                onSeek = { positionMs -> seekActiveTrack(currentTrack, positionMs) },
                onStop = {
                    clearVideoPreview(syncPlayback = false)
                    playerHolder?.stop()
                },
                onPositionChanged = { videoPreviewPositionMs = it },
                onFullscreen = { videoPreviewFullscreen = true },
                onExitFullscreen = { videoPreviewFullscreen = false },
                onClose = { clearVideoPreview(resumePlayback = true) }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.sunsea_launcher),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SunSea Player",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tracks.count { it.status == TrackStatus.DOWNLOADED }} lokalno · " +
                                "${tracks.size} skladb",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSongRecognitionSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Nastavitve slušnega prepoznavanja"
                        )
                    }
                    IconButton(onClick = { showAppearanceDialog = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Izberi videz")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

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
            onValueChange = {
                searchQuery = it
                youtubeSearchRequested = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            placeholder = { Text("Išči po naslovu ali izvajalcu") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (isSongListening) {
                                songRecognitionJob?.cancel()
                            } else if (
                                songRecognitionEndpoint.isNotBlank() &&
                                SongRecognitionSettings.normalizedEndpoint(songRecognitionEndpoint) == null
                            ) {
                                showSongRecognitionSettings = true
                            } else if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startSongRecognition()
                            } else {
                                songRecognitionPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            if (isSongListening) Icons.Default.Stop else Icons.Default.Hearing,
                            contentDescription = if (isSongListening) {
                                "Ustavi slušno prepoznavanje"
                            } else {
                                "Slušno prepoznavanje pesmi"
                            },
                            tint = if (isSongListening) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isVoiceListening) {
                                voiceSearchController.cancel()
                            } else if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                songRecognitionJob?.cancel()
                                voiceSearchController.startListening()
                            } else {
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            if (isVoiceListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isVoiceListening) {
                                "Ustavi glasovno iskanje"
                            } else {
                                "Glasovno iskanje"
                            }
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                youtubeSearchRequested = false
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Počisti iskanje")
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))

        if (
            songRecognitionResult == null &&
            searchQuery.trim().length >= 2 &&
            visibleTracks.isNotEmpty() &&
            !youtubeSearchRequested
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { youtubeSearchRequested = true },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Išči še na YouTubu", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        songRecognitionResult?.let { recognized ->
            YouTubeSearchSection(
                query = recognized.searchQuery,
                results = songRecognitionSearchResults,
                isLoading = isSongRecognitionSearchLoading,
                searchCompleted = songRecognitionSearchCompleted,
                error = songRecognitionSearchError,
                headerText = "Prepoznano: ${recognized.artist} - ${recognized.title}",
                onDismiss = { clearSongRecognition() },
                onDownload = { result ->
                    enqueueYouTubeResult(
                        result = result,
                        clearTextSearch = true,
                        clearRecognizedSong = true
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        currentTrack?.let { activeTrack ->
            val preview = activeVideoPreview
            if (preview != null) {
                NowPlayingVideoPreviewCard(
                    track = activeTrack,
                    preview = preview,
                    isVideoPlaying = videoPreviewIsPlaying,
                    positionMs = playerPositionMs,
                    durationMs = playerDurationMs,
                    queueIndex = playerQueueIndex,
                    queueSize = playerQueueSize,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    isFullscreen = videoPreviewFullscreen,
                    onVideoPlayPause = { videoPreviewIsPlaying = !videoPreviewIsPlaying },
                    onPrevious = { playerHolder?.skipToPrevious() },
                    onNext = { playerHolder?.skipToNext() },
                    onShuffle = { playerHolder?.toggleShuffle() },
                    onRepeat = { playerHolder?.cycleRepeatMode() },
                    onSeek = { positionMs -> seekActiveTrack(activeTrack, positionMs) },
                    onStop = {
                        clearVideoPreview(syncPlayback = false)
                        playerHolder?.stop()
                    },
                    onPositionChanged = { videoPreviewPositionMs = it },
                    onFullscreen = { videoPreviewFullscreen = true },
                    onExitFullscreen = { videoPreviewFullscreen = false },
                    onClose = { clearVideoPreview(resumePlayback = true) }
                )
            } else {
                NowPlayingCard(
                    track = activeTrack,
                    isPlaying = isPlaying,
                    positionMs = playerPositionMs,
                    durationMs = playerDurationMs,
                    queueIndex = playerQueueIndex,
                    queueSize = playerQueueSize,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    onPlayPause = { togglePlaybackFor(activeTrack) },
                    onPrevious = { playerHolder?.skipToPrevious() },
                    onNext = { playerHolder?.skipToNext() },
                    onShuffle = { playerHolder?.toggleShuffle() },
                    onRepeat = { playerHolder?.cycleRepeatMode() },
                    onSeek = { positionMs -> seekActiveTrack(activeTrack, positionMs) },
                    onStop = { playerHolder?.stop() },
                    onArtworkClick = { openVideoPreview(activeTrack) },
                    isVideoPreviewLoading = videoPreviewLoadingTrackId == activeTrack.id
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        val shouldShowTextYouTubeSearch = songRecognitionResult == null &&
            searchQuery.isNotBlank() &&
            (visibleTracks.isEmpty() || youtubeSearchRequested)

        if (shouldShowTextYouTubeSearch) {
            YouTubeSearchSection(
                query = searchQuery,
                results = youtubeSearchResults,
                isLoading = isYoutubeSearchLoading,
                searchCompleted = youtubeSearchCompleted,
                error = youtubeSearchError,
                headerText = if (visibleTracks.isEmpty()) {
                    "Ni lokalnih zadetkov · rezultati YouTube"
                } else {
                    "Dodatni YouTube zadetki"
                },
                onDismiss = if (youtubeSearchRequested && visibleTracks.isNotEmpty()) {
                    { youtubeSearchRequested = false }
                } else {
                    null
                },
                onDownload = { result ->
                    enqueueYouTubeResult(
                        result = result,
                        clearTextSearch = true,
                        clearRecognizedSong = false
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (tracks.isEmpty() && !shouldShowTextYouTubeSearch) {
            EmptyState()
        } else if (visibleTracks.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(visibleTracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        onSelect = if (searchQuery.isNotBlank()) {
                            {
                                searchQuery = ""
                                youtubeSearchRequested = false
                            }
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
                            if (track.localPath.isNullOrBlank()) {
                                message = "Najprej prenesi ali poveži lokalno datoteko."
                            } else {
                                runCatching {
                                    playerHolder?.toggleQueuePlayback(playableTracks, track.id)
                                }
                                    .onFailure { message = "Player napaka: ${it.message}" }
                            }
                        },
                        isActive = playingTrackId == track.id,
                        isPlaying = playingTrackId == track.id && isPlaying,
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
                        onCancelDownload = { cancelDownload(track) },
                        onShowError = {
                            errorToShow = it
                        }
                    )
                }
            }
        }
        }
    }
    }

    if (showAppearanceDialog) {
        AppearanceDialog(
            selected = visualStyle,
            onSelect = {
                onVisualStyleChange(it)
                showAppearanceDialog = false
            },
            onDismiss = { showAppearanceDialog = false }
        )
    }

    if (showSongRecognitionSettings) {
        RecognitionSettingsDialog(
            apiToken = songRecognitionApiToken,
            customEndpoint = songRecognitionEndpoint,
            onSave = { apiToken, endpoint ->
                SongRecognitionSettings.saveAudDApiToken(context, apiToken)
                SongRecognitionSettings.saveEndpoint(context, endpoint)
                songRecognitionApiToken = apiToken
                songRecognitionEndpoint = endpoint
                showSongRecognitionSettings = false
                message = "Nastavitve prepoznavanja so shranjene."
            },
            onDismiss = { showSongRecognitionSettings = false }
        )
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
                val downloadText = if (track.status == TrackStatus.QUEUED ||
                    track.status == TrackStatus.DOWNLOADING
                ) {
                    "Prenos bo preklican in skladba bo odstranjena iz knjižnice."
                } else {
                    "Izbrisana bo tudi njena lokalna zvočna datoteka."
                }
                Text("${track.artist} – ${track.title}\n\n$downloadText")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        trackToDelete = null
                        playerHolder?.removeTrack(track.id)
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                DownloadQueueManager.removeQueued(track.id)
                                val trackDir = File(File(context.filesDir, "music"), track.id)
                                val filesDeleted = !trackDir.exists() || trackDir.deleteRecursively()
                                if (!filesDeleted && track.status != TrackStatus.DOWNLOADING) {
                                    error("Lokalne datoteke ni bilo mogoče izbrisati.")
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
private fun YouTubeSearchSection(
    query: String,
    results: List<YouTubeSearchResult>,
    isLoading: Boolean,
    searchCompleted: Boolean,
    error: String?,
    headerText: String = "Ni lokalnih zadetkov · rezultati YouTube",
    onDismiss: (() -> Unit)? = null,
    onDownload: (YouTubeSearchResult) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Zapri YouTube rezultate")
                }
            }
        }
        when {
            query.trim().length < 2 -> Text(
                "Za iskanje na YouTubu vnesi vsaj 2 znaka.",
                style = MaterialTheme.typography.bodyMedium
            )
            isLoading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Iščem na YouTubu …")
            }
            error != null -> Text(
                text = "YouTube iskanje ni uspelo: " + error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            searchCompleted && results.isEmpty() -> Text(
                "Tudi na YouTubu ni bilo zadetkov.",
                style = MaterialTheme.typography.bodyMedium
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.forEach { result ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = result.channel + result.durationSeconds?.let {
                                        " · " + formatTime(it * 1_000L)
                                    }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onDownload(result) }) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Prenesi")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionSettingsDialog(
    apiToken: String,
    customEndpoint: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var tokenValue by remember(apiToken) {
        mutableStateOf(apiToken.ifBlank { SongRecognitionSettings.DEFAULT_AUDD_API_TOKEN })
    }
    var endpointValue by remember(customEndpoint) { mutableStateOf(customEndpoint) }
    val normalizedEndpoint = SongRecognitionSettings.normalizedEndpoint(endpointValue)
    val endpointIsValid = endpointValue.isBlank() || normalizedEndpoint != null
    val token = tokenValue.trim().ifBlank { SongRecognitionSettings.DEFAULT_AUDD_API_TOKEN }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Slušno prepoznavanje") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tokenValue,
                    onValueChange = { tokenValue = it },
                    label = { Text("AudD API token") },
                    placeholder = { Text(SongRecognitionSettings.DEFAULT_AUDD_API_TOKEN) },
                    singleLine = true,
                    supportingText = { Text("Privzeti test token je omejen na 10 zahtev/dan.") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpointValue,
                    onValueChange = { endpointValue = it },
                    label = { Text("Custom endpoint") },
                    placeholder = { Text("https://example.com/recognize") },
                    singleLine = true,
                    isError = endpointValue.isNotBlank() && normalizedEndpoint == null,
                    supportingText = if (endpointValue.isNotBlank() && normalizedEndpoint == null) {
                        { Text("Uporabi veljaven HTTPS URL.") }
                    } else {
                        { Text("Pusti prazno za brezplačni AudD način.") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = endpointIsValid,
                onClick = { onSave(token, normalizedEndpoint.orEmpty()) }
            ) {
                Text("Shrani")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Prekliči") }
        }
    )
}

@Composable
private fun AppearanceDialog(
    selected: AppVisualStyle,
    onSelect: (AppVisualStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Izberi videz") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppVisualStyle.entries.forEach { style ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(style) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (style == selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(appBackgroundBrush(style))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(style.displayName, fontWeight = FontWeight.Bold)
                                Text(
                                    style.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            RadioButton(
                                selected = style == selected,
                                onClick = { onSelect(style) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Zapri") }
        }
    )
}

@Composable
private fun NowPlayingCard(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    queueIndex: Int,
    queueSize: Int,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onArtworkClick: () -> Unit,
    isVideoPreviewLoading: Boolean
) {
    var draggedPositionMs by remember(track.id) { mutableStateOf<Long?>(null) }
    val shownPosition = draggedPositionMs ?: positionMs
    val safeDuration = durationMs.coerceAtLeast(1L)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(
                    path = track.artworkPath,
                    size = 48,
                    onClick = onArtworkClick,
                    showVideoBadge = true,
                    isLoading = isVideoPreviewLoading
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (queueSize > 0) {
                    Text(
                        "${queueIndex + 1}/${queueSize}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Slider(
                value = shownPosition.coerceIn(0L, safeDuration).toFloat(),
                onValueChange = { draggedPositionMs = it.toLong() },
                onValueChangeFinished = {
                    draggedPositionMs?.let(onSeek)
                    draggedPositionMs = null
                },
                valueRange = 0f..safeDuration.toFloat(),
                enabled = durationMs > 0,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(shownPosition), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Naključno predvajanje",
                        tint = if (shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prejšnja")
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp)
                ) {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pavza" else "Predvajaj",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Naslednja")
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = "Ponavljanje",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Ustavi")
                }
            }
        }
    }
}

@Composable
private fun NowPlayingVideoPreviewCard(
    track: Track,
    preview: YouTubeVideoPreview,
    isVideoPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    queueIndex: Int,
    queueSize: Int,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    isFullscreen: Boolean,
    onVideoPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    onFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onClose: () -> Unit
) {
    var draggedPositionMs by remember(track.id, preview.videoId) { mutableStateOf<Long?>(null) }
    val shownPosition = draggedPositionMs ?: positionMs
    val safeDuration = durationMs.coerceAtLeast(1L)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(
                    path = track.artworkPath,
                    size = 44,
                    onClick = null,
                    showVideoBadge = true,
                    isLoading = false
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (queueSize > 0) {
                    Text(
                        "${queueIndex + 1}/$queueSize",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onFullscreen) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Povečaj video")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Zapri video")
                }
            }

            Spacer(Modifier.height(8.dp))
            if (!isFullscreen) {
                YouTubePreviewPlayer(
                    preview = preview,
                    playbackPositionMs = positionMs,
                    onPositionChanged = onPositionChanged,
                    showControls = false,
                    playWhenReady = isVideoPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                )
            }

            Slider(
                value = shownPosition.coerceIn(0L, safeDuration).toFloat(),
                onValueChange = { draggedPositionMs = it.toLong() },
                onValueChangeFinished = {
                    draggedPositionMs?.let(onSeek)
                    draggedPositionMs = null
                },
                valueRange = 0f..safeDuration.toFloat(),
                enabled = durationMs > 0,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(shownPosition), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Naključno predvajanje",
                        tint = if (shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prejšnja")
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp)
                ) {
                    IconButton(onClick = onVideoPlayPause) {
                        Icon(
                            if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isVideoPlaying) {
                                "Pavza previewja"
                            } else {
                                "Predvajaj preview"
                            },
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Naslednja")
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = "Ponavljanje",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Ustavi")
                }
            }
        }
    }

    if (isFullscreen) {
        YouTubeVideoFullscreenDialog(
            preview = preview,
            playbackPositionMs = positionMs,
            onPositionChanged = onPositionChanged,
            playWhenReady = isVideoPlaying,
            onExitFullscreen = onExitFullscreen,
            onClose = onClose
        )
    }
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
    onEditSource: () -> Unit,
    onPromote: () -> Unit,
    onDelete: () -> Unit,
    onCancelDownload: () -> Unit,
    onShowError: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            },
            contentColor = if (isActive) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pavza" else "Predvajaj",
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
                if (track.status == TrackStatus.QUEUED || track.status == TrackStatus.DOWNLOADING) {
                    TextButton(
                        onClick = onCancelDownload,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("Prekliči", fontSize = 12.sp)
                    }
                }
                IconButton(
                    onClick = onDelete,
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

private data class DownloadedYouTubeVideoPreview(
    val videoId: String,
    val localVideoPath: String
)

private data class YouTubeVideoPreview(
    val trackId: String,
    val title: String,
    val artist: String,
    val videoId: String,
    val localVideoPath: String?,
    val startPositionMs: Long
)

private val YouTubeVideoPreviewSaver = Saver<YouTubeVideoPreview?, String>(
    save = { preview ->
        if (preview == null) {
            ""
        } else {
            JSONObject()
                .put("trackId", preview.trackId)
                .put("title", preview.title)
                .put("artist", preview.artist)
                .put("videoId", preview.videoId)
                .put("localVideoPath", preview.localVideoPath.orEmpty())
                .put("startPositionMs", preview.startPositionMs)
                .toString()
        }
    },
    restore = { encoded ->
        if (encoded.isBlank()) {
            null
        } else {
            runCatching {
                val json = JSONObject(encoded)
                YouTubeVideoPreview(
                    trackId = json.getString("trackId"),
                    title = json.getString("title"),
                    artist = json.getString("artist"),
                    videoId = json.getString("videoId"),
                    localVideoPath = json.optString("localVideoPath").takeIf(String::isNotBlank),
                    startPositionMs = json.optLong("startPositionMs", 0L)
                )
            }.getOrNull()
        }
    }
)

@Composable
private fun LandscapeVideoPreviewScreen(
    track: Track,
    preview: YouTubeVideoPreview,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    queueIndex: Int,
    queueSize: Int,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    isFullscreen: Boolean,
    onVideoPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    onFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ElevatedCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                if (!isFullscreen) {
                    YouTubePreviewPlayer(
                        preview = preview,
                        playbackPositionMs = positionMs,
                        onPositionChanged = onPositionChanged,
                        showControls = false,
                        playWhenReady = isPlaying,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.56f),
                    contentColor = Color.White
                ) {
                    Row {
                        IconButton(onClick = onFullscreen, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Povečaj video")
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Zapri video")
                        }
                    }
                }
            }
        }

        LandscapeNowPlayingCard(
            track = track,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            queueIndex = queueIndex,
            queueSize = queueSize,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            onPlayPause = onVideoPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onShuffle = onShuffle,
            onRepeat = onRepeat,
            onSeek = onSeek,
            onStop = onStop,
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 244.dp, max = 310.dp)
        )
    }

    if (isFullscreen) {
        YouTubeVideoFullscreenDialog(
            preview = preview,
            playbackPositionMs = positionMs,
            onPositionChanged = onPositionChanged,
            playWhenReady = isPlaying,
            onExitFullscreen = onExitFullscreen,
            onClose = onClose
        )
    }
}

@Composable
private fun LandscapeNowPlayingCard(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    queueIndex: Int,
    queueSize: Int,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedPositionMs by remember(track.id) { mutableStateOf<Long?>(null) }
    val shownPosition = draggedPositionMs ?: positionMs
    val safeDuration = durationMs.coerceAtLeast(1L)

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(
                    path = track.artworkPath,
                    size = 42,
                    onClick = null,
                    showVideoBadge = true,
                    isLoading = false
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (queueSize > 0) {
                    Text(
                        "${queueIndex + 1}/$queueSize",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Slider(
                value = shownPosition.coerceIn(0L, safeDuration).toFloat(),
                onValueChange = { draggedPositionMs = it.toLong() },
                onValueChangeFinished = {
                    draggedPositionMs?.let(onSeek)
                    draggedPositionMs = null
                },
                valueRange = 0f..safeDuration.toFloat(),
                enabled = durationMs > 0,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(shownPosition), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Naključno predvajanje",
                        tint = if (shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prejšnja")
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pavza" else "Predvajaj",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Naslednja")
                }
                IconButton(onClick = onRepeat, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = "Ponavljanje",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = "Ustavi")
                }
            }
        }
    }
}

@Composable
private fun YouTubeVideoFullscreenDialog(
    preview: YouTubeVideoPreview,
    playbackPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    playWhenReady: Boolean = true,
    onExitFullscreen: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onExitFullscreen,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            preview.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            preview.artist,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onExitFullscreen) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Zmanjšaj video")
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Zapri video")
                    }
                }
                YouTubePreviewPlayer(
                    preview = preview,
                    playbackPositionMs = playbackPositionMs,
                    onPositionChanged = onPositionChanged,
                    showControls = true,
                    playWhenReady = playWhenReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                )
            }
        }
    }
}

@Composable
private fun YouTubePreviewPlayer(
    preview: YouTubeVideoPreview,
    playbackPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    showControls: Boolean,
    playWhenReady: Boolean,
    modifier: Modifier = Modifier
) {
    val localVideoPath = preview.localVideoPath
    if (localVideoPath != null) {
        LocalVideoPreviewPlayer(
            videoPath = localVideoPath,
            initialPositionMs = playbackPositionMs,
            onPositionChanged = onPositionChanged,
            showControls = showControls,
            playWhenReady = playWhenReady,
            modifier = modifier
        )
    } else {
        YouTubeWebPlayer(
            videoId = preview.videoId,
            showControls = showControls,
            modifier = modifier
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LocalVideoPreviewPlayer(
    videoPath: String,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    showControls: Boolean,
    playWhenReady: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoUri = remember(videoPath) { Uri.fromFile(File(videoPath)) }
    val player = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            seekTo(initialPositionMs.coerceAtLeast(0L))
            this.playWhenReady = playWhenReady
            prepare()
        }
    }

    LaunchedEffect(player, playWhenReady) {
        player.playWhenReady = playWhenReady
        if (playWhenReady) {
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(player, initialPositionMs) {
        val targetPositionMs = initialPositionMs.coerceAtLeast(0L)
        if (kotlin.math.abs(player.currentPosition - targetPositionMs) > SEEK_SYNC_THRESHOLD_MS) {
            player.seekTo(targetPositionMs)
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            onPositionChanged(player.currentPosition.coerceAtLeast(0L))
            delay(500L)
        }
    }

    DisposableEffect(player) {
        onDispose {
            onPositionChanged(player.currentPosition.coerceAtLeast(0L))
            player.release()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                useController = showControls
                this.player = player
            }
        },
        modifier = modifier,
        update = { view ->
            if (view.player !== player) view.player = player
            view.useController = showControls
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebPlayer(
    videoId: String,
    showControls: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val html = remember(videoId, showControls) { youtubeEmbedHtml(videoId, showControls) }
    val webView = remember(videoId, showControls) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    return if (uri.isYouTubeWatchUrl()) {
                        openYouTubeVideo(context, videoId)
                        true
                    } else {
                        false
                    }
                }
            }
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = true
            loadDataWithBaseURL(
                "https://www.youtube.com",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

@Composable
private fun TrackArtwork(
    path: String?,
    size: Int = 56,
    onClick: (() -> Unit)? = null,
    showVideoBadge: Boolean = false,
    isLoading: Boolean = false
) {
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
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            showVideoBadge -> Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(20.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Odpri video preview",
                    modifier = Modifier.padding(3.dp)
                )
            }
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
    val uri = runCatching { value.toUri() }.getOrNull() ?: return false
    if (uri.scheme != "https" && uri.scheme != "http") return false
    val host = uri.host?.lowercase() ?: return false
    return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
}

private fun youtubeVideoIdFromUrl(value: String): String? {
    val uri = runCatching { value.toUri() }.getOrNull() ?: return null
    if (uri.scheme != "https" && uri.scheme != "http") return null
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val normalizedHost = host.removePrefix("www.")

    if (normalizedHost == "youtu.be") {
        return normalizedYouTubeVideoId(uri.pathSegments.firstOrNull())
    }
    if (normalizedHost != "youtube.com" && !normalizedHost.endsWith(".youtube.com")) {
        return null
    }

    normalizedYouTubeVideoId(uri.getQueryParameter("v"))?.let { return it }
    val segments = uri.pathSegments
    val pathVideoId = when (segments.firstOrNull()) {
        "embed", "shorts", "live" -> segments.getOrNull(1)
        else -> null
    }
    return normalizedYouTubeVideoId(pathVideoId)
}

private fun openYouTubeVideo(context: Context, videoId: String) {
    val safeVideoId = normalizedYouTubeVideoId(videoId) ?: return
    val intent = Intent(
        Intent.ACTION_VIEW,
        "https://www.youtube.com/watch?v=$safeVideoId".toUri()
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "YouTuba ni bilo mogoče odpreti.", Toast.LENGTH_SHORT).show()
        }
}

private fun Uri.isYouTubeWatchUrl(): Boolean {
    val host = host.orEmpty().lowercase()
    val path = path.orEmpty()
    return (host == "youtu.be" && path.length > 1) ||
        ((host == "youtube.com" || host.endsWith(".youtube.com")) && path == "/watch")
}

private fun normalizedYouTubeVideoId(value: String?): String? = value
    ?.trim()
    ?.takeIf { youtubeVideoIdRegex.matches(it) }

private fun youtubeEmbedHtml(videoId: String, showControls: Boolean): String {
    val safeVideoId = normalizedYouTubeVideoId(videoId) ?: return ""
    val controls = if (showControls) 1 else 0
    return """
        <!doctype html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: #000;
                }
                iframe {
                    width: 100%;
                    height: 100%;
                    border: 0;
                    display: block;
                    background: #000;
                }
            </style>
        </head>
        <body>
            <iframe
                src="https://www.youtube.com/embed/$safeVideoId?playsinline=1&controls=$controls&rel=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen>
            </iframe>
        </body>
        </html>
    """.trimIndent()
}

private val youtubeVideoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
private const val SEEK_SYNC_THRESHOLD_MS = 1_000L

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
