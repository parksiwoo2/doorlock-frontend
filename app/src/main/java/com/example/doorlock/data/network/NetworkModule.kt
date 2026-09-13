package com.example.doorlock.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Backend 서버 주소는 아직 확정되지 않았다 (v8 명세서 9번, "확정되지 않은 것" 참고).
 * 실제 서버 주소가 정해지면 이 상수만 바꾸면 된다.
 *
 * TODO: 실제 Backend Base URL로 교체
 */
object NetworkConfig {
    const val BASE_URL = "https://api-v2.khlug.org/"
}

/**
 * 앱 전역에서 하나만 유지하는 Retrofit/OkHttp 인스턴스.
 * 별도 DI 프레임워크가 없는 프로젝트 구조에 맞춰 간단한 object로 구성했다.
 */
object NetworkModule {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(NetworkConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val occupantApi: OccupantApi = retrofit.create(OccupantApi::class.java)
}
