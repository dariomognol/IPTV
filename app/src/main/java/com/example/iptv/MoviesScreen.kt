package com.example.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object MoviesScrollStateHolder {
    var index: Int = 0
    var offset: Int = 0
}

enum class MovieSortMode(val label: String) {
    ALPHA_ASC("A-Z"),
    ALPHA_DESC("Z-A"),
    NEWEST("NEW")
}

data class CategoryItem(val categoryId: String, val categoryName: String)

data class MovieItem(
    val streamId: String,
    val title: String,
    val categoryId: String,
    val coverUrl: String? = null,
    val releaseDate: String? = null,
    val addedDate: String? = null,
    val containerExtension: String? = "mp4"
)

data class MovieDetailInfo(
    val streamId: String,
    val name: String,
    val cover: String?,
    val plot: String?,
    val director: String?,
    val cast: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val duration: String?,
    val containerExtension: String
)

@Composable
fun MoviesScreen(
    account: Any?,
    timezoneId: String = "Europe/Rome",
    initialSelectedMovie: MovieItem? = null,
    onDetailMovieChange: ((MovieItem?) -> Unit)? = null,
    onBack: () -> Unit,
    onMovieSelect: (MovieItem, startPositionMs: Long) -> Unit
) {
    val context = LocalContext.current
    val watchPrefs = remember { WatchPreferences(context) }
    val moviesGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = MoviesScrollStateHolder.index,
        initialFirstVisibleItemScrollOffset = MoviesScrollStateHolder.offset
    )

    LaunchedEffect(moviesGridState) {
        snapshotFlow { Pair(moviesGridState.firstVisibleItemIndex, moviesGridState.firstVisibleItemScrollOffset) }
            .collect { (idx, off) ->
                MoviesScrollStateHolder.index = idx
                MoviesScrollStateHolder.offset = off
            }
    }

    val initialGridFocusRequester = remember { FocusRequester() }

    var categories by remember { mutableStateOf<List<CategoryItem>>(emptyList()) }
    var moviesList by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var selectedMovieForDetail by remember { mutableStateOf<MovieItem?>(initialSelectedMovie) }

    var selectedCategoryId by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(MovieSortMode.ALPHA_ASC) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Lista Preferiti
    var favoriteIds by remember { mutableStateOf(watchPrefs.getFavorites()) }

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

    val accountKey = remember(serverUrl, username) { "${serverUrl}_$username" }
    val cacheManager = remember { IptvCacheManager(context) }
    val shouldRefresh = remember { cacheManager.shouldRefresh(context, accountKey) }

    BackHandler {
        if (selectedMovieForDetail != null) {
            selectedMovieForDetail = null
            onDetailMovieChange?.invoke(null)
        } else {
            onBack()
        }
    }

    LaunchedEffect(selectedMovieForDetail) {
        onDetailMovieChange?.invoke(selectedMovieForDetail)
    }

    // Reset dello scroll della griglia ad ogni cambio INTERATTIVO di ordinamento o categoria
    var isInitialSortLaunch by remember { mutableStateOf(true) }
    LaunchedEffect(sortMode, selectedCategoryId) {
        if (isInitialSortLaunch) {
            isInitialSortLaunch = false
        } else {
            try {
                moviesGridState.scrollToItem(0)
            } catch (_: Exception) {}
        }
    }

    // Scarica Categorie e Film
    LaunchedEffect(account) {
        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            errorMessage = "Dati account non validi."
            isLoading = false
            return@LaunchedEffect
        }

        if (!shouldRefresh) {
            withContext(Dispatchers.IO) {
                val cached = cacheManager.getMoviesCache(accountKey)
                if (cached != null && cached.second.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        categories = cached.first
                        moviesList = cached.second
                        isLoading = false
                    }
                }
            }
            if (!isLoading) return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            try {
                val catList = mutableListOf<CategoryItem>()
                catList.add(CategoryItem("ALL", "TUTTI I FILM"))
                catList.add(CategoryItem("FAVORITES", "PREFERITI"))
                catList.add(CategoryItem("CONTINUE", "CONTINUA A GUARDARE"))
                catList.add(CategoryItem("RECENT", "AGGIUNTI DI RECENTE"))

                // 1. Categorie Film
                val catUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_vod_categories"
                val connCat = URL(catUrl).openConnection() as HttpURLConnection
                connCat.connectTimeout = 10000
                connCat.readTimeout = 10000
                if (connCat.responseCode == 200) {
                    val res = connCat.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (res.startsWith("[")) {
                        val jsonArr = JSONArray(res)
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            catList.add(CategoryItem(obj.optString("category_id"), obj.optString("category_name")))
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    categories = catList
                }

                // 2. Film VOD
                val vodUrl = "$serverUrl/player_api.php?username=$username&password=$password&action=get_vod_streams"
                val connVod = URL(vodUrl).openConnection() as HttpURLConnection
                connVod.connectTimeout = 15000
                connVod.readTimeout = 15000
                if (connVod.responseCode == 200) {
                    val res = connVod.inputStream.bufferedReader().use { it.readText() }.trim()
                    val movieList = mutableListOf<MovieItem>()

                    if (res.startsWith("[")) {
                        val jsonArr = JSONArray(res)
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            val rawIcon = obj.optString("stream_icon").ifEmpty { obj.optString("cover") }
                            val fullIcon = getAbsoluteImageUrl(serverUrl, rawIcon)
                            val addedStr = obj.optString("added").ifEmpty { obj.optString("release_date") }

                            movieList.add(
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

                    cacheManager.saveMoviesCache(accountKey, catList, movieList)
                    withContext(Dispatchers.Main) {
                        moviesList = movieList
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Errore caricamento film HTTP ${connVod.responseCode}"
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Impossibile caricare i film: ${e.localizedMessage ?: "Errore di connessione"}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    val selectedCategoryName = remember(selectedCategoryId, categories) {
        when (selectedCategoryId) {
            "CONTINUE" -> "CONTINUA A GUARDARE"
            "FAVORITES" -> "PREFERITI"
            "RECENT" -> "AGGIUNTI DI RECENTE"
            "ALL" -> "TUTTI I FILM"
            else -> categories.find { it.categoryId == selectedCategoryId }?.categoryName?.uppercase() ?: "FILM"
        }
    }

    val filteredMovies = remember(moviesList, selectedCategoryId, searchQuery, favoriteIds, sortMode) {
        val continueWatchingIds = watchPrefs.getContinueWatchingIds()

        moviesList
            .filter { movie ->
                val matchesCategory = when (selectedCategoryId) {
                    "ALL" -> true
                    "CONTINUE" -> continueWatchingIds.contains(movie.streamId)
                    "FAVORITES" -> favoriteIds.contains(movie.streamId)
                    "RECENT" -> true
                    else -> movie.categoryId == selectedCategoryId
                }
                val titleStr = movie.title ?: ""
                val matchesSearch = searchQuery.isBlank() || titleStr.contains(searchQuery, ignoreCase = true)
                matchesCategory && matchesSearch
            }
            .let { list ->
                if (selectedCategoryId == "RECENT") {
                    list.sortedByDescending { m ->
                        val t = parseAddedTimestamp(m.addedDate)
                        val id = (m.streamId ?: "").toLongOrNull() ?: 0L
                        if (t > 0L) t else id
                    }
                } else {
                    when (sortMode) {
                        MovieSortMode.ALPHA_ASC -> list.sortedBy { m -> (m.title ?: "").lowercase(Locale.getDefault()) }
                        MovieSortMode.ALPHA_DESC -> list.sortedByDescending { m -> (m.title ?: "").lowercase(Locale.getDefault()) }
                        MovieSortMode.NEWEST -> list.sortedByDescending { m ->
                            val t = parseAddedTimestamp(m.addedDate)
                            val id = (m.streamId ?: "").toLongOrNull() ?: 0L
                            if (t > 0L) t else id
                        }
                    }
                }
            }
    }

    // Auto-focus sul primo film all'apertura dello schermo o ripristino posizione di scorrimento al ritorno dal dettaglio
    LaunchedEffect(isLoading, filteredMovies.size, selectedMovieForDetail) {
        if (!isLoading && filteredMovies.isNotEmpty() && selectedMovieForDetail == null) {
            delay(150L)
            try {
                if (MoviesScrollStateHolder.index > 0) {
                    moviesGridState.scrollToItem(MoviesScrollStateHolder.index, MoviesScrollStateHolder.offset)
                }
                initialGridFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (selectedMovieForDetail != null) {
        MovieDetailContent(
            movie = selectedMovieForDetail!!,
            serverUrl = serverUrl,
            username = username,
            password = password,
            timezoneId = timezoneId,
            onBack = {
                selectedMovieForDetail = null
                onDetailMovieChange?.invoke(null)
                favoriteIds = watchPrefs.getFavorites()
            },
            onMoviePlay = onMovieSelect
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF040C1A))
        ) {
            MovieTopHeaderBar(
                selectedCategoryName = selectedCategoryName,
                timezoneId = timezoneId,
                onBack = onBack,
                sortModeLabel = if (selectedCategoryId == "RECENT") "NEW" else sortMode.label,
                onSortToggle = {
                    sortMode = when (sortMode) {
                        MovieSortMode.ALPHA_ASC -> MovieSortMode.ALPHA_DESC
                        MovieSortMode.ALPHA_DESC -> MovieSortMode.NEWEST
                        MovieSortMode.NEWEST -> MovieSortMode.ALPHA_ASC
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
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFFFC107), modifier = Modifier.size(30.dp))
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(categories, key = { _, cat -> cat.categoryId }, contentType = { _, _ -> "category" }) { _, cat ->
                                val isSelected = cat.categoryId == selectedCategoryId
                                MovieCategorySidebarItem(
                                    category = cat,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedCategoryId = cat.categoryId
                                        if (cat.categoryId == "RECENT") {
                                            sortMode = MovieSortMode.NEWEST
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
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFC107),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (filteredMovies.isEmpty()) {
                        Text(
                            text = "Nessun film trovato in questa sezione",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                            state = moviesGridState,
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(filteredMovies, key = { _, movie -> movie.streamId }, contentType = { _, _ -> "movie" }) { idx, movie ->
                                val isFirstItem = idx == 0
                                val isFav = favoriteIds.contains(movie.streamId)
                                val pos = watchPrefs.getPosition(movie.streamId)
                                val dur = watchPrefs.getDuration(movie.streamId)

                                MovieCardItem(
                                    movie = movie,
                                    isFavorite = isFav,
                                    posMs = pos,
                                    durMs = dur,
                                    onToggleFavorite = {
                                        watchPrefs.toggleFavorite(movie.streamId)
                                        favoriteIds = watchPrefs.getFavorites()
                                    },
                                    onClick = {
                                        selectedMovieForDetail = movie
                                        onDetailMovieChange?.invoke(movie)
                                    },
                                    modifier = if (isFirstItem) Modifier.focusRequester(initialGridFocusRequester) else Modifier
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
fun MovieDetailContent(
    movie: MovieItem,
    serverUrl: String,
    username: String,
    password: String,
    timezoneId: String,
    onBack: () -> Unit,
    onMoviePlay: (MovieItem, startPositionMs: Long) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val watchPrefs = remember { WatchPreferences(context) }

    var isFavorite by remember { mutableStateOf(watchPrefs.isFavorite(movie.streamId)) }
    var detailInfo by remember { mutableStateOf<MovieDetailInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val detailFocusRequester = remember { FocusRequester() }

    // Assegna il focus al tasto Indietro della schermata di dettaglio appena i dati terminano il caricamento
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            delay(150L)
            try {
                detailFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Dialog per ripresa visione
    var showResumeDialog by remember { mutableStateOf(false) }
    var savedProgressMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(movie.streamId) {
        withContext(Dispatchers.IO) {
            try {
                val url = "$serverUrl/player_api.php?username=$username&password=$password&action=get_vod_info&vod_id=${movie.streamId}"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (res.startsWith("{")) {
                        val jsonObj = org.json.JSONObject(res)
                        val infoObj = jsonObj.optJSONObject("info")
                        val movieDataObj = jsonObj.optJSONObject("movie_data")

                        val rawCover = infoObj?.optString("cover_big", infoObj.optString("movie_image", movie.coverUrl ?: "")) ?: movie.coverUrl
                        val fullCover = getAbsoluteImageUrl(serverUrl, rawCover)

                        val info = MovieDetailInfo(
                            streamId = movie.streamId,
                            name = infoObj?.optString("name", movie.title) ?: movie.title,
                            cover = fullCover,
                            plot = infoObj?.optString("plot", "Nessuna trama disponibile."),
                            director = infoObj?.optString("director", "-"),
                            cast = infoObj?.optString("cast", "-"),
                            genre = infoObj?.optString("genre", "-"),
                            releaseDate = infoObj?.optString("releasedate", movie.releaseDate ?: ""),
                            rating = infoObj?.optString("rating", "0"),
                            duration = infoObj?.optString("duration", ""),
                            containerExtension = movieDataObj?.optString("container_extension", movie.containerExtension ?: "mp4") ?: "mp4"
                        )
                        withContext(Dispatchers.Main) {
                            detailInfo = info
                        }
                    }
                }
            } catch (_: Throwable) {
                val fallback = MovieDetailInfo(
                    streamId = movie.streamId,
                    name = movie.title,
                    cover = movie.coverUrl,
                    plot = "Nessuna trama disponibile.",
                    director = "-",
                    cast = "-",
                    genre = "-",
                    releaseDate = movie.releaseDate,
                    rating = "0",
                    duration = "",
                    containerExtension = movie.containerExtension ?: "mp4"
                )
                withContext(Dispatchers.Main) {
                    detailInfo = fallback
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    val savedPos = watchPrefs.getPosition(movie.streamId)

    if (showResumeDialog) {
        val minutes = (savedProgressMs / 1000) / 60
        val seconds = (savedProgressMs / 1000) % 60
        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text("Riprendere la visione?", color = Color.White) },
            text = {
                Text(
                    "Hai già iniziato a guardare '${movie.title}'.\nVuoi continuare da $formattedTime o ricominciare dall'inizio?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResumeDialog = false
                        onMoviePlay(movie, savedProgressMs)
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
                        watchPrefs.clearPosition(movie.streamId)
                        showResumeDialog = false
                        onMoviePlay(movie, 0L)
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
    ) {
        MovieTopHeaderBar(
            selectedCategoryName = detailInfo?.name ?: movie.title,
            timezoneId = timezoneId,
            onBack = onBack,
            sortModeLabel = "",
            onSortToggle = { },
            onSearchToggle = { },
            isSearchActive = false,
            searchQuery = "",
            onSearchQueryChange = { },
            focusRequester = detailFocusRequester
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFC107))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.width(260.dp)) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                    ) {
                        AsyncImage(
                            model = detailInfo?.cover ?: movie.coverUrl,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = detailInfo?.name ?: movie.title,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            var isFavBtnFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    watchPrefs.toggleFavorite(movie.streamId)
                                    isFavorite = watchPrefs.isFavorite(movie.streamId)
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

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!detailInfo?.releaseDate.isNullOrBlank()) {
                                Text(text = "Data: ${detailInfo?.releaseDate}", color = Color(0xFFFFC107), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            if (!detailInfo?.duration.isNullOrBlank()) {
                                Text(text = "Durata: ${detailInfo?.duration}", color = Color.LightGray, fontSize = 13.sp)
                            }
                            if (!detailInfo?.rating.isNullOrBlank() && detailInfo?.rating != "0") {
                                Text(text = "⭐ ${detailInfo?.rating}", color = Color(0xFFFFC107), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "Genere: ${detailInfo?.genre ?: "-"}",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Regia: ${detailInfo?.director ?: "-"}",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Cast: ${detailInfo?.cast ?: "-"}",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = detailInfo?.plot ?: "Nessuna trama disponibile",
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var isPlayFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            if (savedPos > 10_000) {
                                savedProgressMs = savedPos
                                showResumeDialog = true
                            } else {
                                onMoviePlay(movie, 0L)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlayFocused) Color.White else Color(0xFFFFC107)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .onFocusChanged { isPlayFocused = it.isFocused }
                            .border(if (isPlayFocused) BorderStroke(3.dp, Color(0xFFFFC107)) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (savedPos > 10_000) "RIPRENDI VISIONE" else "RIPRODUCI FILM",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieTopHeaderBar(
    selectedCategoryName: String,
    timezoneId: String = "Europe/Rome",
    onBack: () -> Unit,
    sortModeLabel: String,
    onSortToggle: () -> Unit,
    onSearchToggle: () -> Unit,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester? = null
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
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
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
                Text("CATEGORIE FILM", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Cerca film...", color = Color.Gray, fontSize = 12.sp) },
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

        if (sortModeLabel.isNotEmpty()) {
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
fun MovieCategorySidebarItem(
    category: CategoryItem,
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
            if (category.categoryId == "ALL") {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFFFC107) else Color(0xFF00B0FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "= ${category.categoryName}",
                    color = if (isFocused) Color(0xFFFFC107) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (category.categoryId == "FAVORITES") {
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
            } else if (category.categoryId == "CONTINUE") {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFFFC107) else Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category.categoryName,
                    color = if (isFocused) Color(0xFFFFC107) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (category.categoryId == "RECENT") {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFFFC107) else Color(0xFFFF9800),
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
fun MovieCardItem(
    movie: MovieItem,
    isFavorite: Boolean,
    posMs: Long,
    durMs: Long,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
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
                if (!movie.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.coverUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

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

                // Titolo overlay in basso
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(if (isFocused) Color(0xFF00B0FF).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = movie.title.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Indicatore di progresso (Continua a guardare)
                if (durMs > 0 && posMs > 0) {
                    val progress = (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFFFC107),
                        trackColor = Color(0xFF333333)
                    )
                }
            }
        }
    }
}
