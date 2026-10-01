package com.example.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SeriesSortMode(val label: String) {
    ALPHA_ASC("A-Z"),
    ALPHA_DESC("Z-A"),
    NEWEST("NEW")
}

// Data Models per le Serie TV
data class SeriesCategory(val id: String, val name: String)

data class SeriesData(
    val seriesId: String,
    val title: String,
    val coverUrl: String?,
    val categoryId: String,
    val releaseDate: String? = null,
    val rating: Float = 0f
)

data class SeriesDetailInfo(
    val seriesId: String,
    val name: String,
    val cover: String?,
    val plot: String?,
    val director: String?,
    val releaseDate: String?,
    val cast: String?,
    val genre: String?,
    val rating: Float
)

data class SeriesEpisode(
    val id: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val containerExtension: String,
    val coverUrl: String? = null
)

@Composable
fun MainSeriesScreen(
    serverUrl: String,
    username: String,
    password: String,
    timezoneId: String = "Europe/Rome",
    onBack: () -> Unit,
    onEpisodeSelect: (SeriesEpisode, startPositionMs: Long) -> Unit
) {
    SeriesScreen(
        serverUrl = serverUrl,
        username = username,
        password = password,
        timezoneId = timezoneId,
        onBack = onBack,
        onEpisodeSelect = onEpisodeSelect
    )
}

@Composable
fun SeriesScreen(
    serverUrl: String,
    username: String,
    password: String,
    timezoneId: String = "Europe/Rome",
    onBack: () -> Unit,
    onEpisodeSelect: (SeriesEpisode, startPositionMs: Long) -> Unit
) {
    val context = LocalContext.current
    val watchPrefs = remember { WatchPreferences(context) }

    var categories by remember { mutableStateOf<List<SeriesCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<SeriesCategory?>(null) }
    var seriesList by remember { mutableStateOf<List<SeriesData>>(emptyList()) }
    var selectedSeriesForDetail by remember { mutableStateOf<SeriesData?>(null) }
    var favoriteIds by remember { mutableStateOf(watchPrefs.getSeriesFavorites()) }

    var isLoadingCategories by remember { mutableStateOf(true) }
    var isLoadingSeries by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SeriesSortMode.ALPHA_ASC) }

    val accountKey = remember(serverUrl, username) { "${serverUrl}_$username" }
    val cacheManager = remember { IptvCacheManager(context) }
    val shouldRefresh = remember { cacheManager.shouldRefresh(context, accountKey) }

    // Caricamento Categorie e Serie
    LaunchedEffect(Unit) {
        if (!shouldRefresh) {
            val cached = cacheManager.getSeriesCache(accountKey)
            if (cached != null && cached.second.isNotEmpty()) {
                categories = cached.first
                seriesList = cached.second
                selectedCategory = cached.first.firstOrNull()
                isLoadingCategories = false
                isLoadingSeries = false
                return@LaunchedEffect
            }
        }

        withContext(Dispatchers.IO) {
            try {
                val catUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_series_categories"
                val conn = URL(catUrl).openConnection() as HttpURLConnection
                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArr = JSONArray(res)
                    val list = mutableListOf<SeriesCategory>()
                    list.add(SeriesCategory("ALL", "TUTTE LE SERIE"))
                    list.add(SeriesCategory("FAVORITES", "PREFERITI"))
                    list.add(SeriesCategory("CONTINUE", "CONTINUA A GUARDARE"))
                    list.add(SeriesCategory("RECENT", "AGGIUNTI DI RECENTE"))

                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        list.add(
                            SeriesCategory(
                                id = obj.optString("category_id"),
                                name = obj.optString("category_name")
                            )
                        )
                    }
                    categories = list
                    selectedCategory = list.firstOrNull()
                }

                val seriesUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_series"
                val connSer = URL(seriesUrl).openConnection() as HttpURLConnection
                if (connSer.responseCode == 200) {
                    val res = connSer.inputStream.bufferedReader().use { it.readText() }
                    val jsonArr = JSONArray(res)
                    val list = mutableListOf<SeriesData>()

                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        list.add(
                            SeriesData(
                                seriesId = obj.optString("series_id"),
                                title = obj.optString("name"),
                                coverUrl = obj.optString("cover"),
                                categoryId = obj.optString("category_id"),
                                releaseDate = obj.optString("releaseDate", obj.optString("last_modified")),
                                rating = obj.optString("rating_5based", "0").toFloatOrNull() ?: 0f
                            )
                        )
                    }
                    seriesList = list
                    cacheManager.saveSeriesCache(accountKey, categories, list)
                }
            } catch (_: Exception) {
            } finally {
                isLoadingCategories = false
                isLoadingSeries = false
            }
        }
    }

    val filteredSeriesByCategory = remember(seriesList, selectedCategory) {
        val cat = selectedCategory ?: return@remember seriesList
        if (cat.id == "ALL" || cat.id == "RECENT" || cat.id == "CONTINUE" || cat.id == "FAVORITES") {
            seriesList
        } else {
            seriesList.filter { it.categoryId == cat.id }
        }
    }

    if (selectedSeriesForDetail != null) {
        SeriesDetailContent(
            series = selectedSeriesForDetail!!,
            serverUrl = serverUrl,
            username = username,
            password = password,
            onBack = {
                selectedSeriesForDetail = null
                favoriteIds = watchPrefs.getSeriesFavorites()
            },
            onEpisodeSelect = onEpisodeSelect
        )
    } else {
        val continueSeriesIds = remember(filteredSeriesByCategory, selectedCategory) {
            watchPrefs.getContinueWatchingSeriesIds()
        }

        val filteredList = filteredSeriesByCategory
            .filter { series ->
                val matchesCategory = when (selectedCategory?.id) {
                    "FAVORITES" -> favoriteIds.contains(series.seriesId)
                    "CONTINUE" -> continueSeriesIds.contains(series.seriesId)
                    else -> true
                }
                val matchesSearch = searchQuery.isBlank() || series.title.contains(searchQuery, ignoreCase = true)
                matchesCategory && matchesSearch
            }
            .let { list ->
                if (selectedCategory?.id == "RECENT") {
                    list.sortedWith(
                        compareByDescending<SeriesData> { it.releaseDate ?: "" }
                            .thenByDescending { it.seriesId.toLongOrNull() ?: 0L }
                    )
                } else {
                    when (sortMode) {
                        SeriesSortMode.ALPHA_ASC -> list.sortedBy { s -> s.title.lowercase(Locale.getDefault()) }
                        SeriesSortMode.ALPHA_DESC -> list.sortedByDescending { s -> s.title.lowercase(Locale.getDefault()) }
                        SeriesSortMode.NEWEST -> list.sortedWith(
                            compareByDescending<SeriesData> { it.releaseDate ?: "" }
                                .thenByDescending { it.seriesId.toLongOrNull() ?: 0L }
                        )
                    }
                }
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF040C1A))
        ) {
            TopHeaderBar(
                selectedCategoryName = selectedCategory?.name ?: "TUTTE LE SERIE",
                timezoneId = timezoneId,
                onBack = onBack,
                sortModeLabel = if (selectedCategory?.id == "RECENT") "NEW" else sortMode.label,
                onSortToggle = {
                    sortMode = when (sortMode) {
                        SeriesSortMode.ALPHA_ASC -> SeriesSortMode.ALPHA_DESC
                        SeriesSortMode.ALPHA_DESC -> SeriesSortMode.NEWEST
                        SeriesSortMode.NEWEST -> SeriesSortMode.ALPHA_ASC
                    }
                },
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
                Surface(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    color = Color(0xFF0A182E),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoadingCategories) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFFFC107), modifier = Modifier.size(30.dp))
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { category ->
                                val isSelected = category.id == selectedCategory?.id
                                CategorySidebarItem(
                                    category = category,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedCategory = category
                                        if (category.id == "RECENT") {
                                            sortMode = SeriesSortMode.NEWEST
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (isLoadingSeries) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFC107),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (filteredList.isEmpty()) {
                        Text(
                            text = "Nessuna serie TV trovata",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredList) { item ->
                                val isFav = favoriteIds.contains(item.seriesId)
                                SeriesCardItem(
                                    series = item,
                                    isFavorite = isFav,
                                    onToggleFavorite = {
                                        watchPrefs.toggleSeriesFavorite(item.seriesId)
                                        favoriteIds = watchPrefs.getSeriesFavorites()
                                    },
                                    onClick = { selectedSeriesForDetail = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopHeaderBar(
    selectedCategoryName: String,
    timezoneId: String = "Europe/Rome",
    onBack: () -> Unit,
    sortModeLabel: String,
    onSortToggle: () -> Unit,
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
                Text("CATEGORIE", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Cerca serie...", color = Color.Gray, fontSize = 12.sp) },
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

        var isSortFocused by remember { mutableStateOf(false) }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSortFocused) Color(0xFF1E4175) else Color(0xFF0F2B52),
            modifier = Modifier
                .width(54.dp)
                .height(38.dp)
                .onFocusChanged { isSortFocused = it.isFocused }
                .clickable { onSortToggle() },
            border = BorderStroke(if (isSortFocused) 2.dp else 1.dp, if (isSortFocused) Color(0xFFFFC107) else Color(0xFF1E4175))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(sortModeLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

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
fun CategorySidebarItem(
    category: SeriesCategory,
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
            if (category.id == "ALL") {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFFFC107) else Color(0xFF00B0FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "= ${category.name}",
                    color = if (isFocused) Color(0xFFFFC107) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (category.id == "FAVORITES") {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFFFC107) else Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category.name,
                    color = if (isFocused) Color(0xFFFFC107) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (category.id == "CONTINUE") {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFFFC107) else Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category.name,
                    color = if (isFocused) Color(0xFFFFC107) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (category.id == "RECENT") {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFFFC107) else Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category.name,
                    color = if (isFocused) Color(0xFFFFC107) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (isSelected || isFocused) {
                Text("♦♦ ", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = category.name,
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
                    text = category.name,
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
fun SeriesCardItem(
    series: SeriesData,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFocused) Color(0xFF163866) else Color(0xFF0B192E)
            ),
            border = if (isFocused) {
                BorderStroke(3.dp, Color(0xFFFFC107))
            } else {
                BorderStroke(1.dp, Color(0xFF1E3A66))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = series.coverUrl,
                    contentDescription = series.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Pulsante Cuore Preferito in alto a destra sulla locandina
                var isFavFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .onFocusChanged { isFavFocused = it.isFocused }
                        .background(
                            color = if (isFavFocused) Color(0xFFFFC107) else Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Preferito",
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(if (isFocused) Color(0xFF00B0FF).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    val yearStr = series.releaseDate?.take(4)?.let { " ($it)" } ?: ""
                    Text(
                        text = "${series.title.uppercase()}$yearStr",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        if (!series.releaseDate.isNullOrBlank()) {
            Text(
                text = "(${series.releaseDate})",
                color = if (isFocused) Color(0xFFFFC107) else Color.LightGray,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun SeriesDetailContent(
    series: SeriesData,
    serverUrl: String,
    username: String,
    password: String,
    onBack: () -> Unit,
    onEpisodeSelect: (SeriesEpisode, startPositionMs: Long) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val watchPrefs = remember { WatchPreferences(context) }

    var isFavorite by remember { mutableStateOf(watchPrefs.isSeriesFavorite(series.seriesId)) }
    var detailInfo by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var seasonsList by remember { mutableStateOf<List<Int>>(emptyList()) }
    var episodesMap by remember { mutableStateOf<Map<Int, List<SeriesEpisode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Dialog per ripresa visione dell'episodio
    var selectedEpisodeForPlayback by remember { mutableStateOf<SeriesEpisode?>(null) }
    var savedProgressMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(series.seriesId) {
        withContext(Dispatchers.IO) {
            try {
                val url = "$serverUrl/player_api.php?username=$username&password=$password&action=get_series_info&series_id=${series.seriesId}"
                val conn = URL(url).openConnection() as HttpURLConnection
                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(res)
                    val infoObj = jsonObj.optJSONObject("info")

                    detailInfo = SeriesDetailInfo(
                        seriesId = series.seriesId,
                        name = infoObj?.optString("name", series.title) ?: series.title,
                        cover = infoObj?.optString("cover", series.coverUrl ?: "") ?: series.coverUrl,
                        plot = infoObj?.optString("plot", "Nessuna trama disponibile."),
                        director = infoObj?.optString("director", "-"),
                        releaseDate = infoObj?.optString("releaseDate", series.releaseDate ?: ""),
                        cast = infoObj?.optString("cast", "-"),
                        genre = infoObj?.optString("genre", "-"),
                        rating = infoObj?.optString("rating_5based", "0")?.toFloatOrNull() ?: 0f
                    )

                    val episodesObj = jsonObj.optJSONObject("episodes")
                    val map = mutableMapOf<Int, List<SeriesEpisode>>()
                    val seasons = mutableListOf<Int>()

                    episodesObj?.keys()?.forEach { seasonKey ->
                        val seasonNum = seasonKey.toIntOrNull() ?: 1
                        seasons.add(seasonNum)
                        val epArray = episodesObj.getJSONArray(seasonKey)
                        val epList = mutableListOf<SeriesEpisode>()

                        for (i in 0 until epArray.length()) {
                            val ep = epArray.getJSONObject(i)
                            val info = ep.optJSONObject("info")
                            epList.add(
                                SeriesEpisode(
                                    id = ep.optString("id"),
                                    title = ep.optString("title"),
                                    seasonNumber = ep.optInt("season", seasonNum),
                                    episodeNumber = ep.optInt("episode", i + 1),
                                    containerExtension = ep.optString("container_extension", "mp4"),
                                    coverUrl = info?.optString("movie_image")
                                )
                            )
                        }
                        map[seasonNum] = epList
                    }

                    seasons.sort()
                    seasonsList = seasons
                    episodesMap = map
                    selectedSeason = seasons.firstOrNull()
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    // Dialog di ripresa visione episodio
    if (selectedEpisodeForPlayback != null) {
        val ep = selectedEpisodeForPlayback!!
        val minutes = (savedProgressMs / 1000) / 60
        val seconds = (savedProgressMs / 1000) % 60
        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        AlertDialog(
            onDismissRequest = { selectedEpisodeForPlayback = null },
            title = { Text("Riprendere la visione?", color = Color.White) },
            text = {
                Text(
                    "Hai già iniziato a guardare '${ep.title}'.\nVuoi continuare da $formattedTime o ricominciare dall'inizio?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val epToPlay = selectedEpisodeForPlayback!!
                        selectedEpisodeForPlayback = null
                        watchPrefs.saveSeriesEpisodeMapping(series.seriesId, epToPlay.id)
                        onEpisodeSelect(epToPlay, savedProgressMs)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Continua da $formattedTime", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val epToPlay = selectedEpisodeForPlayback!!
                        watchPrefs.clearPosition(epToPlay.id)
                        selectedEpisodeForPlayback = null
                        watchPrefs.saveSeriesEpisodeMapping(series.seriesId, epToPlay.id)
                        onEpisodeSelect(epToPlay, 0L)
                    }
                ) {
                    Text("Ricomincia da capo", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0D2548)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040C1A))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var isBackFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onBack,
                modifier = Modifier
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

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = series.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            var isFavBtnFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = {
                    watchPrefs.toggleSeriesFavorite(series.seriesId)
                    isFavorite = watchPrefs.isSeriesFavorite(series.seriesId)
                },
                modifier = Modifier
                    .onFocusChanged { isFavBtnFocused = it.isFocused }
                    .background(if (isFavBtnFocused) Color(0xFFFFC107).copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                    .border(if (isFavBtnFocused) BorderStroke(2.dp, Color(0xFFFFC107)) else BorderStroke(0.dp, Color.Transparent), CircleShape)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Preferito",
                    tint = if (isFavorite) Color.Red else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFC107))
            }
        } else {
            Row(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.width(220.dp)) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        AsyncImage(
                            model = detailInfo?.cover ?: series.coverUrl,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Genere: ${detailInfo?.genre ?: "-"}", color = Color.LightGray, fontSize = 11.sp)
                    Text(text = "Regia: ${detailInfo?.director ?: "-"}", color = Color.LightGray, fontSize = 11.sp)
                    Text(text = "Cast: ${detailInfo?.cast ?: "-"}", color = Color.LightGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detailInfo?.plot ?: "Nessuna trama disponibile",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Stagioni", color = Color(0xFFFFC107), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasonsList) { season ->
                            val isSelected = season == selectedSeason
                            var isFocused by remember { mutableStateOf(false) }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when {
                                    isSelected -> Color(0xFFFFC107)
                                    isFocused -> Color(0xFF1E4175)
                                    else -> Color(0xFF0F2B52)
                                },
                                border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
                                modifier = Modifier
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .clickable { selectedSeason = season }
                            ) {
                                Text(
                                    text = "Stagione $season",
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(currentEpisodes) { ep ->
                            var isFocused by remember { mutableStateOf(false) }
                            val savedPos = watchPrefs.getPosition(ep.id)

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFocused) Color(0xFF1E4175) else Color(0xFF0A182E)
                                ),
                                border = if (isFocused) BorderStroke(2.dp, Color(0xFFFFC107)) else BorderStroke(1.dp, Color(0xFF1E3A66)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .clickable {
                                        watchPrefs.saveSeriesEpisodeMapping(series.seriesId, ep.id)
                                        if (savedPos > 0) {
                                            selectedEpisodeForPlayback = ep
                                            savedProgressMs = savedPos
                                        } else {
                                            onEpisodeSelect(ep, 0L)
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleOutline,
                                        contentDescription = "Riproduci",
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Episodio ${ep.episodeNumber}: ${ep.title}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (savedPos > 0) {
                                            val min = (savedPos / 1000) / 60
                                            val sec = (savedPos / 1000) % 60
                                            Text(
                                                text = String.format(Locale.getDefault(), "Interrotto a %02d:%02d", min, sec),
                                                color = Color(0xFFFFC107),
                                                fontSize = 10.sp
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
