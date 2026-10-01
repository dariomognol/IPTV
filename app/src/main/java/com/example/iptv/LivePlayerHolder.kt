package com.example.iptv

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

object LivePlayerHolder {
    var exoPlayer: ExoPlayer? = null
    var currentStreamId: String = ""

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                playWhenReady = true
            }
        }
        return exoPlayer!!
    }

    fun playStream(context: Context, streamId: String, streamUrl: String) {
        val player = getPlayer(context)
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != streamUrl || !player.isPlaying) {
            currentStreamId = streamId
            player.setMediaItem(MediaItem.fromUri(streamUrl))
            player.prepare()
            player.play()
        }
    }
}
