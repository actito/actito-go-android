package com.actito.go.core

import android.content.Context
import android.net.Uri
import com.actito.Actito
import com.actito.ActitoServicesInfo
import com.actito.assets.ktx.assets
import com.actito.go.models.AppConfiguration
import com.actito.go.storage.preferences.ActitoSharedPreferences
import com.actito.internal.network.NetworkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private val allowedConfigurationHostnames = listOf(
    "go-demo.ntc.re",
    "go-demo-test.test.ntc.re"
)

fun extractConfigurationCode(uri: Uri): String? {
    if (uri.scheme != "https") {
        Timber.w("Invalid URI scheme.")
        return null
    }

    if (!allowedConfigurationHostnames.contains(uri.host)) {
        Timber.w("Invalid URI host.")
        return null
    }

    val code = uri.getQueryParameter("referrer") ?: run {
        Timber.w("Invalid URI code query parameter.")
        return null
    }

    return code
}

fun determineEnvironment(uri: Uri): AppConfiguration.Environment {
    if (uri.host?.endsWith(".test.ntc.re") == true) {
        return AppConfiguration.Environment.TEST
    }

    return AppConfiguration.Environment.PRODUCTION
}

suspend fun loadRemoteConfig(preferences: ActitoSharedPreferences): Unit = withContext(Dispatchers.IO) {
    try {
        val assets = Actito.assets().fetch(group = "config")
        val storeEnabled = assets.firstOrNull()?.extra?.get("storeEnabled") as? Boolean

        if (storeEnabled != null) {
            preferences.hasStoreEnabled = storeEnabled
            return@withContext
        }
    } catch (e: Exception) {
        if (e is NetworkException.ValidationException && e.response.code == 404) {
            // The config asset group is not available. The store can be enabled.
            preferences.hasStoreEnabled = true
            return@withContext
        }

        Timber.e(e, "Failed to fetch the remote config.")
    }

    preferences.hasStoreEnabled = false
}

fun configure(context: Context, configuration: AppConfiguration) {
    val servicesInfo: ActitoServicesInfo

    when(configuration.environment) {
        AppConfiguration.Environment.PRODUCTION ->
            servicesInfo = ActitoServicesInfo(
                applicationKey = configuration.applicationKey,
                applicationSecret = configuration.applicationSecret,
            )
        AppConfiguration.Environment.TEST ->
            servicesInfo = ActitoServicesInfo(
                applicationKey = configuration.applicationKey,
                applicationSecret = configuration.applicationSecret,
                hosts = ActitoServicesInfo.Hosts(
                    restApi = "https://push-test.notifica.re",
                    appLinks = "applinks.test.notifica.re",
                    shortLinks = "test.ntc.re",
                )
            )
    }

    Actito.configure(context, servicesInfo)
}
