package com.example.doorlock

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCAN_RESULT) return

        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        if (errorCode != 0) {
            val message = "BLE 수신 오류: ${BleScanRegistrar.scanErrorName(errorCode)}"
            RelayStatusStore.addEvent(context, message)
            Log.e(tag, message)
            return
        }

        @Suppress("DEPRECATION")
        val results = intent.getParcelableArrayListExtra<ScanResult>(
            BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
        ).orEmpty()
        if (results.isEmpty()) {
            RelayStatusStore.addEvent(context, "BLE 콜백을 받았지만 스캔 결과가 비어 있습니다.")
            Log.w(tag, "Empty scan callback")
            return
        }

        val strongestRssi = results.maxOf { it.rssi }
        val message = "0312 신호 감지: ${results.size}개 결과, RSSI ${strongestRssi} dBm"
        RelayStatusStore.addEvent(context, message)
        Log.i(tag, message)

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BleRelayService::class.java)
                    .setAction(BleRelayService.ACTION_START_ADVERTISING)
            )
            RelayStatusStore.addEvent(context, "1111 응답 광고 서비스 시작 요청")
        } catch (exception: RuntimeException) {
            val failure = "백그라운드 광고 서비스 시작 실패: ${exception.javaClass.simpleName} - ${exception.message}"
            RelayStatusStore.addEvent(context, failure)
            Log.e(tag, failure, exception)
        }
    }

    companion object {
        const val ACTION_SCAN_RESULT = "com.example.doorlock.action.SCAN_RESULT"
        private const val tag = "BleReceiver"
    }
}
