package com.example.doorlock

import android.os.ParcelUuid

object BleConstants {
    const val TARGET_UUID = "00000312-0000-1000-8000-00805f9b34fb"
    const val RESPONSE_UUID = "00001111-0000-1000-8000-00805f9b34fb"
    val targetParcelUuid: ParcelUuid = ParcelUuid.fromString(TARGET_UUID)
    val responseParcelUuid: ParcelUuid = ParcelUuid.fromString(RESPONSE_UUID)
}
