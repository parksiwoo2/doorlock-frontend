package com.example.doorlock

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

object BleScanRegistrar {
    fun register(context: Context): RegistrationResult {
        if (!hasScanPermission(context)) {
            return RegistrationResult(false, "BLE 스캔 권한이 없습니다.")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return RegistrationResult(false, "Bluetooth 어댑터를 찾을 수 없습니다.")
        if (!adapter.isEnabled) {
            return RegistrationResult(false, "Bluetooth가 꺼져 있습니다.")
        }
        val scanner = adapter.bluetoothLeScanner
            ?: return RegistrationResult(false, "BLE 스캐너를 사용할 수 없습니다.")
        val callbackIntent = PendingIntent.getBroadcast(
            context,
            312,
            Intent(context, BleReceiver::class.java).setAction(BleReceiver.ACTION_SCAN_RESULT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.targetParcelUuid)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .setReportDelay(0L)
            .build()
        return try {
            val errorCode = scanner.startScan(listOf(filter), settings, callbackIntent)
            val success = errorCode == 0 ||
                errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED
            if (success) {
                RelayStatusStore.setScanRegistered(context, true)
                RegistrationResult(true, "0312 백그라운드 감지가 활성화되었습니다.")
            } else {
                RelayStatusStore.setScanRegistered(context, false)
                RegistrationResult(false, "BLE 스캔 등록 실패: ${scanErrorName(errorCode)}")
            }
        } catch (exception: SecurityException) {
            RelayStatusStore.setScanRegistered(context, false)
            RegistrationResult(false, "BLE 스캔 권한 오류: ${exception.message}")
        }
    }

    private fun hasScanPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun scanErrorName(errorCode: Int): String = when (errorCode) {
        0 -> "NO_ERROR"
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APPLICATION_REGISTRATION_FAILED"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "OUT_OF_HARDWARE_RESOURCES"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "SCANNING_TOO_FREQUENTLY"
        else -> "UNKNOWN($errorCode)"
    }

    data class RegistrationResult(val success: Boolean, val message: String)
}
