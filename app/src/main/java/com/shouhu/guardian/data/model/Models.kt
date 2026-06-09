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
    @SerializedName("verification_code") val verificationCode: String
)
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: Int,
    val email: String,
    val group: String = "default"
)

// ====== 联系人 ======
data class ContactRequest(
    val name: String,
    val phone: String,
    val relation: String? = null,
    val priority: Int = 1
)
data class ContactResponse(
    val id: Int,
    val name: String,
    val phone: String,
    val relation: String?,
    val priority: Int
)

// ====== 报警 ======
data class EmergencyRequest(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    @SerializedName("device_info") val deviceInfo: Map<String, String>? = null
)
data class ContactNotified(
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("contact_phone") val contactPhone: String,
    @SerializedName("sms_sent") val smsSent: Boolean,
    @SerializedName("call_made") val callMade: Boolean
)
data class EmergencyResponse(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val status: String,
    @SerializedName("triggered_at") val triggeredAt: String,
    @SerializedName("resolved_at") val resolvedAt: String?,
    @SerializedName("contacts_notified") val contactsNotified: List<ContactNotified>
)

// ====== 设置 ======
data class SettingsResponse(
    @SerializedName("safe_password") val safePassword: String,
    @SerializedName("trigger_volume_key") val triggerVolumeKey: Boolean,
    @SerializedName("trigger_voice") val triggerVoice: Boolean,
    @SerializedName("trigger_widget") val triggerWidget: Boolean,
    @SerializedName("auto_record") val autoRecord: Boolean,
    @SerializedName("auto_gps") val autoGps: Boolean
)
data class SettingsUpdateRequest(
    @SerializedName("safe_password") val safePassword: String? = null,
    @SerializedName("trigger_volume_key") val triggerVolumeKey: Boolean? = null,
    @SerializedName("trigger_voice") val triggerVoice: Boolean? = null,
    @SerializedName("trigger_widget") val triggerWidget: Boolean? = null,
    @SerializedName("auto_record") val autoRecord: Boolean? = null,
    @SerializedName("auto_gps") val autoGps: Boolean? = null
)

// ====== 通用 ======
data class ApiError(val detail: String)
