package com.example.doorlock

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class BleRelayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var state = SessionState.IDLE
    private var foregroundStarted = false
    private var initialAdvertisingFinished = false
    private var openConfirmed = false
    private var lastHeartbeatAt = 0L

    private val finishInitialAdvertisingTask: Runnable = Runnable {
        if (state != SessionState.WAITING_FOR_OPEN) return@Runnable
        stopAdvertising()
        initialAdvertisingFinished = true
        RelayStatusStore.addEvent(this, "최초 1111 학번 광고 5초 송출 완료")
        if (openConfirmed) beginPresenceMonitoring()
    }

    private val openConfirmationTimeoutTask: Runnable = Runnable {
        if (state == SessionState.WAITING_FOR_OPEN) {
            stopSession("10초 안에 내 학번이 담긴 2222 문 열림 신호를 받지 못했습니다.")
        }
    }

    private val heartbeatTimeoutCheckTask: Runnable = object : Runnable {
        override fun run() {
            if (state != SessionState.MONITORING_PRESENCE) return
            if (hasHeartbeatTimedOut()) {
                stopSession("내 학번이 담긴 3333 신호를 30초 동안 받지 못해 BLE 세션을 종료합니다.")
            } else {
                handler.postDelayed(this, heartbeatCheckIntervalMillis)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ADVERTISING -> {
                stopSession("사용자가 BLE 세션을 중지했습니다.")
            }

            ACTION_ENTRY_SIGNAL -> {
                ensureForegroundStarted()
                if (state == SessionState.IDLE) startEntrySession()
            }

            ACTION_OPEN_CONFIRMED -> {
                ensureForegroundStarted()
                if (!hasMatchingStudentId(intent)) {
                    if (state == SessionState.IDLE) stopSession("학번이 일치하지 않는 2222 신호를 무시했습니다.")
                } else if (state == SessionState.WAITING_FOR_OPEN) {
                    handleOpenConfirmed()
                } else if (state == SessionState.IDLE) {
                    stopSession("활성 세션이 없어 2222 신호를 무시했습니다.")
                }
            }

            ACTION_HEARTBEAT -> {
                ensureForegroundStarted()
                if (!hasMatchingStudentId(intent)) {
                    if (state == SessionState.IDLE) stopSession("학번이 일치하지 않는 3333 신호를 무시했습니다.")
                } else if (state == SessionState.MONITORING_PRESENCE) {
                    lastHeartbeatAt = SystemClock.elapsedRealtime()
                    Log.d(tag, "3333 heartbeat received")
                } else if (state == SessionState.IDLE) {
                    stopSession("활성 세션이 없어 3333 신호를 무시했습니다.")
                }
            }

            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startEntrySession() {
        activeSession = true
        state = SessionState.WAITING_FOR_OPEN
        initialAdvertisingFinished = false
        openConfirmed = false
        RelayStatusStore.addEvent(this, "0312 감지: 2222 문 열림 확인 대기 시작")
        updateNotification(
            getString(R.string.notification_detected_title),
            getString(R.string.notification_detected_text)
        )

        val scanResult = BleScanRegistrar.register(
            this,
            BleScanRegistrar.ScanMode.OPEN_CONFIRMATION
        )
        RelayStatusStore.addEvent(this, scanResult.message)
        if (!scanResult.success) {
            stopSession("2222 문 열림 확인 스캔 전환 실패: ${scanResult.message}")
            return
        }
        if (!startAdvertising(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, "최초 1111 광고")) {
            return
        }
        handler.postDelayed(finishInitialAdvertisingTask, initialAdvertiseDurationMillis)
        handler.postDelayed(openConfirmationTimeoutTask, openConfirmationTimeoutMillis)
    }

    private fun handleOpenConfirmed() {
        if (!openConfirmed) {
            openConfirmed = true
            RelayStatusStore.addEvent(this, "내 학번이 담긴 2222 문 열림 신호 확인")
        }
        if (initialAdvertisingFinished) beginPresenceMonitoring()
    }

    private fun beginPresenceMonitoring() {
        if (
            state != SessionState.WAITING_FOR_OPEN ||
            !openConfirmed ||
            !initialAdvertisingFinished
        ) {
            return
        }

        handler.removeCallbacks(openConfirmationTimeoutTask)
        state = SessionState.MONITORING_PRESENCE
        val scanResult = BleScanRegistrar.register(
            this,
            BleScanRegistrar.ScanMode.PRESENCE_MONITORING
        )
        RelayStatusStore.addEvent(this, scanResult.message)
        if (!scanResult.success) {
            stopSession("3333 근접 확인 스캔 전환 실패: ${scanResult.message}")
            return
        }

        lastHeartbeatAt = SystemClock.elapsedRealtime()
        if (!startAdvertising(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER, "저전력 1111 광고")) {
            return
        }
        updateNotification(
            getString(R.string.notification_session_title),
            getString(R.string.notification_session_text)
        )
        handler.postDelayed(heartbeatTimeoutCheckTask, heartbeatCheckIntervalMillis)
    }

    private fun startAdvertising(advertiseMode: Int, phase: String): Boolean {
        if (!hasAdvertisePermission()) {
            failAndStop("BLE 광고 권한이 없습니다.")
            return false
        }
        val studentId = RelayStatusStore.studentId(this)
        if (studentId == null) {
            failAndStop("저장된 학번이 없어 BLE 광고를 시작할 수 없습니다.")
            return false
        }
        val activeAdvertiser = advertiser
            ?: getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser
            ?: run {
                failAndStop("BLE 광고 미지원 또는 Bluetooth 꺼짐")
                return false
            }
        advertiser = activeAdvertiser

        if (advertiseCallback != null) stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(advertiseMode)
            .setTxPowerLevel(
                if (advertiseMode == AdvertiseSettings.ADVERTISE_MODE_LOW_POWER) {
                    AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
                } else {
                    AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
                }
            )
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceData(
                BleConstants.responseParcelUuid,
                studentId.toByteArray(Charsets.US_ASCII)
            )
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                if (advertiseCallback !== this) return
                RelayStatusStore.setAdvertising(this@BleRelayService, true)
                Log.i(tag, "$phase started")
            }

            override fun onStartFailure(errorCode: Int) {
                if (advertiseCallback !== this) return
                val message = "$phase 시작 실패: ${advertiseErrorName(errorCode)}"
                Log.e(tag, message)
                stopSession(message)
            }
        }
        advertiseCallback = callback
        return try {
            activeAdvertiser.startAdvertising(settings, data, callback)
            true
        } catch (exception: SecurityException) {
            failAndStop("BLE 광고 권한 오류: ${exception.message}")
            false
        } catch (exception: IllegalArgumentException) {
            failAndStop("BLE 광고 데이터 오류: ${exception.message}")
            false
        }
    }

    private fun stopAdvertising() {
        val callback = advertiseCallback
        advertiseCallback = null
        if (callback != null && hasAdvertisePermission()) {
            try {
                advertiser?.stopAdvertising(callback)
            } catch (exception: SecurityException) {
                Log.e(tag, "BLE 광고 중지 실패", exception)
            }
        }
        RelayStatusStore.setAdvertising(this, false)
    }

    private fun hasMatchingStudentId(intent: Intent): Boolean {
        val receivedStudentId = intent.getStringExtra(EXTRA_STUDENT_ID)
        return receivedStudentId != null &&
            receivedStudentId == RelayStatusStore.studentId(this)
    }

    private fun hasHeartbeatTimedOut(): Boolean =
        SystemClock.elapsedRealtime() - lastHeartbeatAt >= heartbeatTimeoutMillis

    private fun stopSession(message: String) {
        if (state != SessionState.IDLE) RelayStatusStore.addEvent(this, message)
        Log.i(tag, message)
        activeSession = false
        state = SessionState.IDLE
        handler.removeCallbacksAndMessages(null)
        stopAdvertising()
        restoreEntryDetectionScan()
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun failAndStop(message: String) {
        Log.e(tag, message)
        stopSession(message)
    }

    private fun restoreEntryDetectionScan() {
        val result = BleScanRegistrar.register(
            this,
            BleScanRegistrar.ScanMode.ENTRY_DETECTION
        )
        RelayStatusStore.addEvent(this, result.message)
        if (!result.success) Log.e(tag, "0312 진입 감지 복구 실패: ${result.message}")
    }

    private fun ensureForegroundStarted() {
        if (foregroundStarted) return
        startForeground(
            notificationId,
            buildNotification(
                getString(R.string.notification_detected_title),
                getString(R.string.notification_detected_text)
            )
        )
        foregroundStarted = true
    }

    private fun updateNotification(title: String, text: String) {
        if (!foregroundStarted) return
        getSystemService(NotificationManager::class.java)?.notify(
            notificationId,
            buildNotification(title, text)
        )
    }

    private fun buildNotification(title: String, text: String): Notification {
        ensureNotificationChannel()
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BleRelayService::class.java).setAction(ACTION_STOP_ADVERTISING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.stop_advertising), stopIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onDestroy() {
        val shouldRestoreEntryScan = state != SessionState.IDLE
        activeSession = false
        state = SessionState.IDLE
        handler.removeCallbacksAndMessages(null)
        stopAdvertising()
        if (shouldRestoreEntryScan) restoreEntryDetectionScan()
        advertiser = null
        foregroundStarted = false
        super.onDestroy()
    }

    private fun hasAdvertisePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED

    private fun advertiseErrorName(errorCode: Int): String = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "UNKNOWN($errorCode)"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private enum class SessionState {
        IDLE,
        WAITING_FOR_OPEN,
        MONITORING_PRESENCE
    }

    companion object {
        @Volatile
        private var activeSession = false

        fun isSessionActive(): Boolean = activeSession

        const val ACTION_ENTRY_SIGNAL = "com.example.doorlock.action.ENTRY_SIGNAL"
        const val ACTION_OPEN_CONFIRMED = "com.example.doorlock.action.OPEN_CONFIRMED"
        const val ACTION_HEARTBEAT = "com.example.doorlock.action.HEARTBEAT"
        const val ACTION_STOP_ADVERTISING = "com.example.doorlock.action.STOP_ADVERTISING"
        const val EXTRA_STUDENT_ID = "student_id"

        private const val initialAdvertiseDurationMillis = 5_000L
        private const val openConfirmationTimeoutMillis = 10_000L
        private const val heartbeatTimeoutMillis = 30_000L
        private const val heartbeatCheckIntervalMillis = 1_000L
        private const val channelId = "ble_relay_status"
        private const val notificationId = 2002
        private const val tag = "BleRelayService"
    }
}
