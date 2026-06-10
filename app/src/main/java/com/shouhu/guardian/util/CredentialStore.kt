package com.shouhu.guardian.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Android Keystore 加密凭据存储
 * 用于退出登录后仍能通过指纹恢复登录
 */
object CredentialStore {

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "secure_credentials",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, token: String, email: String) {
        getPrefs(context).edit()
            .putString("token", token)
            .putString("email", email)
            .apply()
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString("token", null)
    }

    fun getEmail(context: Context): String? {
        return getPrefs(context).getString("email", null)
    }

    fun hasCredentials(context: Context): Boolean {
        return getToken(context) != null
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
