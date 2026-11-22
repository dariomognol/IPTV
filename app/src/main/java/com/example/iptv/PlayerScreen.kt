package com.example.iptv

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
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
    onNextEpisode: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val watchPrefs = remember { WatchPreferences(context) }
    val focusManager = LocalFocusManager.current
    val isLiveStream = videoUrl.contains("/live/")

    val streamId = remember(videoUrl) {
        videoUrl.substringAfterLast("/").substringBefore(".")
    }

    val reconnectHandler = remember { Handler(Looper.getMainLooper()) }

    val exoPlayer = remember(videoUrl) {
        if (isLiveStream) {
            LivePlayerHolder.getPlayer(context).apply {
                val currentUri = currentMediaItem?.localConfiguration?.uri?.toString()
                if (currentUri != videoUrl || !isPlaying) {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    play()
                }
            }
        } else {
            LivePlayerHolder.stopStream()

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
    }

    val reconnectRunnable = remember(exoPlayer) {
        Runnable {
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }

    val playPauseFocusRequester = remember { FocusRequester() }

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

    // Quando i controlli diventano visibili, assegna il focus al tasto Play/Pausa
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(100L)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
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
            if (!isLiveStream) {
                val currentPos = exoPlayer.currentPosition
                val totalDur = exoPlayer.duration
                if (streamId.isNotEmpty()) {
                    watchPrefs.savePosition(streamId, currentPos, totalDur)
                }
                exoPlayer.release()
            } else {
                LivePlayerHolder.stopStream()
            }
            reconnectHandler.removeCallbacks(reconnectRunnable)
            exoPlayer.removeListener(listener)
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
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = currentScale.second,
                    scaleY = currentScale.third
                )
        )

        // 2. Overlay invisibile per intercettare i click e mostrare/nascondere i controlli
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                }
        )

        // Overlay stato errore di playback (es: Errore playback 1/5)
        val liveErrorState = LivePlayerHolder.playbackErrorState
        if (isLiveStream && liveErrorState != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xDDCC0000),
                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = liveErrorState,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Controlli Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // BARRA SUPERIORE CONTROLLI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Torna indietro",
                            tint = Color.White
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pulsante Prossimo Episodio (Se disponibile)
                        if (onNextEpisode != null) {
                            var isNextFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { onNextEpisode() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isNextFocused) Color(0xFFFFC107) else Color(0x99333333)
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .onFocusChanged { isNextFocused = it.isFocused }
                                    .border(if (isNextFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(6.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Prossimo Episodio",
                                    tint = if (isNextFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Episodio Succ.", color = if (isNextFocused) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Pulsante Formato / Proporzioni
                        Button(
                            onClick = {
                                zoomModeIndex = (zoomModeIndex + 1) % zoomScaleFactors.size
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AspectRatio, contentDescription = "Formato", tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(" ${currentScale.first}", color = Color.White, fontSize = 11.sp)
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
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

                // CONTROLLO CENTRALE PLAY / PAUSA
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    var isPlayPauseFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .focusRequester(playPauseFocusRequester)
                            .onFocusChanged { isPlayPauseFocused = it.isFocused }
                            .background(if (isPlayPauseFocused) Color(0xFFFFC107) else Color(0x99000000), CircleShape)
                            .border(if (isPlayPauseFocused) BorderStroke(3.dp, Color.White) else BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                            tint = if (isPlayPauseFocused) Color.Black else Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Se non è Live TV (Film o Serie TV), mostra i pulsanti di salto e la barra di scorrimento
                if (!isLiveStream) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 60000L).coerceAtLeast(0L)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("-1m", color = Color.White, fontSize = 10.sp) }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 30000L).coerceAtLeast(0L)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("-30s", color = Color.White, fontSize = 10.sp) }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 15000L).coerceAtLeast(0L)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("-15s", color = Color.White, fontSize = 10.sp) }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 15000L).coerceAtMost(duration)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("+15s", color = Color.White, fontSize = 10.sp) }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 30000L).coerceAtMost(duration)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("+30s", color = Color.White, fontSize = 10.sp) }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 60000L).coerceAtMost(duration)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("+1m", color = Color.White, fontSize = 10.sp) }
                    }

                    if (duration > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
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
                                modifier = Modifier
                                    .weight(1f)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                    val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(duration)
                                                    exoPlayer.seekTo(newPos)
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                                    exoPlayer.seekTo(newPos)
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    focusManager.moveFocus(FocusDirection.Up)
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_UP,
                                                KeyEvent.KEYCODE_DPAD_DOWN,
                                                KeyEvent.KEYCODE_DPAD_LEFT,
                                                KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                                else -> false
                                            }
                                        } else false
                                    },
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
