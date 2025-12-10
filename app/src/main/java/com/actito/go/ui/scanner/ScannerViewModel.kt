package com.actito.go.ui.scanner

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.actito.go.core.determineEnvironment
import com.actito.go.core.extractConfigurationCode
import com.actito.go.models.AppConfiguration
import com.actito.go.network.push.PushServiceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val pushServiceFactory: PushServiceFactory,
) : ViewModel() {

    suspend fun fetchConfiguration(barcode: String): AppConfiguration = withContext(Dispatchers.IO) {
        val code = extractConfigurationCode(barcode.toUri())

        if (code == null) {
            Timber.w("Invalid URI code query parameter.")
            throw IllegalArgumentException("Invalid URI code query parameter.")
        }

        val environment = determineEnvironment(barcode.toUri())
        val pushService = pushServiceFactory.createService(environment.baseUrl)

        pushService.getConfiguration(code).let {
            AppConfiguration(
                applicationKey = it.demo.applicationKey,
                applicationSecret = it.demo.applicationSecret,
                loyaltyProgramId = it.demo.loyaltyProgram,
                environment = environment
            )
        }
    }
}
