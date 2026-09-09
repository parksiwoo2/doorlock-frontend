package com.example.doorlock.data.network

/**
 * GET /occupants 호출을 감싸는 얇은 Repository.
 * v8 명세서 기준으로 Backend 내부 구현(Pi 처리 방식, DB 구조, 인증 방식 등)은 신경 쓰지 않는다.
 */
class OccupancyRepository(
    private val api: OccupantApi = NetworkModule.occupantApi
) {
    suspend fun fetchOccupants(): OccupantsResponse = api.getOccupants()
}
