package com.example.iptv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object LivePlayerHolder {
    var exoPlayer: ExoPlayer? = null
    var currentStreamId: String = ""
    var activeChannel: LiveChannel? = null

    val channelLoadMap = mutableStateMapOf<String, Int>()

    private val handler = Handler(Looper.getMainLooper())
    private var bufferingRunnable: Runnable? = null
    private var loadProgressRunnable: Runnable? = null
    private var streamStartTime: Long = 0L

    var onChannelAutoSwitched: ((LiveChannel) -> Unit)? = null

    fun getChannelLoad(streamId: String): Int {
        return channelLoadMap[streamId] ?: 15
    }

    // Ping ultraleggero HTTP HEAD (Consumo dati 0 KB / 0 Dati video)
    suspend fun checkChannelHealthLightweight(streamUrl: String): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val startTime = System.currentTimeMillis()
            val url = URL(streamUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

            val responseCode = conn.responseCode
            val latency = System.currentTimeMillis() - startTime
            conn.disconnect()

            if (responseCode in 200..399) {
                when {
                    latency < 300L -> 12
                    latency < 800L -> 24
                    latency < 2000L -> 48
                    latency < 3500L -> 72
                    else -> 88
                }
            } else {
                99
            }
        } catch (_: Throwable) {
            99
        }
    }

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                startBufferingProgress(currentStreamId)
                                scheduleBufferingCheck(context)
                            }
                            Player.STATE_READY -> {
                                cancelBufferingCheck()
                                stopBufferingProgress()
                                calculateRealTimeLoad(currentStreamId)
                            }
                            Player.STATE_ENDED -> {
                                cancelBufferingCheck()
                                stopBufferingProgress()
                            }
                            else -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        cancelBufferingCheck()
                        stopBufferingProgress()
                        if (currentStreamId.isNotEmpty()) {
                            channelLoadMap[currentStreamId] = 99
                        }
                        handleStreamFailure(context)
                    }
                })
            }
        }
        return exoPlayer!!
    }

    fun playStream(context: Context, channel: LiveChannel, streamUrl: String) {
        activeChannel = channel
        currentStreamId = channel.streamId
        streamStartTime = System.currentTimeMillis()

        if (channelLoadMap[channel.streamId] == null) {
            channelLoadMap[channel.streamId] = 25
        }

        val player = getPlayer(context)
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != streamUrl || !player.isPlaying) {
            player.setMediaItem(MediaItem.fromUri(streamUrl))
            player.prepare()
            player.play()
        }
    }

    private fun calculateRealTimeLoad(streamId: String) {
        if (streamId.isEmpty()) return
        val latency = System.currentTimeMillis() - streamStartTime
        val loadPct = when {
            latency < 600L -> 12
            latency < 1200L -> 24
            latency < 2500L -> 48
            latency < 4500L -> 75
            else -> 90
        }
        channelLoadMap[streamId] = loadPct
    }

    private fun startBufferingProgress(streamId: String) {
        stopBufferingProgress()
        if (streamId.isEmpty()) return

        var currentProgress = channelLoadMap[streamId] ?: 30
        loadProgressRunnable = object : Runnable {
            override fun run() {
                if (currentProgress < 95) {
                    currentProgress += 15
                    channelLoadMap[streamId] = currentProgress
                    handler.postDelayed(this, 1500L)
                }
            }
        }
        handler.postDelayed(loadProgressRunnable!!, 1500L)
    }

    private fun stopBufferingProgress() {
        loadProgressRunnable?.let { handler.removeCallbacks(it) }
        loadProgressRunnable = null
    }

    private fun scheduleBufferingCheck(context: Context) {
        cancelBufferingCheck()
        val runnable = Runnable {
            if (currentStreamId.isNotEmpty()) {
                channelLoadMap[currentStreamId] = 99
            }
            handleStreamFailure(context)
        }
        bufferingRunnable = runnable
        handler.postDelayed(runnable, 6000L)
    }

    private fun cancelBufferingCheck() {
        bufferingRunnable?.let { handler.removeCallbacks(it) }
        bufferingRunnable = null
    }

    private fun handleStreamFailure(context: Context) {
        val current = activeChannel ?: return
        val allChannels = LiveTvCache.channels
        if (allChannels.isEmpty()) return

        val backups = findBackupChannels(current, allChannels)
        if (backups.isNotEmpty()) {
            val backupChannel = backups.first()
            Toast.makeText(
                context.applicationContext,
                "⚠️ Server intasato su ${current.name}. Passaggio a ${backupChannel.name}...",
                Toast.LENGTH_LONG
            ).show()

            onChannelAutoSwitched?.invoke(backupChannel)
        }
    }

    fun findBackupChannels(currentChannel: LiveChannel, allChannels: List<LiveChannel>): List<LiveChannel> {
        val cleanName = extractCoreChannelName(currentChannel.name)
        if (cleanName.length < 2) return emptyList()

        return allChannels.filter { ch ->
            ch.streamId != currentChannel.streamId && extractCoreChannelName(ch.name) == cleanName
        }
    }

    fun extractCoreChannelName(name: String): String {
        return name
            .replace(Regex("(?i)\\b(IT|UK|US|DE|FR|ES|FHD|HD|SD|4K|1080p|720p|HEVC|H265|RAW|BACKUP|ALT|VIP|FULL HD)\\b"), "")
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .lowercase()
            .trim()
    }
}
