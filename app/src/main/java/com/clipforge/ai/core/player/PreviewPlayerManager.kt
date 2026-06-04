package com.clipforge.ai.core.player
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
class PreviewPlayerManager(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    fun loadUri(uri: Uri) { player.setMediaItem(MediaItem.fromUri(uri)); player.prepare() }
    fun play()                   { player.play() }
    fun pause()                  { player.pause() }
    fun seekTo(positionMs: Long) { player.seekTo(positionMs) }
    fun release()                { player.release() }
    fun isPlaying(): Boolean     = player.isPlaying
}
