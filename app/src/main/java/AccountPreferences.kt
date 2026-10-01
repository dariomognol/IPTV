package com.example.iptv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// --- MODELLO DATI ACCOUNT ---
data class UserAccount(
    val playlistName: String = "",
    val username: String = "",
    val password: String = "",
    val serverUrl: String = "",
    val url: String = ""
)

class AccountPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("iptv_accounts_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Ottiene la lista di tutti gli utenti salvati
    fun getSavedAccounts(): List<UserAccount> {
        val json = prefs.getString("saved_accounts_list", null) ?: return emptyList()
        val type = object : TypeToken<List<UserAccount>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Ritorna l'account attivo (per compatibilità con il codice esistente)
    fun getSavedAccount(): UserAccount? {
        return getActiveAccount()
    }

    // Ottiene l'utente attualmente attivo
    fun getActiveAccount(): UserAccount? {
        val accounts = getSavedAccounts()
        val activeName = prefs.getString("active_account_name", null)
        return accounts.find { it.playlistName == activeName } ?: accounts.firstOrNull()
    }

    // Salva un nuovo utente AGGIUNGENDOLO alla lista
    fun saveAccount(account: UserAccount) {
        val currentList = getSavedAccounts().toMutableList()
        currentList.removeAll { it.playlistName == account.playlistName }
        currentList.add(account)

        prefs.edit()
            .putString("saved_accounts_list", gson.toJson(currentList))
            .putString("active_account_name", account.playlistName)
            .apply()
    }

    // Cambia il profilo attivo
    fun setActiveAccount(playlistName: String) {
        prefs.edit().putString("active_account_name", playlistName).apply()
    }

    // Rimuove un profilo specifico
    fun removeAccount(playlistName: String) {
        val currentList = getSavedAccounts().toMutableList()
        currentList.removeAll { it.playlistName == playlistName }

        val editor = prefs.edit()
        editor.putString("saved_accounts_list", gson.toJson(currentList))

        if (prefs.getString("active_account_name", null) == playlistName) {
            val nextActive = currentList.firstOrNull()?.playlistName
            editor.putString("active_account_name", nextActive)
        }
        editor.apply()
    }

    // Disconnette il profilo attivo
    fun logout() {
        val active = getActiveAccount()
        if (active != null) {
            removeAccount(active.playlistName)
        }
    }
}