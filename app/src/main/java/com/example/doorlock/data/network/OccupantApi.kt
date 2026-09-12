package com.example.doorlock.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

/**
 * v8 명세서 기준 응답 모델.
 * Backend 내부의 studentId/userId/DB 구조는 Android가 알 필요가 없으므로
 * 여기엔 표시용 필드(name)만 존재한다. studentId/occupantCount는 절대 추가하지 않는다.
 */
data class Occupant(
    @SerializedName("name") val name: String
)

data class OccupantsResponse(
    @SerializedName("occupants") val occupants: List<Occupant>,
    @SerializedName("lastSyncedAt") val lastSyncedAt: String
)

interface OccupantApi {
    @GET("occupants")
    suspend fun getOccupants(): OccupantsResponse
}
