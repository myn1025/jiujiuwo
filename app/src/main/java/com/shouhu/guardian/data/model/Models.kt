package com.shouhu.guardian.data.model

import com.google.gson.annotations.SerializedName

// ====== 认证 ======
data class LoginRequest(val email: String, val password: String)
data class SendCodeRequest(val email: String)
data class SendCodeResponse(val message: String, val success: Boolean)
data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String,
    @SerializedName("verification_code") val verificationCode: String,
    val phone: String? = null
)
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: Int,
    val email: String,
    val group: String = "default"
)

data class EmergencyContact(val id: Int? = null, val name: String, val phone: String, val order: Int = 0)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val timestamp: Long? = null
)

data class EmergencyEvent(
    val id: Int? = null,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("event_type") val eventType: String = "manual",
    val status: String = "active",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("resolved_at") val resolvedAt: String? = null
)

data class GuardianSettings(
    val id: Int? = null,
    @SerializedName("auto_record_seconds") val autoRecordSeconds: Int = 30,
    @SerializedName("auto_call_enabled") val autoCallEnabled: Boolean = true,
    @SerializedName("voice_wake_enabled") val voiceWakeEnabled: Boolean = false,
    @SerializedName("fake_call_enabled") val fakeCallEnabled: Boolean = false,
    @SerializedName("trigger_methods_raw") val triggerMethodsRaw: String = "volume_long_press",
    @SerializedName("safe_password") val safePassword: String? = null,
    @SerializedName("auto_report_interval") val autoReportInterval: Int = 300
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)

data class ErrorResponse(val detail: String)
