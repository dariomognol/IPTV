package com.example.iptv

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    startPositionMs: Long = 0L,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val watchPrefs = remember { WatchPreferences(context) }

    val streamId = remember(videoUrl) {
        videoUrl.substringAfterLast("/").substringBefore(".")
    }

    val reconnectHandler = remember { Handler(Looper.getMainLooper()) }

    val exoPlayer = remember(videoUrl) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)

        val customLoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long = 5000L
            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 10
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)
            .setLoadErrorHandlingPolicy(customLoadErrorHandlingPolicy)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                if (startPositionMs > 0) {
                    seekTo(startPositionMs)
                }
                playWhenReady = true
            }
    }

    val reconnectRunnable = remember(exoPlayer) {
        Runnable {
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Stati per la gestione della barra di scorrimento (Seekbar)
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    var zoomModeIndex by remember { mutableIntStateOf(0) }
    val zoomScaleFactors = arrayOf(
        Triple("Adatta", 1.0f, 1.0f),
        Triple("Zoom", 1.25f, 1.25f),
        Triple("Espanso", 1.0f, 1.33f)
    )

    var showAudioMenu by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

    var showVideoMenu by remember { mutableStateOf(false) }
    var videoTracks by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

    // Aggiornamento periodico della posizione del player
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            if (!isSeeking && duration > 0) {
                sliderPosition = currentPosition.toFloat() / duration.toFloat()
            }
            delay(500L)
        }
    }

    // Timer per la scomparsa automatica dei controlli
    LaunchedEffect(controlsVisible, showAudioMenu, showVideoMenu) {
        if (controlsVisible && !showAudioMenu && !showVideoMenu) {
            delay(15000L)
            controlsVisible = false
        }
    }

    val activity = context as? Activity
    DisposableEffect(exoPlayer) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                reconnectHandler.removeCallbacks(reconnectRunnable)
                reconnectHandler.postDelayed(reconnectRunnable, 5000)
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val currentPos = exoPlayer.currentPosition
            val totalDur = exoPlayer.duration
            if (streamId.isNotEmpty()) {
                watchPrefs.savePosition(streamId, currentPos, totalDur)
            }
            reconnectHandler.removeCallbacks(reconnectRunnable)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    BackHandler {
        if (controlsVisible || showAudioMenu || showVideoMenu) {
            controlsVisible = false
            showAudioMenu = false
            showVideoMenu = false
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Player Video con scala dinamica
        val currentScale = zoomScaleFactors[zoomModeIndex]
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = currentScale.second,
                    scaleY = currentScale.third
                )
        )

        // 2. Strato di tocco invisibile per riaprire la barra quando è chiusa
        if (!controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        controlsVisible = true
                    }
            )
        }

        // 3. BARRA SUPERIORE DEI CONTROLLI
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Tasto Indietro
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Indietro", color = Color.White, fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Tasto Play / Pause
                        Button(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Tasto Modifica Size
                        Button(
                            onClick = {
                                zoomModeIndex = (zoomModeIndex + 1) % zoomScaleFactors.size
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AspectRatio, contentDescription = "Size", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(zoomScaleFactors[zoomModeIndex].first, color = Color.White, fontSize = 11.sp)
                        }

                        // Pulsante Tracce Audio
                        Box {
                            Button(
                                onClick = {
                                    val tracks = exoPlayer.currentTracks
                                    val audioList = mutableListOf<Pair<String, Int>>()
                                    for (i in 0 until tracks.groups.size) {
                                        val group = tracks.groups[i]
                                        if (group.type == C.TRACK_TYPE_AUDIO) {
                                            for (j in 0 until group.length) {
                                                if (group.isTrackSupported(j)) {
                                                    val format = group.getTrackFormat(j)
                                                    val lang = format.language ?: "Traccia ${audioList.size + 1}"
                                                    audioList.add(Pair(lang.uppercase(), i))
                                                }
                                            }
                                        }
                                    }
                                    audioTracks = audioList
                                    showAudioMenu = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(" Audio", color = Color.White, fontSize = 11.sp)
                            }

                            DropdownMenu(
                                expanded = showAudioMenu,
                                onDismissRequest = { showAudioMenu = false },
                                modifier = Modifier.background(Color(0xFF222222))
                            ) {
                                if (audioTracks.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Nessuna traccia audio extra", color = Color.Gray, fontSize = 12.sp) },
                                        onClick = { showAudioMenu = false }
                                    )
                                } else {
                                    audioTracks.forEach { track ->
                                        DropdownMenuItem(
                                            text = { Text(track.first, color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                val group = exoPlayer.currentTracks.groups[track.second]
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(0)))
                                                    .build()
                                                showAudioMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Pulsante Tracce Video / Qualità
                        Box {
                            Button(
                                onClick = {
                                    val tracks = exoPlayer.currentTracks
                                    val videoList = mutableListOf<Pair<String, Int>>()
                                    for (i in 0 until tracks.groups.size) {
                                        val group = tracks.groups[i]
                                        if (group.type == C.TRACK_TYPE_VIDEO) {
                                            for (j in 0 until group.length) {
                                                if (group.isTrackSupported(j)) {
                                                    val format = group.getTrackFormat(j)
                                                    val resLabel = "${format.width}x${format.height}"
                                                    videoList.add(Pair(resLabel, i))
                                                }
                                            }
                                        }
                                    }
                                    videoTracks = videoList
                                    showVideoMenu = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(" Video", color = Color.White, fontSize = 11.sp)
                            }

                            DropdownMenu(
                                expanded = showVideoMenu,
                                onDismissRequest = { showVideoMenu = false },
                                modifier = Modifier.background(Color(0xFF222222))
                            ) {
                                if (videoTracks.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Nessun dettaglio video extra", color = Color.Gray, fontSize = 12.sp) },
                                        onClick = { showVideoMenu = false }
                                    )
                                } else {
                                    videoTracks.forEach { track ->
                                        DropdownMenuItem(
                                            text = { Text(track.first, color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                val group = exoPlayer.currentTracks.groups[track.second]
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(0)))
                                                    .build()
                                                showVideoMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Se il video ha una durata valida (Film o Serie TV), mostra la barra di scorrimento
                if (duration > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatMillis(currentPosition),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = sliderPosition,
                            onValueChange = { newValue ->
                                isSeeking = true
                                sliderPosition = newValue
                            },
                            onValueChangeFinished = {
                                val targetPosition = (sliderPosition * duration).toLong()
                                exoPlayer.seekTo(targetPosition)
                                isSeeking = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Text(
                            text = formatMillis(duration),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// Funzione di supporto per formattare i millisecondi in mm:ss o hh:mm:ss
private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}