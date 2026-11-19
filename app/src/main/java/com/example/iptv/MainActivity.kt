package com.example.iptv

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

enum class AppScreen {
    DASHBOARD,
    LIVE_TV,
    MOVIES,
    SERIES,
    SETTINGS,
    PLAYER
}

class MainActivity : ComponentActivity() {

    private lateinit var accountPrefs: AccountPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountPrefs = AccountPreferences(this)

        setContent {
            MaterialTheme {
                Surface {
                    val appPrefs = remember { AppPreferences(this) }

                    var savedAccounts by remember { mutableStateOf(accountPrefs.getSavedAccounts()) }
                    var currentAccount by remember { mutableStateOf(accountPrefs.getActiveAccount()) }
                    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
                    var previousScreen by remember { mutableStateOf(AppScreen.LIVE_TV) }

                    // --- STATI IMPOSTAZIONI ---
                    var selectedTimezoneId by remember { mutableStateOf(appPrefs.getTimezone()) }
                    var selectedPlayer by remember { mutableStateOf(appPrefs.getPlayer()) }
                    var selectedStreamFormat by remember { mutableStateOf(appPrefs.getStreamFormat()) }
                    var selectedAutoUpdate by remember { mutableStateOf(appPrefs.getAutoUpdate()) }
                    var isAutostartEnabled by remember { mutableStateOf(appPrefs.getAutostart()) }

                    // --- STATO STREAM E RIPRESA VISIONE ---
                    var currentStreamUrl by remember { mutableStateOf("") }
                    var startPositionMs by remember { mutableLongStateOf(0L) }
                    var nextEpisodeCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

                    var showExitConfirmation by remember { mutableStateOf(false) }

                    BackHandler(enabled = currentScreen == AppScreen.DASHBOARD) {
                        showExitConfirmation = true
                    }

                    if (showExitConfirmation) {
                        AlertDialog(
                            onDismissRequest = { showExitConfirmation = false },
                            title = { Text("Uscire dall'applicazione?", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = { Text("Sei sicuro di voler uscire da IPTV PRO?", color = Color.LightGray) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showExitConfirmation = false
                                        finishAffinity()
                                        Process.killProcess(Process.myPid())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B4B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Esci", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExitConfirmation = false }) {
                                    Text("Annulla", color = Color.White)
                                }
                            },
                            containerColor = Color(0xFF161D2B)
                        )
                    }

                    if (currentAccount == null) {
                        LoginScreen(
                            onBack = if (savedAccounts.isNotEmpty()) {
                                {
                                    currentAccount = accountPrefs.getActiveAccount() ?: savedAccounts.firstOrNull()
                                    currentScreen = AppScreen.DASHBOARD
                                }
                            } else null,
                            onLoginSuccess = { newAccount ->
                                accountPrefs.saveAccount(newAccount)
                                savedAccounts = accountPrefs.getSavedAccounts()
                                currentAccount = newAccount
                                currentScreen = AppScreen.DASHBOARD
                            }
                        )
                    } else {
                        when (currentScreen) {
                            AppScreen.DASHBOARD -> {
                                SmartersDashboardScreen(
                                    accountName = currentAccount?.playlistName ?: "Utente IPTV",
                                    accountsList = savedAccounts.map { it.playlistName },
                                    timezoneId = selectedTimezoneId,
                                    onSelectAccount = { selectedAccountName ->
                                        accountPrefs.setActiveAccount(selectedAccountName)
                                        currentAccount = accountPrefs.getActiveAccount()
                                    },
                                    onRemoveAccount = { accountToRemoveName ->
                                        accountPrefs.removeAccount(accountToRemoveName)
                                        savedAccounts = accountPrefs.getSavedAccounts()
                                        currentAccount = accountPrefs.getActiveAccount()
                                    },
                                    onAddNewAccount = {
                                        currentAccount = null
                                    },
                                    onNavigateToLiveTv = {
                                        currentScreen = AppScreen.LIVE_TV
                                    },
                                    onNavigateToMovies = {
                                        currentScreen = AppScreen.MOVIES
                                    },
                                    onNavigateToSeries = {
                                        currentScreen = AppScreen.SERIES
                                    },
                                    onNavigateToSettings = {
                                        currentScreen = AppScreen.SETTINGS
                                    },
                                    onRefreshData = {
                                        LiveTvCache.clear()
                                        Toast.makeText(this@MainActivity, "Lista canali e EPG aggiornati!", Toast.LENGTH_SHORT).show()
                                    },
                                    onExitApp = {
                                        showExitConfirmation = true
                                    }
                                )
                            }

                            AppScreen.LIVE_TV -> {
                                LiveTvScreen(
                                    account = currentAccount,
                                    timezoneId = selectedTimezoneId,
                                    onBack = {
                                        LivePlayerHolder.exoPlayer?.pause()
                                        currentScreen = AppScreen.DASHBOARD
                                    },
                                    onChannelSelect = { channel ->
                                        nextEpisodeCallback = null
                                        currentAccount?.let { acc ->
                                            val ext = if (selectedStreamFormat.contains("TS")) "ts" else "m3u8"
                                            val baseUrl = acc.serverUrl.trimEnd('/')
                                            currentStreamUrl = "$baseUrl/live/${acc.username}/${acc.password}/${channel.streamId}.$ext"
                                            startPositionMs = 0L
                                            previousScreen = AppScreen.LIVE_TV

                                            if (selectedPlayer.contains("VLC") || selectedPlayer.contains("Nativo")) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(currentStreamUrl.toUri(), "video/*")
                                                    }
                                                    startActivity(intent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(this@MainActivity, "Nessun player esterno trovato, avvio ExoPlayer...", Toast.LENGTH_SHORT).show()
                                                    currentScreen = AppScreen.PLAYER
                                                }
                                            } else {
                                                currentScreen = AppScreen.PLAYER
                                            }
                                        }
                                    }
                                )
                            }

                            AppScreen.MOVIES -> {
                                MoviesScreen(
                                    account = currentAccount,
                                    timezoneId = selectedTimezoneId,
                                    onBack = { currentScreen = AppScreen.DASHBOARD },
                                    onMovieSelect = { movie, startPos ->
                                        activeDetailMovie = movie
                                        nextEpisodeCallback = null
                                        currentAccount?.let { acc ->
                                            val ext = movie.containerExtension ?: "mp4"
                                            val baseUrl = acc.serverUrl.trimEnd('/')
                                            currentStreamUrl = "$baseUrl/movie/${acc.username}/${acc.password}/${movie.streamId}.$ext"
                                            startPositionMs = startPos
                                            previousScreen = AppScreen.MOVIES

                                            if (selectedPlayer.contains("VLC") || selectedPlayer.contains("Nativo")) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(currentStreamUrl.toUri(), "video/*")
                                                        putExtra("position", startPos.toInt())
                                                    }
                                                    startActivity(intent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(this@MainActivity, "Nessun player esterno trovato, avvio ExoPlayer...", Toast.LENGTH_SHORT).show()
                                                    currentScreen = AppScreen.PLAYER
                                                }
                                            } else {
                                                currentScreen = AppScreen.PLAYER
                                            }
                                        }
                                    }
                                )
                            }

                            AppScreen.SERIES -> {
                                MainSeriesScreen(
                                    serverUrl = currentAccount?.serverUrl ?: "",
                                    username = currentAccount?.username ?: "",
                                    password = currentAccount?.password ?: "",
                                    timezoneId = selectedTimezoneId,
                                    initialSelectedSeries = activeDetailSeries,
                                    onDetailSeriesChange = { activeDetailSeries = it },
                                    onBack = { currentScreen = AppScreen.DASHBOARD },
                                    onEpisodeSelect = { episode, startPos, onNextEp ->
                                        nextEpisodeCallback = onNextEp
                                        currentAccount?.let { acc ->
                                            val ext = episode.containerExtension
                                            val baseUrl = acc.serverUrl.trimEnd('/')
                                            currentStreamUrl = "$baseUrl/series/${acc.username}/${acc.password}/${episode.id}.$ext"
                                            startPositionMs = startPos
                                            previousScreen = AppScreen.SERIES

                                            if (selectedPlayer.contains("VLC") || selectedPlayer.contains("Nativo")) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(currentStreamUrl.toUri(), "video/*")
                                                        putExtra("position", startPos.toInt())
                                                    }
                                                    startActivity(intent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(this@MainActivity, "Nessun player esterno trovato, avvio ExoPlayer...", Toast.LENGTH_SHORT).show()
                                                    currentScreen = AppScreen.PLAYER
                                                }
                                            } else {
                                                currentScreen = AppScreen.PLAYER
                                            }
                                        }
                                    }
                                )
                            }

                            AppScreen.PLAYER -> {
                                PlayerScreen(
                                    videoUrl = currentStreamUrl,
                                    startPositionMs = startPositionMs,
                                    onNextEpisode = nextEpisodeCallback,
                                    onBack = { currentScreen = previousScreen }
                                )
                            }

                            AppScreen.SETTINGS -> {
                                SettingsScreen(
                                    currentTimezoneId = selectedTimezoneId,
                                    onTimezoneChanged = {
                                        selectedTimezoneId = it
                                        appPrefs.saveTimezone(it)
                                    },
                                    currentPlayer = selectedPlayer,
                                    onPlayerChanged = {
                                        selectedPlayer = it
                                        appPrefs.savePlayer(it)
                                    },
                                    currentStreamFormat = selectedStreamFormat,
                                    onStreamFormatChanged = {
                                        selectedStreamFormat = it
                                        appPrefs.saveStreamFormat(it)
                                    },
                                    currentAutoUpdate = selectedAutoUpdate,
                                    onAutoUpdateChanged = {
                                        selectedAutoUpdate = it
                                        appPrefs.saveAutoUpdate(it)
                                    },
                                    isAutostartEnabled = isAutostartEnabled,
                                    onAutostartChanged = {
                                        isAutostartEnabled = it
                                        appPrefs.saveAutostart(it)
                                    },
                                    onUpdateEpg = { },
                                    onResetDefaults = {
                                        appPrefs.resetDefaults()
                                        selectedTimezoneId = appPrefs.getTimezone()
                                        selectedPlayer = appPrefs.getPlayer()
                                        selectedStreamFormat = appPrefs.getStreamFormat()
                                        selectedAutoUpdate = appPrefs.getAutoUpdate()
                                        isAutostartEnabled = appPrefs.getAutostart()
                                    },
                                    onBack = {
                                        currentScreen = AppScreen.DASHBOARD
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
