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
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class BleRelayService : Service() {
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADVERTISING) {
            RelayStatusStore.addEvent(this, "사용자가 1111 송출을 중지했습니다.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            notificationId,
            buildNotification(
                getString(R.string.notification_detected_title),
                getString(R.string.notification_detected_text)
            )
        )
        if (advertiseCallback == null) {
            startAdvertising()
        } else {
            RelayStatusStore.addEvent(this, "추가 0312 신호 감지: 1111 광고는 이미 송출 중입니다.")
        }
        return START_STICKY
    }

    private fun startAdvertising() {
        if (!hasAdvertisePermission()) {
            failAndStop("BLE 광고 권한이 없습니다.")
            return
        }
        val studentId = RelayStatusStore.studentId(this)
        if (studentId == null) {
            failAndStop("저장된 학번이 없어 BLE 광고를 시작할 수 없습니다.")
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val activeAdvertiser = adapter?.bluetoothLeAdvertiser
        if (activeAdvertiser == null) {
            failAndStop("BLE 광고 미지원 또는 Bluetooth 꺼짐")
            return
        }
        advertiser = activeAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.responseParcelUuid)
            .addServiceData(BleConstants.responseParcelUuid, studentId.toByteArray(Charsets.US_ASCII))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                RelayStatusStore.setAdvertising(this@BleRelayService, true)
                RelayStatusStore.addEvent(
                    this@BleRelayService,
                    "학번이 포함된 1111 응답 광고 시작 성공 - 중지할 때까지 지속 송출"
                )
                Log.i(tag, "1111 BLE advertising started")
                getSystemService(NotificationManager::class.java)?.notify(
                    notificationId,
                    buildNotification(
                        getString(R.string.notification_advertising_title),
                        getString(R.string.notification_advertising_text)
                    )
                )
            }

            override fun onStartFailure(errorCode: Int) {
                val message = "1111 광고 시작 실패: ${advertiseErrorName(errorCode)}"
                RelayStatusStore.addEvent(this@BleRelayService, message)
                Log.e(tag, message)
                RelayStatusStore.setAdvertising(this@BleRelayService, false)
                stopSelf()
            }
        }
        advertiseCallback = callback
        try {
            activeAdvertiser.startAdvertising(settings, data, callback)
        } catch (exception: SecurityException) {
            failAndStop("BLE 광고 권한 오류: ${exception.message}")
        } catch (exception: IllegalArgumentException) {
            failAndStop("BLE 광고 데이터 오류: ${exception.message}")
        }
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

    private fun failAndStop(message: String) {
        RelayStatusStore.addEvent(this, message)
        RelayStatusStore.setAdvertising(this, false)
        Log.e(tag, message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        val callback = advertiseCallback
        if (callback != null && hasAdvertisePermission()) {
            try {
                advertiser?.stopAdvertising(callback)
            } catch (exception: SecurityException) {
                Log.e(tag, "Failed to stop BLE advertising", exception)
            }
        }
        advertiseCallback = null
        advertiser = null
        RelayStatusStore.setAdvertising(this, false)
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

    companion object {
        const val ACTION_START_ADVERTISING = "com.example.doorlock.action.START_ADVERTISING"
        const val ACTION_STOP_ADVERTISING = "com.example.doorlock.action.STOP_ADVERTISING"
        private const val channelId = "ble_relay_status"
        private const val notificationId = 2002
        private const val tag = "BleRelayService"
    }
}
