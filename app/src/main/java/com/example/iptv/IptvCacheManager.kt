package com.example.iptv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class IptvCacheManager(context: Context) {
    private val prefs = context.getSharedPreferences("iptv_cache_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getLastSyncTime(accountKey: String): Long {
        return prefs.getLong("sync_time_$accountKey", 0L)
    }

    fun setLastSyncTime(accountKey: String, time: Long) {
        prefs.edit().putLong("sync_time_$accountKey", time).apply()
    }

    fun saveLiveCache(accountKey: String, categories: List<LiveCategory>, channels: List<LiveChannel>) {
        val catJson = gson.toJson(categories)
        val chanJson = gson.toJson(channels)
        prefs.edit()
            .putString("live_cat_$accountKey", catJson)
            .putString("live_chan_$accountKey", chanJson)
            .putLong("sync_time_$accountKey", System.currentTimeMillis())
            .apply()
    }

    fun getLiveCache(accountKey: String): Pair<List<LiveCategory>, List<LiveChannel>>? {
        val catJson = prefs.getString("live_cat_$accountKey", null) ?: return null
        val chanJson = prefs.getString("live_chan_$accountKey", null) ?: return null
        return try {
            val catType = object : TypeToken<List<LiveCategory>>() {}.type
            val chanType = object : TypeToken<List<LiveChannel>>() {}.type
            val cats: List<LiveCategory> = gson.fromJson(catJson, catType) ?: emptyList()
            val chans: List<LiveChannel> = gson.fromJson(chanJson, chanType) ?: emptyList()
            Pair(cats, chans)
        } catch (_: Exception) {
            null
        }
    }

    fun saveMoviesCache(accountKey: String, categories: List<CategoryItem>, movies: List<MovieItem>) {
        val catJson = gson.toJson(categories)
        val movJson = gson.toJson(movies)
        prefs.edit()
            .putString("mov_cat_$accountKey", catJson)
            .putString("mov_item_$accountKey", movJson)
            .putLong("sync_time_$accountKey", System.currentTimeMillis())
            .apply()
    }

    fun getMoviesCache(accountKey: String): Pair<List<CategoryItem>, List<MovieItem>>? {
        val catJson = prefs.getString("mov_cat_$accountKey", null) ?: return null
        val movJson = prefs.getString("mov_item_$accountKey", null) ?: return null
        return try {
            val catType = object : TypeToken<List<CategoryItem>>() {}.type
            val movType = object : TypeToken<List<MovieItem>>() {}.type
            val cats: List<CategoryItem> = gson.fromJson(catJson, catType) ?: emptyList()
            val movs: List<MovieItem> = gson.fromJson(movJson, movType) ?: emptyList()
            Pair(cats, movs)
        } catch (_: Exception) {
            null
        }
    }

    fun saveSeriesCache(accountKey: String, categories: List<SeriesCategory>, series: List<SeriesData>) {
        val catJson = gson.toJson(categories)
        val serJson = gson.toJson(series)
        prefs.edit()
            .putString("ser_cat_$accountKey", catJson)
            .putString("ser_item_$accountKey", serJson)
            .putLong("sync_time_$accountKey", System.currentTimeMillis())
            .apply()
    }

    fun getSeriesCache(accountKey: String): Pair<List<SeriesCategory>, List<SeriesData>>? {
        val catJson = prefs.getString("ser_cat_$accountKey", null) ?: return null
        val serJson = prefs.getString("ser_item_$accountKey", null) ?: return null
        return try {
            val catType = object : TypeToken<List<SeriesCategory>>() {}.type
            val serType = object : TypeToken<List<SeriesData>>() {}.type
            val cats: List<SeriesCategory> = gson.fromJson(catJson, catType) ?: emptyList()
            val sers: List<SeriesData> = gson.fromJson(serJson, serType) ?: emptyList()
            Pair(cats, sers)
        } catch (_: Exception) {
            null
        }
    }

    fun clearAll(accountKey: String) {
        prefs.edit()
            .remove("sync_time_$accountKey")
            .remove("live_cat_$accountKey")
            .remove("live_chan_$accountKey")
            .remove("mov_cat_$accountKey")
            .remove("mov_item_$accountKey")
            .remove("ser_cat_$accountKey")
            .remove("ser_item_$accountKey")
            .apply()
    }

    fun shouldRefresh(context: Context, accountKey: String): Boolean {
        val appPrefs = AppPreferences(context)
        val autoUpdateSetting = appPrefs.getAutoUpdate()
        val lastSync = getLastSyncTime(accountKey)

        if (lastSync == 0L) return true

        val now = System.currentTimeMillis()
        val diff = now - lastSync

        return when (autoUpdateSetting) {
            "Ogni 6 ore" -> diff > 6 * 3600 * 1000L
            "Ogni 12 ore" -> diff > 12 * 3600 * 1000L
            "Ogni 24 ore" -> diff > 24 * 3600 * 1000L
            "Mai (Solo manuale)" -> false
            else -> false // "All'avvio dell'app" o default -> usa cache se già scaricati in questa sessione
        }
    }
}
