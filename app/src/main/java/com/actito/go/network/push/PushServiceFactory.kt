package com.actito.go.network.push

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class PushServiceFactory (
    private val client: OkHttpClient,
    private val moshi: Moshi
) {
    fun createService(baseUrl: String): PushService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(PushService::class.java)
}