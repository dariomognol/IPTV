package com.example.iptv

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object LivePlayerHolder {
    var exoPlayer: ExoPlayer? = null
    var currentStreamId: String = ""
    var activeChannel: LiveChannel? = null

    val channelLoadMap = mutableStateMapOf<String, Int>()

    // Stato per la gestione dell'errore di playback e tentativi di riconnessione (es: "Errore playback (1/5)")
    var playbackErrorState by mutableStateOf<String?>(null)

    private val handler = Handler(Looper.getMainLooper())
    private var loadProgressRunnable: Runnable? = null
    private var healthCheckJob: Job? = null
    private var stallCheckRunnable: Runnable? = null
    private var retryRunnable: Runnable? = null

    private var retryCount = 0
    private var currentStreamUrl: String = ""
    private var streamStartTime: Long = 0L

    fun getChannelLoad(streamId: String): Int {
        return channelLoadMap[streamId] ?: 15
    }

    // Ping ultraleggero HTTP HEAD per misurare la risposta del server (Consumo dati 0 KB)
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
                                if (playbackErrorState == null) {
                                    scheduleStallCheck(context)
                                }
                            }
                            Player.STATE_READY -> {
                                cancelStallCheck()
                                cancelRetryRunnable()
                                stopBufferingProgress()
                                retryCount = 0
                                playbackErrorState = null
                                calculateRealTimeLoad(currentStreamId)
                            }
                            Player.STATE_ENDED -> {
                                cancelStallCheck()
                                stopBufferingProgress()
                            }
                            else -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        cancelStallCheck()
                        stopBufferingProgress()
                        triggerRetryProcess(context)
                    }
                })
            }
        }
        return exoPlayer!!
    }

    fun playStream(context: Context, channel: LiveChannel, streamUrl: String) {
        activeChannel = channel
        currentStreamId = channel.streamId
        currentStreamUrl = streamUrl
        streamStartTime = System.currentTimeMillis()

        // Reset dei tentativi e dell'errore per il nuovo canale
        cancelRetryProcess()
        retryCount = 0
        playbackErrorState = null

        if (channelLoadMap[channel.streamId] == null) {
            channelLoadMap[channel.streamId] = 20
        }

        // Controllo del carico solo ed esclusivamente per il canale correntemente aperto
        healthCheckJob?.cancel()
        healthCheckJob = CoroutineScope(Dispatchers.Main).launch {
            val load = checkChannelHealthLightweight(streamUrl)
            if (currentStreamId == channel.streamId) {
                channelLoadMap[channel.streamId] = load
            }
        }

        val player = getPlayer(context)
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != streamUrl || !player.isPlaying) {
            player.setMediaItem(MediaItem.fromUri(streamUrl))
            player.prepare()
            player.play()
        }

        scheduleStallCheck(context)
    }

    private fun triggerRetryProcess(context: Context) {
        cancelStallCheck()
        cancelRetryRunnable()

        if (currentStreamId.isNotEmpty()) {
            channelLoadMap[currentStreamId] = 99
        }

        if (retryCount < 5) {
            retryCount++
            playbackErrorState = "Errore playback ($retryCount/5)"

            val runnable = Runnable {
                if (currentStreamUrl.isNotEmpty()) {
                    val player = getPlayer(context)
                    player.setMediaItem(MediaItem.fromUri(currentStreamUrl))
                    player.prepare()
                    player.play()
                    scheduleStallCheck(context)
                }
            }
            retryRunnable = runnable
            handler.postDelayed(runnable, 5000L)
        } else {
            playbackErrorState = "Errore playback (5/5)"
        }
    }

    private fun scheduleStallCheck(context: Context) {
        cancelStallCheck()
        val runnable = Runnable {
            triggerRetryProcess(context)
        }
        stallCheckRunnable = runnable
        handler.postDelayed(runnable, 6000L)
    }

    private fun cancelStallCheck() {
        stallCheckRunnable?.let { handler.removeCallbacks(it) }
        stallCheckRunnable = null
    }

    private fun cancelRetryRunnable() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
    }

    private fun cancelRetryProcess() {
        cancelStallCheck()
        cancelRetryRunnable()
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

    fun stopStream() {
        cancelRetryProcess()
        stopBufferingProgress()
        playbackErrorState = null
        healthCheckJob?.cancel()
        currentStreamId = ""
        currentStreamUrl = ""
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
    }
}
