package com.example.iptv

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("iptv_settings_prefs", Context.MODE_PRIVATE)

    fun getTimezone(): String = prefs.getString("key_timezone", "Europe/Rome") ?: "Europe/Rome"
    fun saveTimezone(value: String) = prefs.edit().putString("key_timezone", value).apply()

    fun getPlayer(): String = prefs.getString("key_player", "ExoPlayer (Consigliato)") ?: "ExoPlayer (Consigliato)"
    fun savePlayer(value: String) = prefs.edit().putString("key_player", value).apply()

    fun getStreamFormat(): String = prefs.getString("key_stream_format", "M3U8 (HLS)") ?: "M3U8 (HLS)"
    fun saveStreamFormat(value: String) = prefs.edit().putString("key_stream_format", value).apply()

    fun getAutoUpdate(): String = prefs.getString("key_auto_update", "Abilitato all'avvio") ?: "Abilitato all'avvio"
    fun saveAutoUpdate(value: String) = prefs.edit().putString("key_auto_update", value).apply()

    fun getAutostart(): Boolean = prefs.getBoolean("key_autostart", false)
    fun saveAutostart(value: Boolean) = prefs.edit().putBoolean("key_autostart", value).apply()

    fun resetDefaults() {
        prefs.edit().clear().apply()
    }
}