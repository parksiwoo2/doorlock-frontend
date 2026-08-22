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
    private var sessionToken: Int? = null
    private var lastHeartbeatAt = 0L

    private val refreshVisibilityAdvertisementTask: Runnable = Runnable {
        when (state) {
            SessionState.WAITING_FOR_OPEN -> {
                if (!initialAdvertisingFinished && advertiseCallback != null) {
                    startAdvertising(
                        AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
                        "공개 설정이 갱신된 최초 1111 광고"
                    )
                }
            }
            SessionState.MONITORING_PRESENCE -> {
                if (advertiseCallback != null) {
                    startAdvertising(
                        AdvertiseSettings.ADVERTISE_MODE_LOW_POWER,
                        "공개 설정이 갱신된 저전력 1111 광고"
                    )
                }
            }
            SessionState.IDLE -> Unit
        }
    }

    private val finishInitialAdvertisingTask: Runnable = Runnable {
        if (state != SessionState.WAITING_FOR_OPEN) return@Runnable
        stopAdvertising()
        initialAdvertisingFinished = true
        RelayStatusStore.addEvent(this, "최초 1111 학번 광고 5초 송출 완료")
        if (openConfirmed) beginPresenceMonitoring()
    }

    private val openConfirmationTimeoutTask: Runnable = Runnable {
        if (state == SessionState.WAITING_FOR_OPEN) {
            stopSession("10초 안에 내 학번과 세션 번호가 담긴 2222 신호를 받지 못했습니다.")
        }
    }

    private val heartbeatTimeoutCheckTask: Runnable = object : Runnable {
        override fun run() {
            if (state != SessionState.MONITORING_PRESENCE) return
            if (hasHeartbeatTimedOut()) {
                stopSession("3333의 24바이트 목록에서 내 세션 번호를 30초 동안 확인하지 못해 BLE 세션을 종료합니다.")
            } else {
                handler.postDelayed(this, heartbeatCheckIntervalMillis)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            ensureForegroundStarted()
            resumePersistedPresenceSession(registerScan = true, heartbeatReceivedNow = false)
            return START_STICKY
        }

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
                val receivedSessionToken = intent.getIntExtra(EXTRA_SESSION_TOKEN, -1)
                if (!hasMatchingStudentId(intent) || receivedSessionToken !in 1..255) {
                    if (state == SessionState.IDLE) {
                        stopSession("학번 또는 세션 번호가 올바르지 않은 2222 신호를 무시했습니다.")
                    }
                } else if (state == SessionState.WAITING_FOR_OPEN) {
                    handleOpenConfirmed(receivedSessionToken)
                } else if (state == SessionState.IDLE) {
                    stopSession("활성 세션이 없어 2222 신호를 무시했습니다.")
                }
            }

            ACTION_HEARTBEAT -> {
                ensureForegroundStarted()
                handleHeartbeat(intent)
            }

            ACTION_RESUME_PRESENCE -> {
                ensureForegroundStarted()
                resumePersistedPresenceSession(registerScan = true, heartbeatReceivedNow = false)
            }

            ACTION_VISIBILITY_CHANGED -> {
                if (state != SessionState.IDLE) {
                    handler.removeCallbacks(refreshVisibilityAdvertisementTask)
                    handler.postDelayed(
                        refreshVisibilityAdvertisementTask,
                        visibilityUpdateDebounceMillis
                    )
                } else {
                    stopSelf(startId)
                }
            }

            else -> stopSelf(startId)
        }
        return START_STICKY
    }

    private fun handleHeartbeat(intent: Intent) {
        val receivedSessionToken = intent.getIntExtra(EXTRA_SESSION_TOKEN, -1)
        if (state == SessionState.IDLE) {
            val storedSessionToken = persistedPresenceSessionToken()
            if (storedSessionToken == null || receivedSessionToken != storedSessionToken) {
                stopSession("세션 번호가 일치하지 않는 3333 신호를 무시했습니다.")
                return
            }
            if (!restorePresenceSession(
                    storedSessionToken,
                    registerScan = false,
                    heartbeatReceivedNow = true
                )
            ) {
                return
            }
            Log.d(tag, "3333 heartbeat received while restoring session")
            return
        }

        if (!hasMatchingSessionToken(intent)) return
        if (state == SessionState.MONITORING_PRESENCE) {
            recordHeartbeat()
            Log.d(tag, "3333 heartbeat received")
        }
    }

    private fun resumePersistedPresenceSession(
        registerScan: Boolean,
        heartbeatReceivedNow: Boolean
    ) {
        val storedSessionToken = persistedPresenceSessionToken()
        if (storedSessionToken == null) {
            stopSession("복구할 heartbeat 세션이 없어 0312 감시로 돌아갑니다.")
            return
        }
        if (!heartbeatReceivedNow) {
            val storedHeartbeatAt = RelayStatusStore.lastHeartbeatElapsedRealtime(this)
            if (!isHeartbeatFresh(storedHeartbeatAt, SystemClock.elapsedRealtime())) {
                stopSession("저장된 heartbeat 세션이 이미 만료되어 0312 감시로 돌아갑니다.")
                return
            }
        }
        restorePresenceSession(storedSessionToken, registerScan, heartbeatReceivedNow)
    }

    private fun persistedPresenceSessionToken(): Int? =
        RelayStatusStore.sessionToken(this)?.takeIf {
            RelayStatusStore.relayPhase(this) == RelayStatusStore.RelayPhase.INSIDE_ROOM
        }

    private fun restorePresenceSession(
        storedSessionToken: Int,
        registerScan: Boolean,
        heartbeatReceivedNow: Boolean
    ): Boolean {
        activeSession = true
        state = SessionState.MONITORING_PRESENCE
        sessionToken = storedSessionToken

        if (registerScan) {
            val scanResult = BleScanRegistrar.register(
                this,
                BleScanRegistrar.ScanMode.PRESENCE_MONITORING
            )
            RelayStatusStore.addEvent(this, "heartbeat 세션 복구: ${scanResult.message}")
            if (!scanResult.success) {
                stopSession("heartbeat 세션 복구 중 3333 스캔 등록 실패: ${scanResult.message}")
                return false
            }
        }

        if (heartbeatReceivedNow) {
            recordHeartbeat()
        } else {
            lastHeartbeatAt = RelayStatusStore.lastHeartbeatElapsedRealtime(this) ?: return false
        }
        if (!startAdvertising(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER, "복구된 저전력 1111 광고")) {
            return false
        }
        updateNotification(
            getString(R.string.notification_session_title),
            getString(R.string.notification_session_text)
        )
        handler.removeCallbacks(heartbeatTimeoutCheckTask)
        handler.postDelayed(heartbeatTimeoutCheckTask, heartbeatCheckIntervalMillis)
        RelayStatusStore.addEvent(this, "heartbeat 세션 복구 완료: 세션 번호 $storedSessionToken")
        return true
    }

    private fun startEntrySession() {
        activeSession = true
        state = SessionState.WAITING_FOR_OPEN
        initialAdvertisingFinished = false
        openConfirmed = false
        sessionToken = null
        RelayStatusStore.setSessionToken(this, null)
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

    private fun handleOpenConfirmed(receivedSessionToken: Int) {
        if (!openConfirmed) {
            openConfirmed = true
            sessionToken = receivedSessionToken
            RelayStatusStore.setSessionToken(this, receivedSessionToken)
            RelayStatusStore.addEvent(
                this,
                "내 학번이 담긴 2222 문 열림 신호 확인: 세션 번호 $receivedSessionToken"
            )
        } else if (sessionToken != receivedSessionToken) {
            Log.w(tag, "이미 확정된 세션과 다른 2222 세션 번호를 무시합니다.")
            return
        }
        if (initialAdvertisingFinished) beginPresenceMonitoring()
    }

    private fun beginPresenceMonitoring() {
        if (
            state != SessionState.WAITING_FOR_OPEN ||
            !openConfirmed ||
            !initialAdvertisingFinished ||
            sessionToken == null
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

        recordHeartbeat()
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
        val servicePayload = when (state) {
            SessionState.WAITING_FOR_OPEN -> BlePayloadCodec.initialAdvertisement(
                studentId,
                RelayStatusStore.isPresenceVisible(this)
            )
            SessionState.MONITORING_PRESENCE -> {
                val activeSessionToken = sessionToken ?: run {
                    failAndStop("세션 번호가 없어 저전력 BLE 광고를 시작할 수 없습니다.")
                    return false
                }
                BlePayloadCodec.presenceAdvertisement(
                    activeSessionToken,
                    RelayStatusStore.isPresenceVisible(this)
                )
            }
            SessionState.IDLE -> {
                failAndStop("활성 BLE 세션이 없어 광고를 시작할 수 없습니다.")
                return false
            }
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
                servicePayload
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

    private fun hasMatchingSessionToken(intent: Intent): Boolean {
        val receivedSessionToken = intent.getIntExtra(EXTRA_SESSION_TOKEN, -1)
        return receivedSessionToken in 1..255 &&
            receivedSessionToken == sessionToken &&
            receivedSessionToken == RelayStatusStore.sessionToken(this)
    }

    private fun hasHeartbeatTimedOut(): Boolean =
        SystemClock.elapsedRealtime() - lastHeartbeatAt >= heartbeatTimeoutMillis

    private fun recordHeartbeat() {
        lastHeartbeatAt = SystemClock.elapsedRealtime()
        RelayStatusStore.setLastHeartbeatElapsedRealtime(this, lastHeartbeatAt)
    }

    private fun stopSession(message: String) {
        if (state != SessionState.IDLE) RelayStatusStore.addEvent(this, message)
        Log.i(tag, message)
        activeSession = false
        state = SessionState.IDLE
        sessionToken = null
        RelayStatusStore.setSessionToken(this, null)
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
        val previousState = state
        val shouldPreservePresenceSession =
            previousState == SessionState.MONITORING_PRESENCE &&
                sessionToken == persistedPresenceSessionToken()
        activeSession = false
        state = SessionState.IDLE
        handler.removeCallbacksAndMessages(null)
        stopAdvertising()
        if (!shouldPreservePresenceSession) {
            sessionToken = null
            RelayStatusStore.setSessionToken(this, null)
            if (previousState != SessionState.IDLE) restoreEntryDetectionScan()
        }
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
        const val ACTION_RESUME_PRESENCE = "com.example.doorlock.action.RESUME_PRESENCE"
        const val ACTION_VISIBILITY_CHANGED = "com.example.doorlock.action.VISIBILITY_CHANGED"
        const val ACTION_STOP_ADVERTISING = "com.example.doorlock.action.STOP_ADVERTISING"
        const val EXTRA_STUDENT_ID = "student_id"
        const val EXTRA_SESSION_TOKEN = "session_token"

        private const val initialAdvertiseDurationMillis = 5_000L
        private const val openConfirmationTimeoutMillis = 10_000L
        private const val heartbeatTimeoutMillis = 30_000L
        private const val heartbeatCheckIntervalMillis = 1_000L
        private const val visibilityUpdateDebounceMillis = 300L
        private const val channelId = "ble_relay_status"
        private const val notificationId = 2002
        private const val tag = "BleRelayService"

        internal fun isHeartbeatFresh(lastHeartbeatAt: Long?, now: Long): Boolean =
            lastHeartbeatAt != null &&
                now >= lastHeartbeatAt &&
                now - lastHeartbeatAt < heartbeatTimeoutMillis
    }
}
