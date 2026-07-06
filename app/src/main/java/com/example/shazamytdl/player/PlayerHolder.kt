package com.example.shazamytdl.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@OptIn(UnstableApi::class)
class PlayerHolder(context: Context) {
    private val positionsByTrack = mutableMapOf<String, Long>()
    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackId: StateFlow<String?> = _currentTrackId

    @Volatile
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        context.applicationContext,
        SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )
    ).buildAsync()

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { connectedController ->
                    controller = connectedController
                    _currentTrackId.value = connectedController.currentMediaItem?.mediaId
                    connectedController.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            _currentTrackId.value = mediaItem?.mediaId
                        }
                    })
                }
            },
            ContextCompat.getMainExecutor(context.applicationContext)
        )
    }

    val isPlaying: Boolean
        get() = controller?.isPlaying == true

    val currentPosition: Long
        get() = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    val duration: Long
        get() = controller?.duration?.coerceAtLeast(0L) ?: 0L

    fun playLocalFile(
        trackId: String,
        path: String,
        title: String,
        artist: String,
        artworkPath: String? = null
    ) {
        val file = File(path)
        require(file.isFile) { "Zvočna datoteka ne obstaja: $path" }

        _currentTrackId.value = trackId
        withController { player ->
            if (player.currentMediaItem?.mediaId == trackId) {
                if (player.playbackState == Player.STATE_ENDED) {
                    positionsByTrack[trackId] = 0L
                    player.seekTo(0)
                }
                player.play()
                return@withController
            }

            rememberCurrentPosition(player)
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .apply {
                    artworkPath
                        ?.let(::File)
                        ?.takeIf { it.isFile }
                        ?.let { setArtworkUri(Uri.fromFile(it)) }
                }
                .build()
            val mediaItem = MediaItem.Builder()
                .setMediaId(trackId)
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(metadata)
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            positionsByTrack[trackId]
                ?.takeIf { it > 0L }
                ?.let(player::seekTo)
            player.play()
        }
    }

    fun togglePlayback(
        trackId: String,
        path: String,
        title: String,
        artist: String,
        artworkPath: String? = null
    ) {
        val player = controller
        if (player?.currentMediaItem?.mediaId == trackId && player.isPlaying) pause()
        else playLocalFile(trackId, path, title, artist, artworkPath)
    }

    fun pause() {
        withController { player ->
            player.pause()
            rememberCurrentPosition(player)
        }
    }

    fun stop() {
        withController { player ->
            player.currentMediaItem?.mediaId?.let { positionsByTrack[it] = 0L }
            player.stop()
            player.clearMediaItems()
            _currentTrackId.value = null
        }
    }

    fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        withController { player ->
            player.currentMediaItem?.mediaId?.let { positionsByTrack[it] = safePosition }
            player.seekTo(safePosition)
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
        controller = null
        MediaController.releaseFuture(controllerFuture)
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
}
