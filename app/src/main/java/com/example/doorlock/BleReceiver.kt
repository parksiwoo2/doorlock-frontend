package com.example.doorlock

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.os.SystemClock
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
            Log.w(tag, "BLE 콜백 결과가 비어 있습니다.")
            return
        }

        val studentId = RelayStatusStore.studentId(context)
        val expectedStudentIdData = studentId?.toByteArray(Charsets.US_ASCII)
        val relayPhase = RelayStatusStore.relayPhase(context)
        results
            .mapNotNull { result ->
                classifySignal(result, expectedStudentIdData, relayPhase)
                    ?.let { signal -> signal to result.rssi }
            }
            .distinctBy { it.first }
            .forEach { (signal, rssi) ->
                forwardSignal(context, signal, studentId, rssi)
            }
    }

    private fun classifySignal(
        result: ScanResult,
        expectedStudentIdData: ByteArray?,
        relayPhase: RelayStatusStore.RelayPhase
    ): SignalType? {
        val scanRecord = result.scanRecord ?: return null
        val serviceData = scanRecord.serviceData
        val hasEntrySignal =
            scanRecord.serviceUuids?.contains(BleConstants.targetParcelUuid) == true ||
            serviceData.containsKey(BleConstants.targetParcelUuid)
        val hasOpenSignal = matchesRaspberrySignal(
            scanRecord,
            BleConstants.openParcelUuid,
            expectedStudentIdData
        )
        val hasHeartbeatSignal = matchesRaspberrySignal(
            scanRecord,
            BleConstants.heartbeatParcelUuid,
            expectedStudentIdData
        )
        return selectSignalForPhase(
            relayPhase,
            hasEntrySignal,
            hasOpenSignal,
            hasHeartbeatSignal
        )
    }

    private fun matchesRaspberrySignal(
        scanRecord: ScanRecord,
        serviceUuid: ParcelUuid,
        expectedStudentIdData: ByteArray?
    ): Boolean {
        val serviceData = scanRecord.serviceData
        return expectedStudentIdData != null &&
            serviceData[serviceUuid]?.contentEquals(expectedStudentIdData) == true
    }

    private fun forwardSignal(
        context: Context,
        signal: SignalType,
        studentId: String?,
        rssi: Int
    ) {
        val now = SystemClock.elapsedRealtime()
        if (!signal.shouldForward(now)) return

        val serviceIntent = Intent(context, BleRelayService::class.java)
            .setAction(signal.serviceAction)
        if (signal != SignalType.ENTRY && studentId != null) {
            serviceIntent.putExtra(BleRelayService.EXTRA_STUDENT_ID, studentId)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(tag, "${signal.logName} 전달, RSSI $rssi dBm")
        } catch (exception: RuntimeException) {
            val failure = "BLE 신호 전달 실패: ${exception.javaClass.simpleName} - ${exception.message}"
            RelayStatusStore.addEvent(context, failure)
            Log.e(tag, failure, exception)
        }
    }

    internal enum class SignalType(
        val serviceAction: String,
        val logName: String
    ) {
        ENTRY(BleRelayService.ACTION_ENTRY_SIGNAL, "0312 진입 신호"),
        OPEN_CONFIRMED(BleRelayService.ACTION_OPEN_CONFIRMED, "2222 문 열림 확인"),
        HEARTBEAT(BleRelayService.ACTION_HEARTBEAT, "3333 heartbeat");

        fun shouldForward(now: Long): Boolean {
            val previous = when (this) {
                ENTRY -> lastEntryForwardedAt
                OPEN_CONFIRMED -> lastOpenForwardedAt
                HEARTBEAT -> lastHeartbeatForwardedAt
            }
            if (now - previous < signalForwardIntervalMillis) return false
            when (this) {
                ENTRY -> lastEntryForwardedAt = now
                OPEN_CONFIRMED -> lastOpenForwardedAt = now
                HEARTBEAT -> lastHeartbeatForwardedAt = now
            }
            return true
        }
    }

    companion object {
        const val ACTION_SCAN_RESULT = "com.example.doorlock.action.SCAN_RESULT"
        private const val signalForwardIntervalMillis = 1_000L
        private const val tag = "BleReceiver"

        @Volatile
        private var lastEntryForwardedAt = 0L

        @Volatile
        private var lastOpenForwardedAt = 0L

        @Volatile
        private var lastHeartbeatForwardedAt = 0L

        internal fun selectSignalForPhase(
            relayPhase: RelayStatusStore.RelayPhase,
            hasEntrySignal: Boolean,
            hasOpenSignal: Boolean,
            hasHeartbeatSignal: Boolean
        ): SignalType? = when (relayPhase) {
            RelayStatusStore.RelayPhase.WATCHING_0312 ->
                SignalType.ENTRY.takeIf { hasEntrySignal }
            RelayStatusStore.RelayPhase.REQUESTING_OPEN ->
                SignalType.OPEN_CONFIRMED.takeIf { hasOpenSignal }
            RelayStatusStore.RelayPhase.INSIDE_ROOM ->
                SignalType.HEARTBEAT.takeIf { hasHeartbeatSignal }
        }
    }
}
