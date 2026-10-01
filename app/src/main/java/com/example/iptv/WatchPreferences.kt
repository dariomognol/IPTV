package com.example.iptv

import android.content.Context

class WatchPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("watch_prefs", Context.MODE_PRIVATE)

    // --- FAVORITI FILM ---
    fun getFavorites(): Set<String> {
        return prefs.getStringSet("favorites_movies", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(streamId: String) {
        val current = getFavorites().toMutableSet()
        if (current.contains(streamId)) {
            current.remove(streamId)
        } else {
            current.add(streamId)
        }
        prefs.edit().putStringSet("favorites_movies", current).apply()
    }

    fun isFavorite(streamId: String): Boolean {
        return getFavorites().contains(streamId)
    }

    // --- FAVORITI SERIE TV ---
    fun getSeriesFavorites(): Set<String> {
        return prefs.getStringSet("favorites_series", emptySet()) ?: emptySet()
    }

    fun toggleSeriesFavorite(seriesId: String) {
        val current = getSeriesFavorites().toMutableSet()
        if (current.contains(seriesId)) {
            current.remove(seriesId)
        } else {
            current.add(seriesId)
        }
        prefs.edit().putStringSet("favorites_series", current).apply()
    }

    fun isSeriesFavorite(seriesId: String): Boolean {
        return getSeriesFavorites().contains(seriesId)
    }

    // --- FAVORITI LIVE TV ---
    fun getLiveFavorites(): Set<String> {
        return prefs.getStringSet("favorites_live", emptySet()) ?: emptySet()
    }

    fun toggleLiveFavorite(streamId: String) {
        val current = getLiveFavorites().toMutableSet()
        if (current.contains(streamId)) {
            current.remove(streamId)
        } else {
            current.add(streamId)
        }
        prefs.edit().putStringSet("favorites_live", current).apply()
    }

    fun isLiveFavorite(streamId: String): Boolean {
        return getLiveFavorites().contains(streamId)
    }

    // --- CONTINUA A GUARDARE (Posizione in MS) ---
    fun savePosition(streamId: String, positionMs: Long, durationMs: Long) {
        if (durationMs > 0 && positionMs > 10_000 && positionMs < durationMs - 30_000) {
            // Salva solo se è stato visto più di 10s e non è negli ultimi 30 secondi
            prefs.edit()
                .putLong("pos_$streamId", positionMs)
                .putLong("dur_$streamId", durationMs)
                .putLong("time_$streamId", System.currentTimeMillis())
                .apply()
        } else if (positionMs >= durationMs - 30_000) {
            // Se è quasi finito, rimuovi dal "Continua a guardare"
            clearPosition(streamId)
        }
    }

    fun saveSeriesEpisodeMapping(seriesId: String, episodeId: String) {
        prefs.edit().putString("series_of_$episodeId", seriesId).apply()
    }

    fun getContinueWatchingSeriesIds(): Set<String> {
        val continueEpIds = getContinueWatchingIds()
        val result = mutableSetOf<String>()
        for (epId in continueEpIds) {
            val seriesId = prefs.getString("series_of_$epId", null)
            if (seriesId != null) {
                result.add(seriesId)
            } else {
                result.add(epId)
            }
        }
        return result
    }

    fun getPosition(streamId: String): Long {
        return prefs.getLong("pos_$streamId", 0L)
    }

    fun getDuration(streamId: String): Long {
        return prefs.getLong("dur_$streamId", 0L)
    }

    fun clearPosition(streamId: String) {
        prefs.edit()
            .remove("pos_$streamId")
            .remove("dur_$streamId")
            .remove("time_$streamId")
            .remove("series_of_$streamId")
            .apply()
    }

    fun getContinueWatchingIds(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith("pos_") }
            .map { it.removePrefix("pos_") }
            .toSet()
    }
}