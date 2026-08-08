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
    fun register(
        context: Context,
        mode: ScanMode = ScanMode.ENTRY_DETECTION
    ): RegistrationResult {
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
        val filters = buildFilters(context, mode)
            ?: return RegistrationResult(false, "저장된 학번이 없어 BLE 스캔을 등록할 수 없습니다.")
        val settings = ScanSettings.Builder()
            .setScanMode(mode.platformScanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0L)
            .build()
        return try {
            scanner.stopScan(callbackIntent)
            val errorCode = scanner.startScan(filters, settings, callbackIntent)
            val success = errorCode == 0 ||
                errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED
            if (success) {
                RelayStatusStore.setScanRegistered(context, true)
                RelayStatusStore.setRelayPhase(context, mode.relayPhase)
                RegistrationResult(true, mode.successMessage)
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

    private fun buildFilters(context: Context, mode: ScanMode): List<ScanFilter>? = when (mode) {
        ScanMode.ENTRY_DETECTION -> listOf(
            ScanFilter.Builder()
                .setServiceUuid(BleConstants.targetParcelUuid)
                .build()
        )

        ScanMode.OPEN_CONFIRMATION -> buildRaspberrySignalFilters(
            context,
            BleConstants.openParcelUuid
        )

        ScanMode.PRESENCE_MONITORING -> buildRaspberrySignalFilters(
            context,
            BleConstants.heartbeatParcelUuid
        )
    }

    private fun buildRaspberrySignalFilters(
        context: Context,
        serviceUuid: android.os.ParcelUuid
    ): List<ScanFilter>? {
        val studentId = RelayStatusStore.studentId(context) ?: return null
        return listOf(
            ScanFilter.Builder()
                .setServiceData(
                    serviceUuid,
                    studentId.toByteArray(Charsets.US_ASCII)
                )
                .build()
        )
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

    enum class ScanMode(
        val platformScanMode: Int,
        val successMessage: String,
        val relayPhase: RelayStatusStore.RelayPhase
    ) {
        ENTRY_DETECTION(
            ScanSettings.SCAN_MODE_LOW_LATENCY,
            "0312 빠른 진입 감지가 활성화되었습니다.",
            RelayStatusStore.RelayPhase.WATCHING_0312
        ),
        OPEN_CONFIRMATION(
            ScanSettings.SCAN_MODE_LOW_LATENCY,
            "2222 문 열림 확인 감지가 활성화되었습니다.",
            RelayStatusStore.RelayPhase.REQUESTING_OPEN
        ),
        PRESENCE_MONITORING(
            ScanSettings.SCAN_MODE_BALANCED,
            "3333 저전력 근접 확인이 활성화되었습니다.",
            RelayStatusStore.RelayPhase.INSIDE_ROOM
        )
    }

    data class RegistrationResult(val success: Boolean, val message: String)
}
