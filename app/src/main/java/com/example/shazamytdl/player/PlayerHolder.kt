package com.example.shazamytdl.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.shazamytdl.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(UnstableApi::class)
class PlayerHolder(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val positionsByTrack = mutableMapOf<String, Long>()
    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackId: StateFlow<String?> = _currentTrackId

    @Volatile
    private var controller: MediaController? = null
    private var lastPositionPersistedAt = 0L
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
    ).buildAsync()

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { connectedController ->
                    controller = connectedController
                    if (connectedController.mediaItemCount == 0) restoreQueue(connectedController)
                    connectedController.shuffleModeEnabled =
                        preferences.getBoolean(KEY_SHUFFLE, false)
                    connectedController.repeatMode = preferences.getInt(
                        KEY_REPEAT,
                        Player.REPEAT_MODE_OFF
                    )
                    updateCurrentTrack(connectedController)
                    connectedController.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            _currentTrackId.value = mediaItem?.mediaId
                            persistPlayerState(connectedController)
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            persistPlayerState(connectedController)
                        }

                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            persistPlayerState(connectedController)
                        }

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            persistPlayerState(connectedController)
                        }
                    })
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    val isPlaying: Boolean
        get() = controller?.isPlaying == true

    val currentPosition: Long
        get() {
            val player = controller ?: return 0L
            maybePersistPosition(player)
            return player.currentPosition.coerceAtLeast(0L)
        }

    val duration: Long
        get() = controller?.duration?.coerceAtLeast(0L) ?: 0L

    val queueIndex: Int
        get() = controller?.currentMediaItemIndex?.takeIf { it >= 0 } ?: 0

    val queueSize: Int
        get() = controller?.mediaItemCount ?: 0

    val shuffleEnabled: Boolean
        get() = controller?.shuffleModeEnabled
            ?: preferences.getBoolean(KEY_SHUFFLE, false)

    val repeatMode: Int
        get() = controller?.repeatMode
            ?: preferences.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)

    fun toggleQueuePlayback(tracks: List<Track>, startTrackId: String) {
        val playableTracks = tracks.filter { track ->
            track.localPath?.let(::File)?.isFile == true
        }
        require(playableTracks.any { it.id == startTrackId }) {
            "Izbrana skladba nima veljavne lokalne datoteke."
        }

        withController { player ->
            if (player.currentMediaItem?.mediaId == startTrackId) {
                if (player.isPlaying) {
                    pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                    player.play()
                }
                return@withController
            }

            rememberCurrentPosition(player)
            val startIndex = playableTracks.indexOfFirst { it.id == startTrackId }
            player.setMediaItems(
                playableTracks.map(::trackToMediaItem),
                startIndex,
                positionsByTrack[startTrackId] ?: 0L
            )
            player.prepare()
            player.play()
            persistQueue(playableTracks)
            persistPlayerState(player)
            updateCurrentTrack(player)
        }
    }

    fun playLocalFile(
        trackId: String,
        path: String,
        title: String,
        artist: String,
        artworkPath: String? = null
    ) {
        toggleQueuePlayback(
            listOf(
                Track(
                    id = trackId,
                    title = title,
                    artist = artist,
                    localPath = path,
                    artworkPath = artworkPath
                )
            ),
            trackId
        )
    }

    fun togglePlayback(
        trackId: String,
        path: String,
        title: String,
        artist: String,
        artworkPath: String? = null
    ) = playLocalFile(trackId, path, title, artist, artworkPath)

    fun pause() {
        withController { player ->
            player.pause()
            rememberCurrentPosition(player)
            persistPlayerState(player)
        }
    }

    fun play() {
        withController { player ->
            if (player.mediaItemCount == 0) return@withController
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.play()
            persistPlayerState(player)
            updateCurrentTrack(player)
        }
    }

    fun playFrom(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        withController { player ->
            if (player.mediaItemCount == 0) return@withController
            player.currentMediaItem?.mediaId?.let { positionsByTrack[it] = safePosition }
            player.seekTo(safePosition)
            player.play()
            persistPlayerState(player)
            updateCurrentTrack(player)
        }
    }

    fun stop() {
        withController { player ->
            player.currentMediaItem?.mediaId?.let { positionsByTrack[it] = 0L }
            player.stop()
            player.clearMediaItems()
            _currentTrackId.value = null
            clearSavedQueue()
        }
    }

    fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        withController { player ->
            player.currentMediaItem?.mediaId?.let { positionsByTrack[it] = safePosition }
            player.seekTo(safePosition)
            persistPlayerState(player)
        }
    }

    fun skipToNext() {
        withController { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
                persistPlayerState(player)
            }
        }
    }

    fun skipToPrevious() {
        withController { player ->
            if (player.currentPosition > RESTART_THRESHOLD_MS || !player.hasPreviousMediaItem()) {
                player.seekTo(0L)
            } else {
                player.seekToPreviousMediaItem()
            }
            player.play()
            persistPlayerState(player)
        }
    }

    fun toggleShuffle() {
        withController { player ->
            player.shuffleModeEnabled = !player.shuffleModeEnabled
            persistPlayerState(player)
        }
    }

    fun cycleRepeatMode() {
        withController { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            persistPlayerState(player)
        }
    }

    fun removeTrack(trackId: String) {
        withController { player ->
            val index = (0 until player.mediaItemCount)
                .firstOrNull { player.getMediaItemAt(it).mediaId == trackId }
                ?: return@withController
            player.removeMediaItem(index)
            if (player.mediaItemCount == 0) {
                _currentTrackId.value = null
                clearSavedQueue()
            } else {
                persistCurrentQueue(player)
                persistPlayerState(player)
                updateCurrentTrack(player)
            }
        }
    }

    fun positionFor(trackId: String): Long {
        val player = controller
        if (player?.currentMediaItem?.mediaId == trackId) {
            val position = if (player.playbackState == Player.STATE_ENDED) {
                0L
            } else {
                player.currentPosition.coerceAtLeast(0L)
            }
            positionsByTrack[trackId] = position
            return position
        }
        return positionsByTrack[trackId] ?: 0L
    }

    fun release() {
        controller?.let(::persistPlayerState)
        controller = null
        MediaController.releaseFuture(controllerFuture)
    }

    private fun trackToMediaItem(track: Track): MediaItem {
        val path = requireNotNull(track.localPath)
        val file = File(path)
        require(file.isFile) { "Zvočna datoteka ne obstaja: $path" }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .apply {
                track.artworkPath
                    ?.let(::File)
                    ?.takeIf { it.isFile }
                    ?.let { setArtworkUri(Uri.fromFile(it)) }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun restoreQueue(player: MediaController) {
        val serialized = preferences.getString(KEY_QUEUE, null) ?: return
        val items = runCatching {
            val array = JSONArray(serialized)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val path = item.getString(JSON_PATH)
                    if (!File(path).isFile) continue
                    add(
                        MediaItem.Builder()
                            .setMediaId(item.getString(JSON_ID))
                            .setUri(Uri.fromFile(File(path)))
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(item.optString(JSON_TITLE))
                                    .setArtist(item.optString(JSON_ARTIST))
                                    .apply {
                                        item.optString(JSON_ARTWORK)
                                            .takeIf { it.isNotBlank() && File(it).isFile }
                                            ?.let { setArtworkUri(Uri.fromFile(File(it))) }
                                    }
                                    .build()
                            )
                            .build()
                    )
                }
            }
        }.getOrElse {
            clearSavedQueue()
            emptyList()
        }
        if (items.isEmpty()) return

        val savedTrackId = preferences.getString(KEY_CURRENT_ID, null)
        val matchingIndex = items.indexOfFirst { it.mediaId == savedTrackId }
        val index = if (matchingIndex >= 0) {
            matchingIndex
        } else {
            preferences.getInt(KEY_INDEX, 0).coerceIn(items.indices)
        }
        val position = if (matchingIndex >= 0) {
            preferences.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
        } else {
            0L
        }
        player.setMediaItems(items, index, position)
        player.prepare()
    }

    private fun persistQueue(tracks: List<Track>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(
                JSONObject()
                    .put(JSON_ID, track.id)
                    .put(JSON_PATH, track.localPath)
                    .put(JSON_TITLE, track.title)
                    .put(JSON_ARTIST, track.artist)
                    .put(JSON_ARTWORK, track.artworkPath.orEmpty())
            )
        }
        preferences.edit { putString(KEY_QUEUE, array.toString()) }
    }

    private fun persistCurrentQueue(player: MediaController) {
        val array = JSONArray()
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val path = item.localConfiguration?.uri?.path ?: continue
            array.put(
                JSONObject()
                    .put(JSON_ID, item.mediaId)
                    .put(JSON_PATH, path)
                    .put(JSON_TITLE, item.mediaMetadata.title?.toString().orEmpty())
                    .put(JSON_ARTIST, item.mediaMetadata.artist?.toString().orEmpty())
                    .put(JSON_ARTWORK, item.mediaMetadata.artworkUri?.path.orEmpty())
            )
        }
        preferences.edit { putString(KEY_QUEUE, array.toString()) }
    }

    private fun persistPlayerState(player: MediaController) {
        if (player.mediaItemCount == 0) return
        preferences.edit {
            putInt(KEY_INDEX, player.currentMediaItemIndex.coerceAtLeast(0))
            putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0L))
            putString(KEY_CURRENT_ID, player.currentMediaItem?.mediaId)
            putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            putInt(KEY_REPEAT, player.repeatMode)
        }
        lastPositionPersistedAt = SystemClock.elapsedRealtime()
    }

    private fun maybePersistPosition(player: MediaController) {
        val now = SystemClock.elapsedRealtime()
        if (player.isPlaying && now - lastPositionPersistedAt >= POSITION_SAVE_INTERVAL_MS) {
            persistPlayerState(player)
        }
    }

    private fun updateCurrentTrack(player: MediaController) {
        _currentTrackId.value = player.currentMediaItem?.mediaId
    }

    private fun clearSavedQueue() {
        preferences.edit {
            remove(KEY_QUEUE)
            remove(KEY_INDEX)
            remove(KEY_POSITION)
            remove(KEY_CURRENT_ID)
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: controllerFuture.addListener(
            { runCatching { controllerFuture.get() }.onSuccess(action) },
            Runnable::run
        )
    }

    private fun rememberCurrentPosition(player: Player) {
        val trackId = player.currentMediaItem?.mediaId ?: return
        positionsByTrack[trackId] = if (player.playbackState == Player.STATE_ENDED) {
            0L
        } else {
            player.currentPosition.coerceAtLeast(0L)
        }
    }

    private companion object {
        const val PREFS_NAME = "player_queue"
        const val KEY_QUEUE = "queue"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_CURRENT_ID = "current_id"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val JSON_ID = "id"
        const val JSON_PATH = "path"
        const val JSON_TITLE = "title"
        const val JSON_ARTIST = "artist"
        const val JSON_ARTWORK = "artwork"
        const val RESTART_THRESHOLD_MS = 3_000L
        const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
}
