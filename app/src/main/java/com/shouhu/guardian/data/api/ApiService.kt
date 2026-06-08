package com.shouhu.guardian.data.api

import com.shouhu.guardian.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // ====== 认证 ======
    @POST("auth/send-code")
    suspend fun sendVerificationCode(@Body body: SendCodeRequest): Response<SendCodeResponse>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    // ====== 紧急联系人 ======
    @GET("contacts")
    suspend fun getContacts(): Response<ApiResponse<List<EmergencyContact>>>

    @POST("contacts")
    suspend fun addContact(@Body contact: EmergencyContact): Response<ApiResponse<EmergencyContact>>

    @PUT("contacts/{id}")
    suspend fun updateContact(@Path("id") id: Int, @Body contact: EmergencyContact): Response<ApiResponse<EmergencyContact>>

    @DELETE("contacts/{id}")
    suspend fun deleteContact(@Path("id") id: Int): Response<ApiResponse<Any>>

    // ====== 紧急事件 ======
    @POST("emergency/start")
    @Headers("Content-Type: application/json")
    suspend fun startEmergency(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ApiResponse<EmergencyEvent>>

    @POST("emergency/stop")
    suspend fun stopEmergency(): Response<ApiResponse<Any>>

    @GET("emergency/status")
    suspend fun getEmergencyStatus(): Response<ApiResponse<EmergencyEvent?>>

    // ====== 位置 ======
    @POST("location/report")
    suspend fun reportLocation(@Body location: LocationData): Response<ApiResponse<Any>>

    // ====== 设置 ======
    @GET("settings")
    suspend fun getSettings(): Response<ApiResponse<GuardianSettings>>

    @PUT("settings")
    suspend fun updateSettings(@Body settings: GuardianSettings): Response<ApiResponse<GuardianSettings>>
}
