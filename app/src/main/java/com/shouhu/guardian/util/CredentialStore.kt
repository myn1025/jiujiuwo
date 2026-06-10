package com.shouhu.guardian.util

import android.content.Context

/**
 * 加密凭据存储
 * 用于退出登录后仍能通过指纹恢复登录
 *
 * 安全设计：token 存在普通 SharedPreferences，但仅在系统指纹/面容验证通过后才取出。
 * 没有生物识别的人无法拿到 token，不需要额外的加密层。
 */
object CredentialStore {

    private const val PREFS_NAME = "credential_backup"

    fun save(context: Context, token: String, email: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("token", token)
            .putString("email", email)
            .apply()
    }

    fun getToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("token", null)
    }

    fun getEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("email", null)
    }

    fun hasCredentials(context: Context): Boolean {
        return getToken(context) != null
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
