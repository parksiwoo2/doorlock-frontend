package com.example.doorlock

import android.os.ParcelUuid

object BleConstants {
    const val TARGET_UUID = "00000312-0000-1000-8000-00805f9b34fb"
    const val RESPONSE_UUID = "00001111-0000-1000-8000-00805f9b34fb"
    const val OPEN_UUID = "00002222-0000-1000-8000-00805f9b34fb"
    const val HEARTBEAT_UUID = "00003333-0000-1000-8000-00805f9b34fb"
    val targetParcelUuid: ParcelUuid = ParcelUuid.fromString(TARGET_UUID)
    val responseParcelUuid: ParcelUuid = ParcelUuid.fromString(RESPONSE_UUID)
    val openParcelUuid: ParcelUuid = ParcelUuid.fromString(OPEN_UUID)
    val heartbeatParcelUuid: ParcelUuid = ParcelUuid.fromString(HEARTBEAT_UUID)
}
