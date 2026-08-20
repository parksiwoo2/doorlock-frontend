package com.example.doorlock

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        val sessionToken = RelayStatusStore.sessionToken(context)
        val relayPhase = RelayStatusStore.relayPhase(context)
        results
            .mapNotNull { result ->
                classifySignal(result, studentId, sessionToken, relayPhase)
                    ?.let { signal -> signal to result.rssi }
            }
            .distinctBy { it.first.type }
            .forEach { (detectedSignal, rssi) ->
                forwardSignal(context, detectedSignal, studentId, rssi)
            }
    }

    private fun classifySignal(
        result: ScanResult,
        studentId: String?,
        sessionToken: Int?,
        relayPhase: RelayStatusStore.RelayPhase
    ): DetectedSignal? {
        val scanRecord = result.scanRecord ?: return null
        val serviceData = scanRecord.serviceData
        val hasEntrySignal =
            scanRecord.serviceUuids?.contains(BleConstants.targetParcelUuid) == true ||
            serviceData.containsKey(BleConstants.targetParcelUuid)
        val openSessionToken = studentId?.let {
            BlePayloadCodec.openSessionToken(serviceData[BleConstants.openParcelUuid], it)
        }
        val hasHeartbeatSignal = sessionToken != null &&
            BlePayloadCodec.matchesHeartbeatRoster(
                serviceData[BleConstants.heartbeatParcelUuid],
                sessionToken
            )
        val signalType = selectSignalForPhase(
            relayPhase,
            hasEntrySignal,
            openSessionToken != null,
            hasHeartbeatSignal
        ) ?: return null
        val detectedSessionToken = when (signalType) {
            SignalType.ENTRY -> null
            SignalType.OPEN_CONFIRMED -> openSessionToken
            SignalType.HEARTBEAT -> sessionToken
        }
        return DetectedSignal(signalType, detectedSessionToken)
    }

    private fun forwardSignal(
        context: Context,
        detectedSignal: DetectedSignal,
        studentId: String?,
        rssi: Int
    ) {
        val signal = detectedSignal.type
        val now = SystemClock.elapsedRealtime()
        if (!signal.shouldForward(now)) return

        val serviceIntent = Intent(context, BleRelayService::class.java)
            .setAction(signal.serviceAction)
        when (signal) {
            SignalType.ENTRY -> Unit
            SignalType.OPEN_CONFIRMED -> {
                if (studentId == null || detectedSignal.sessionToken == null) return
                serviceIntent.putExtra(BleRelayService.EXTRA_STUDENT_ID, studentId)
                serviceIntent.putExtra(
                    BleRelayService.EXTRA_SESSION_TOKEN,
                    detectedSignal.sessionToken
                )
            }
            SignalType.HEARTBEAT -> {
                if (detectedSignal.sessionToken == null) return
                serviceIntent.putExtra(
                    BleRelayService.EXTRA_SESSION_TOKEN,
                    detectedSignal.sessionToken
                )
            }
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

    private data class DetectedSignal(
        val type: SignalType,
        val sessionToken: Int?
    )

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
