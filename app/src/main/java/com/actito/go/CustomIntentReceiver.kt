package com.actito.go

import android.content.Context
import com.actito.ActitoIntentReceiver
import com.actito.models.ActitoDevice
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics

class CustomIntentReceiver : ActitoIntentReceiver() {
    override fun onDeviceRegistered(context: Context, device: ActitoDevice) {
        Firebase.crashlytics.setUserId("device_${device.id}")
    }
}