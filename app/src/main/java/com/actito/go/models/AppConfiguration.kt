package com.actito.go.models

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppConfiguration(
    val applicationKey: String,
    val applicationSecret: String,
    val loyaltyProgramId: String?,
    val environment: Environment = Environment.PRODUCTION
) {
    @Keep
    enum class Environment {
        PRODUCTION,
        TEST;

        val baseUrl: String
            get() = when (this) {
                PRODUCTION -> "https://push.notifica.re"
                TEST -> "https://push-test.notifica.re"
            }
    }
}
