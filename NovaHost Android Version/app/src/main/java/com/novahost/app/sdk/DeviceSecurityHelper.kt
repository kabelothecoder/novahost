package com.novahost.app.sdk

import android.content.Context
import android.provider.Settings

object DeviceSecurityHelper {
    /**
     * @description Fetches the unique ANDROID_ID for the device to lock the license to this hardware.
     */
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"
    }
}
