package com.shouhu.guardian.data.api

import com.shouhu.guardian.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ====== 认证 ======
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    // ====== 联系人 ======
    @GET("contacts/")
    suspend fun getContacts(): Response<List<ContactResponse>>

    @POST("contacts/")
    suspend fun addContact(@Body body: ContactRequest): Response<ContactResponse>

    @PUT("contacts/{id}")
    suspend fun updateContact(@Path("id") id: Int, @Body body: ContactRequest): Response<ContactResponse>

    @DELETE("contacts/{id}")
    suspend fun deleteContact(@Path("id") id: Int): Response<Unit>

    // ====== 报警 ======
    @POST("emergency/trigger")
    suspend fun triggerEmergency(@Body body: EmergencyRequest): Response<EmergencyResponse>

    @GET("emergency/history")
    suspend fun getAlertHistory(@Query("limit") limit: Int = 30): Response<List<EmergencyResponse>>

    // ====== 设置 ======
    @GET("settings/")
    suspend fun getSettings(): Response<SettingsResponse>

    @PUT("settings/")
    suspend fun updateSettings(@Body body: SettingsUpdateRequest): Response<SettingsResponse>
}
