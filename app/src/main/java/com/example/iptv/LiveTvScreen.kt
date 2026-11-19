package com.example.iptv

import android.app.Activity
import android.util.Base64
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object LiveTvCache {
    var accountKey: String = ""
    var categories: List<LiveCategory> = emptyList()
    var channels: List<LiveChannel> = emptyList()
    var lastSelectedChannelId: String = ""
    var lastSelectedCategoryId: String = "FAVORITES"

    fun clear() {
        accountKey = ""
        categories = emptyList()
        channels = emptyList()
        lastSelectedChannelId = ""
        lastSelectedCategoryId = "FAVORITES"
    }
}

data class LiveCategory(val categoryId: String, val categoryName: String)
data class LiveChannel(val streamId: String, val name: String, val categoryId: String, val streamIcon: String?)
data class EpgProgram(val title: String, val start: String, val end: String)

@Composable
fun LiveTvScreen(
    account: Any?,
    timezoneId: String = "Europe/Rome",
    onChannelSelect: (LiveChannel) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val watchPrefs = remember { WatchPreferences(context) }

    fun getAccountProperty(propName: String): String {
        if (account == null) return ""
        return try {
            val method = account.javaClass.methods.firstOrNull { it.name.equals("get${propName.replaceFirstChar { c -> c.uppercase() }}", ignoreCase = true) }
            if (method != null) method.invoke(account)?.toString() ?: ""
            else {
                val field = account.javaClass.declaredFields.firstOrNull { it.name.equals(propName, ignoreCase = true) }
                field?.isAccessible = true
                field?.get(account)?.toString() ?: ""
            }
        } catch (_: Exception) { "" }
    }

    val serverUrl = getAccountProperty("serverUrl").trimEnd('/')
    val username = getAccountProperty("username")
    val password = getAccountProperty("password")

    val currentAccountKey = "${serverUrl}_$username"
    val cacheManager = remember { IptvCacheManager(context) }
    val shouldRefresh = remember { cacheManager.shouldRefresh(context, currentAccountKey) }

    if (LiveTvCache.accountKey.isNotEmpty() && LiveTvCache.accountKey != currentAccountKey) {
        LiveTvCache.clear()
    }
    LiveTvCache.accountKey = currentAccountKey

    var categories by remember { mutableStateOf<List<LiveCategory>>(LiveTvCache.categories) }
    var channels by remember { mutableStateOf<List<LiveChannel>>(LiveTvCache.channels) }

    var selectedCategoryId by rememberSaveable { mutableStateOf(LiveTvCache.lastSelectedCategoryId.ifEmpty { "FAVORITES" }) }
    var selectedChannelId by rememberSaveable { mutableStateOf(LiveTvCache.lastSelectedChannelId) }

    var selectedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var epgList by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf(watchPrefs.getLiveFavorites()) }
    var isLoading by remember { mutableStateOf(LiveTvCache.channels.isEmpty()) }

    val exoPlayer = remember { LivePlayerHolder.getPlayer(context) }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        LivePlayerHolder.onChannelAutoSwitched = { backupChannel ->
            selectedChannel = backupChannel
            selectedChannelId = backupChannel.streamId
            LiveTvCache.lastSelectedChannelId = backupChannel.streamId
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            LivePlayerHolder.onChannelAutoSwitched = null
        }
    }

    val selectedCategoryName = remember(selectedCategoryId, categories) {
        when (selectedCategoryId) {
            "FAVORITES" -> "PREFERITI"
            else -> categories.find { it.categoryId == selectedCategoryId }?.categoryName?.uppercase() ?: "LIVE TV"
        }
    }

    val displayedChannels = remember(channels, selectedCategoryId, searchQuery, favoriteIds) {
        channels.filter { ch ->
            val matchesCat = when (selectedCategoryId) {
                "FAVORITES" -> favoriteIds.contains(ch.streamId)
                else -> ch.categoryId == selectedCategoryId
            }
            val matchesSearch = searchQuery.isBlank() || ch.name.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesSearch
        }
    }

    LaunchedEffect(serverUrl, username, password) {
        if (serverUrl.isNotEmpty() && username.isNotEmpty()) {
            if (!shouldRefresh && LiveTvCache.channels.isNotEmpty()) {
                categories = LiveTvCache.categories
                channels = LiveTvCache.channels
                isLoading = false
            } else if (!shouldRefresh) {
                val cached = cacheManager.getLiveCache(currentAccountKey)
                if (cached != null && cached.second.isNotEmpty()) {
                    categories = cached.first
                    channels = cached.second
                    LiveTvCache.categories = cached.first
                    LiveTvCache.channels = cached.second
                    isLoading = false
                }
            }

            if (isLoading) {
                withContext(Dispatchers.IO) {
                    try {
                        val fetchedCategories = mutableListOf<LiveCategory>()
                        val fetchedChannels = mutableListOf<LiveChannel>()

                        fetchedCategories.add(LiveCategory("FAVORITES", "PREFERITI"))

                        val catUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_live_categories"
                        val catArray = JSONArray(URL(catUrl).readText())
                        for (i in 0 until catArray.length()) {
                            val obj = catArray.getJSONObject(i)
                            fetchedCategories.add(
                                LiveCategory(
                                    categoryId = obj.optString("category_id", ""),
                                    categoryName = obj.optString("category_name", "")
                                )
                            )
                        }

                        val streamUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_live_streams"
                        val streamArray = JSONArray(URL(streamUrl).readText())
                        for (i in 0 until streamArray.length()) {
                            val obj = streamArray.getJSONObject(i)
                            fetchedChannels.add(
                                LiveChannel(
                                    streamId = obj.optString("stream_id", ""),
                                    name = obj.optString("name", ""),
                                    categoryId = obj.optString("category_id", ""),
                                    streamIcon = obj.optString("stream_icon", "").takeIf { it.isNotBlank() }
                                )
                            )
                        }

                        cacheManager.saveLiveCache(currentAccountKey, fetchedCategories, fetchedChannels)
                        LiveTvCache.categories = fetchedCategories
                        LiveTvCache.channels = fetchedChannels
                        categories = fetchedCategories
                        channels = fetchedChannels
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }

            val targetChannel = channels.find { it.streamId == selectedChannelId }
                ?: channels.find { it.streamId == LiveTvCache.lastSelectedChannelId }
                ?: channels.firstOrNull()

            if (targetChannel != null) {
                selectedChannelId = targetChannel.streamId
                selectedChannel = targetChannel
                LiveTvCache.lastSelectedChannelId = targetChannel.streamId
            }
        }
    }

    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { channel ->
            if (serverUrl.isNotEmpty() && username.isNotEmpty()) {
                val streamUrl = "$serverUrl/live/$username/$password/${channel.streamId}.m3u8"
                LivePlayerHolder.playStream(context, channel, streamUrl)

                val epgFetched = mutableListOf<EpgProgram>()
                withContext(Dispatchers.IO) {
                    try {
                        val epgUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_short_epg&stream_id=${channel.streamId}"
                        val response = URL(epgUrl).readText()
                        val jsonRoot = JSONObject(response)
                        val epgArray = jsonRoot.optJSONArray("epg_listings")
                        if (epgArray != null) {
                            for (i in 0 until epgArray.length()) {
                                val item = epgArray.getJSONObject(i)
                                val titleBase64 = item.optString("title", "")
                                val decodedTitle = try {
                                    val data = Base64.decode(titleBase64, Base64.DEFAULT)
                                    String(data, Charsets.UTF_8)
                                } catch (_: Exception) {
                                    titleBase64
                                }
                                epgFetched.add(
                                    EpgProgram(
                                        title = decodedTitle,
                                        start = item.optString("start", ""),
                                        end = item.optString("end", "")
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                epgList = epgFetched
            }
        }
    }

    // Controllo ping ultraleggero dei canali in background (Interrotto se il video è in riproduzione)
    LaunchedEffect(displayedChannels, exoPlayer.isPlaying) {
        if (!exoPlayer.isPlaying && serverUrl.isNotEmpty() && username.isNotEmpty()) {
            val visibleChannels = displayedChannels.take(12)
            for (channel in visibleChannels) {
                if (LivePlayerHolder.channelLoadMap[channel.streamId] == null) {
                    val streamUrl = "$serverUrl/live/$username/$password/${channel.streamId}.m3u8"
                    val load = LivePlayerHolder.checkChannelHealthLightweight(streamUrl)
                    LivePlayerHolder.channelLoadMap[channel.streamId] = load
                    delay(250L)
                }
            }
        }
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040C1A))
    ) {
        LiveTopHeaderBar(
            selectedCategoryName = selectedCategoryName,
            timezoneId = timezoneId,
            onBack = onBack,
            onSearchToggle = { isSearchActive = !isSearchActive },
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it }
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 1. COLONNA CATEGORIE (SX)
            Surface(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight(),
                color = Color(0xFF0A182E),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFC107), modifier = Modifier.size(30.dp))
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories, key = { it.categoryId }) { category ->
                            val isSelected = category.categoryId == selectedCategoryId
                            LiveCategorySidebarItem(
                                category = category,
                                isSelected = isSelected,
                                onClick = {
                                    selectedCategoryId = category.categoryId
                                    LiveTvCache.lastSelectedCategoryId = category.categoryId
                                    val firstCh = channels.firstOrNull { ch ->
                                        if (category.categoryId == "FAVORITES") favoriteIds.contains(ch.streamId)
                                        else ch.categoryId == category.categoryId
                                    }
                                    if (firstCh != null) {
                                        selectedChannel = firstCh
                                        selectedChannelId = firstCh.streamId
                                        LiveTvCache.lastSelectedChannelId = firstCh.streamId
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. COLONNA CANALI (CENTRO)
            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                color = Color(0xFF0A182E),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFC107), modifier = Modifier.size(30.dp))
                    }
                } else if (displayedChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nessun canale in questa sezione",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedChannels, key = { it.streamId }) { channel ->
                            val isSelected = channel.streamId == selectedChannel?.streamId
                            val isFav = favoriteIds.contains(channel.streamId)

                            LiveChannelRowItem(
                                channel = channel,
                                isSelected = isSelected,
                                isFavorite = isFav,
                                onToggleFavorite = {
                                    watchPrefs.toggleLiveFavorite(channel.streamId)
                                    favoriteIds = watchPrefs.getLiveFavorites()
                                },
                                onClick = {
                                    selectedChannelId = channel.streamId
                                    LiveTvCache.lastSelectedChannelId = channel.streamId
                                    if (isSelected) {
                                        exoPlayer.pause()
                                        onChannelSelect(channel)
                                    } else {
                                        selectedChannel = channel
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 3. ANTEPRIMA VIDEO + EPG PALINSESTO (DX)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Riquadro Anteprima
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black,
                    border = BorderStroke(1.dp, Color(0xFF1E3A66)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clickable {
                            selectedChannel?.let {
                                selectedChannelId = it.streamId
                                LiveTvCache.lastSelectedChannelId = it.streamId
                                exoPlayer.pause()
                                onChannelSelect(it)
                            }
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
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
                            modifier = Modifier.fillMaxSize()
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            val activeLoad = selectedChannel?.let { LivePlayerHolder.getChannelLoad(it.streamId) } ?: 15
                            val badgeColor = when {
                                activeLoad < 35 -> Color(0xFF4CAF50)
                                activeLoad < 75 -> Color(0xFFFFC107)
                                else -> Color(0xFFFF4242)
                            }
                            Text(
                                text = "● Carico Server: $activeLoad%",
                                color = badgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Tocca/OK per Schermo Intero",
                                color = Color(0xFFFFC107),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Guida EPG Palinsesto
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0A182E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedChannel?.name ?: "Nessun canale selezionato",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (epgList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Nessun programma disponibile al momento per questo canale.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(epgList, key = { it.start + it.title }) { prog ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF132A4D),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = "${prog.start} - ${prog.end}",
                                                color = Color(0xFFFFC107),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = prog.title,
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTopHeaderBar(
    selectedCategoryName: String,
    timezoneId: String = "Europe/Rome",
    onBack: () -> Unit,
    onSearchToggle: () -> Unit,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(timezoneId) {
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone(timezoneId)
        }
        val dateFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale.ITALIAN).apply {
            timeZone = TimeZone.getTimeZone(timezoneId)
        }
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now).uppercase()
            currentDate = dateFormat.format(now).replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp)
    ) {
        var isBackFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(36.dp)
                .onFocusChanged { isBackFocused = it.isFocused }
                .background(
                    color = if (isBackFocused) Color(0xFFFFC107).copy(alpha = 0.3f) else Color.Transparent,
                    shape = CircleShape
                )
                .border(
                    border = if (isBackFocused) BorderStroke(2.dp, Color(0xFFFFC107)) else BorderStroke(0.dp, Color.Transparent),
                    shape = CircleShape
                )
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = Color.White)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(text = currentTime, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = currentDate, color = Color.Gray, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!isSearchActive) {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("♦♦ ", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = selectedCategoryName.uppercase(),
                        color = Color(0xFFFFC107),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(" ♦♦", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("LIVE TV", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Cerca canale...", color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .width(220.dp)
                    .height(46.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFC107),
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        var isSearchBtnFocused by remember { mutableStateOf(false) }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSearchBtnFocused) Color(0xFF1E4175) else Color(0xFF0F2B52),
            modifier = Modifier
                .size(38.dp)
                .onFocusChanged { isSearchBtnFocused = it.isFocused }
                .clickable { onSearchToggle() },
            border = BorderStroke(if (isSearchBtnFocused) 2.dp else 1.dp, if (isSearchBtnFocused) Color(0xFFFFC107) else Color(0xFF1E4175))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Search, contentDescription = "Cerca", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun LiveCategorySidebarItem(
    category: LiveCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isFocused -> Color(0xFF1E4175)
        isSelected -> Color(0xFF0D2548)
        else -> Color.Transparent
    }

    val borderColor = when {
        isFocused -> Color(0xFFFFC107)
        isSelected -> Color(0xFFFFC107).copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    val borderWidth = if (isFocused) 3.dp else if (isSelected) 2.dp else 0.dp

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        ) {
            if (category.categoryId == "FAVORITES") {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFFFC107) else Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category.categoryName,
                    color = if (isFocused) Color(0xFFFFC107) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (isSelected || isFocused) {
                Text("♦♦ ", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = category.categoryName,
                    color = if (isFocused || isSelected) Color(0xFFFFC107) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(" ♦♦", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("♦ ", color = Color.White, fontSize = 10.sp)
                Text(
                    text = category.categoryName,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(" ♦", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun LiveChannelRowItem(
    channel: LiveChannel,
    isSelected: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isFocused -> Color(0xFF1E4175)
        isSelected -> Color(0xFF0D2548)
        else -> Color(0xFF0B192E)
    }

    val borderColor = when {
        isFocused -> Color(0xFFFFC107)
        isSelected -> Color(0xFF00B0FF)
        else -> Color(0xFF1E3A66)
    }

    val borderWidth = if (isFocused) 3.dp else if (isSelected) 2.dp else 1.dp

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!channel.streamIcon.isNullOrEmpty()) {
                AsyncImage(
                    model = channel.streamIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = channel.name,
                color = if (isFocused || isSelected) Color(0xFFFFC107) else Color.White,
                fontSize = 12.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Badge Carico Server % Reale
            val loadPercentage = LivePlayerHolder.getChannelLoad(channel.streamId)

            val loadColor = when {
                loadPercentage < 35 -> Color(0xFF4CAF50)
                loadPercentage < 75 -> Color(0xFFFFC107)
                else -> Color(0xFFFF4242)
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = loadColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, loadColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "$loadPercentage%",
                    color = loadColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            var isFavFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(26.dp)
                    .onFocusChanged { isFavFocused = it.isFocused }
                    .background(
                        color = if (isFavFocused) Color(0xFFFFC107).copy(alpha = 0.3f) else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Preferito",
                    tint = if (isFavorite) Color.Red else Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
