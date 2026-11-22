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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray

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
                    var activeDetailMovie by remember { mutableStateOf<MovieItem?>(null) }
                    var activeDetailSeries by remember { mutableStateOf<SeriesData?>(null) }

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
                                        LivePlayerHolder.stopStream()
                                        currentScreen = AppScreen.MOVIES
                                    },
                                    onNavigateToSeries = {
                                        LivePlayerHolder.stopStream()
                                        currentScreen = AppScreen.SERIES
                                    },
                                    onNavigateToSettings = {
                                        LivePlayerHolder.stopStream()
                                        currentScreen = AppScreen.SETTINGS
                                    },
                                    onRefreshData = {
                                        currentAccount?.let { acc ->
                                            performFullRefresh(acc) { live, mov, ser ->
                                                if (live >= 0) {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "✅ Aggiornamento completato!\nScaricati: $live Canali, $mov Film, $ser Serie TV",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "⚠️ Errore durante l'aggiornamento dati dal server",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
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
                                        LivePlayerHolder.stopStream()
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
                                    initialSelectedMovie = activeDetailMovie,
                                    onDetailMovieChange = { activeDetailMovie = it },
                                    onBack = { currentScreen = AppScreen.DASHBOARD },
                                    onMovieSelect = { movie, startPos ->
                                        LivePlayerHolder.stopStream()
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
                                        LivePlayerHolder.stopStream()
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

    private suspend fun performFullRefresh(
        account: UserAccount,
        onComplete: (liveCount: Int, moviesCount: Int, seriesCount: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val serverUrl = account.serverUrl.trimEnd('/')
        val username = account.username
        val password = account.password
        val accountKey = "${serverUrl}_$username"
        val cacheManager = IptvCacheManager(this@MainActivity)

        // Reset cache in memoria e su disco
        LiveTvCache.clear()
        MoviesCache.clear()
        SeriesCache.clear()
        cacheManager.clearAll(accountKey)

        var liveCount = 0
        var moviesCount = 0
        var seriesCount = 0

        try {
            val liveCatUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_live_categories"
            val liveStreamUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_live_streams"
            val movCatUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_vod_categories"
            val movStreamUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_vod_streams"
            val serCatUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_series_categories"
            val serStreamUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_series"

            val liveCatDeferred = async { NetworkUtils.fetchUrlOptimized(liveCatUrl) }
            val liveStreamDeferred = async { NetworkUtils.fetchUrlOptimized(liveStreamUrl) }
            val movCatDeferred = async { NetworkUtils.fetchUrlOptimized(movCatUrl) }
            val movStreamDeferred = async { NetworkUtils.fetchUrlOptimized(movStreamUrl) }
            val serCatDeferred = async { NetworkUtils.fetchUrlOptimized(serCatUrl) }
            val serStreamDeferred = async { NetworkUtils.fetchUrlOptimized(serStreamUrl) }

            // Live TV
            val liveCatText = liveCatDeferred.await().trim()
            val liveStreamText = liveStreamDeferred.await().trim()
            val fetchedLiveCat = mutableListOf<LiveCategory>()
            val fetchedLiveChan = mutableListOf<LiveChannel>()
            fetchedLiveCat.add(LiveCategory("FAVORITES", "PREFERITI"))

            if (liveCatText.startsWith("[")) {
                val arr = JSONArray(liveCatText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    fetchedLiveCat.add(LiveCategory(obj.optString("category_id"), obj.optString("category_name")))
                }
            }
            if (liveStreamText.startsWith("[")) {
                val arr = JSONArray(liveStreamText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    fetchedLiveChan.add(
                        LiveChannel(
                            streamId = obj.optString("stream_id"),
                            name = obj.optString("name"),
                            categoryId = obj.optString("category_id"),
                            streamIcon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            liveCount = fetchedLiveChan.size
            cacheManager.saveLiveCache(accountKey, fetchedLiveCat, fetchedLiveChan)
            LiveTvCache.categories = fetchedLiveCat
            LiveTvCache.channels = fetchedLiveChan

            // Film
            val movCatText = movCatDeferred.await().trim()
            val movStreamText = movStreamDeferred.await().trim()
            val fetchedMovCat = mutableListOf<CategoryItem>()
            fetchedMovCat.add(CategoryItem("ALL", "TUTTI I FILM"))
            fetchedMovCat.add(CategoryItem("FAVORITES", "PREFERITI"))
            fetchedMovCat.add(CategoryItem("CONTINUE", "CONTINUA A GUARDARE"))
            fetchedMovCat.add(CategoryItem("RECENT", "AGGIUNTI DI RECENTE"))

            if (movCatText.startsWith("[")) {
                val arr = JSONArray(movCatText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    fetchedMovCat.add(CategoryItem(obj.optString("category_id"), obj.optString("category_name")))
                }
            }
            val fetchedMovies = mutableListOf<MovieItem>()
            if (movStreamText.startsWith("[")) {
                val arr = JSONArray(movStreamText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rawIcon = obj.optString("stream_icon").ifEmpty { obj.optString("cover") }
                    val fullIcon = getAbsoluteImageUrl(serverUrl, rawIcon)
                    val addedStr = obj.optString("added").ifEmpty { obj.optString("release_date") }

                    fetchedMovies.add(
                        MovieItem(
                            streamId = obj.optString("stream_id"),
                            title = obj.optString("name", "Senza Titolo"),
                            categoryId = obj.optString("category_id"),
                            coverUrl = fullIcon,
                            releaseDate = obj.optString("release_date", obj.optString("added")),
                            addedDate = addedStr,
                            containerExtension = obj.optString("container_extension", "mp4")
                        )
                    )
                }
            }
            moviesCount = fetchedMovies.size
            cacheManager.saveMoviesCache(accountKey, fetchedMovCat, fetchedMovies)
            MoviesCache.categories = fetchedMovCat
            MoviesCache.moviesList = fetchedMovies

            // Serie TV
            val serCatText = serCatDeferred.await().trim()
            val serStreamText = serStreamDeferred.await().trim()
            val fetchedSerCat = mutableListOf<SeriesCategory>()
            fetchedSerCat.add(SeriesCategory("ALL", "TUTTE LE SERIE"))
            fetchedSerCat.add(SeriesCategory("FAVORITES", "PREFERITI"))
            fetchedSerCat.add(SeriesCategory("CONTINUE", "CONTINUA A GUARDARE"))
            fetchedSerCat.add(SeriesCategory("RECENT", "AGGIUNTI DI RECENTE"))

            if (serCatText.startsWith("[")) {
                val arr = JSONArray(serCatText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    fetchedSerCat.add(SeriesCategory(obj.optString("category_id"), obj.optString("category_name")))
                }
            }
            val fetchedSeries = mutableListOf<SeriesData>()
            if (serStreamText.startsWith("[")) {
                val arr = JSONArray(serStreamText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rawCover = obj.optString("cover")
                    val fullCover = getAbsoluteImageUrl(serverUrl, rawCover)
                    val lastMod = obj.optString("last_modified").ifEmpty { obj.optString("added") }

                    fetchedSeries.add(
                        SeriesData(
                            seriesId = obj.optString("series_id"),
                            title = obj.optString("name"),
                            coverUrl = fullCover,
                            categoryId = obj.optString("category_id"),
                            releaseDate = obj.optString("releaseDate", obj.optString("last_modified")),
                            lastModified = lastMod,
                            rating = obj.optString("rating_5based", "0").toFloatOrNull() ?: 0f
                        )
                    )
                }
            }
            seriesCount = fetchedSeries.size
            cacheManager.saveSeriesCache(accountKey, fetchedSerCat, fetchedSeries)
            SeriesCache.categories = fetchedSerCat
            SeriesCache.seriesList = fetchedSeries

            withContext(Dispatchers.Main) {
                onComplete(liveCount, moviesCount, seriesCount)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onComplete(-1, -1, -1)
            }
        }
    }
}
