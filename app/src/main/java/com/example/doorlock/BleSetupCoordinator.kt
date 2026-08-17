package com.example.doorlock.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.example.doorlock.BleConstants
import com.example.doorlock.BleRelayService
import com.example.doorlock.BleScanRegistrar
import com.example.doorlock.RelayStatusStore

/**
 * origin/main 최신 MainActivity(AppCompatActivity + XML 버전)에 있던 아래 로직을
 * 그대로 옮겨온 클래스입니다.
 *  - 런타임 BLE 권한 요청
 *  - CompanionDeviceManager(CDM) 기반 라즈베리파이 페어링
 *    (onAssociationCreated 콜백과 associationLauncher 결과, 두 경로 모두에서
 *     완료 처리하는 최신 구조를 그대로 반영했습니다)
 *  - BLE PendingIntent 스캔 등록(BleScanRegistrar.register)
 *
 * BLE 쪽 클래스는 이 클래스에서 전혀 수정하지 않고 기존 공개 API만 그대로 호출합니다.
 *
 * ⚠️ 확인 필요: BleScanRegistrar.register(context) / BleRelayService.isSessionActive() /
 * RelayStatusStore.setStudentId(...) 시그니처가 "학번 난독화" 기능 추가로 바뀌었을 수 있습니다.
 * origin/main의 RelayStatusStore.kt / BleScanRegistrar.kt 최신본을 확인 후
 * 시그니처가 다르면 이 파일과 StudentIdRepository.kt를 함께 조정해야 합니다.
 *
 * registerForActivityResult()는 Activity가 STARTED 상태가 되기 전에 등록해야 하므로,
 * 이 클래스는 반드시 MainActivity.onCreate() 안에서 생성해야 합니다.
 */
class BleSetupCoordinator(private val activity: ComponentActivity) {

    private val cdmManager: CompanionDeviceManager =
        activity.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

    private var chooserOpen = false
    private var initialSetupStarted = false

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            // origin/main과 동일하게, 요청 결과 맵이 아니라 "실제 부여된 권한 상태"를 다시 확인합니다.
            if (hasRequiredBlePermissions()) {
                configureCompanionAndScan()
            } else {
                val message = "필수 Bluetooth 권한이 거부되었습니다."
                RelayStatusStore.addEvent(activity, message)
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }

    // CDM 페어링 완료는 onAssociationCreated 콜백과 이 launcher 결과 두 경로 모두에서 올 수 있어
    // registerBackgroundScan()이 중복 호출될 수 있습니다. BleScanRegistrar.register()는
    // 이미 "다시 시작" 형태(stopScan 후 startScan)라 중복 호출 자체가 치명적이지는 않지만,
    // 정확한 동작은 origin/main의 BleScanRegistrar.kt 최신본 확인 후 필요시 방지 로직을 추가하세요.
    private val associationLauncher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            chooserOpen = false
            if (result.resultCode == Activity.RESULT_OK) {
                RelayStatusStore.addEvent(activity, "동반 기기 등록 완료")
                registerBackgroundScan()
            } else {
                val message = "동반 기기 등록이 취소되었습니다."
                RelayStatusStore.addEvent(activity, message)
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }

    /**
     * 이미 학번이 등록된 상태로 앱을 다시 열었을 때 호출합니다.
     * (Splash가 등록된 학번을 확인하고 Home으로 바로 이동하는 시점에 호출)
     */
    fun ensureConfigured(showToast: Boolean = false) {
        if (RelayStatusStore.studentId(activity) == null) return
        if (
            RelayStatusStore.isInitialSetupComplete(activity) &&
            hasCompanionAssociation() &&
            hasRequiredBlePermissions()
        ) {
            if (!BleRelayService.isSessionActive()) {
                registerBackgroundScan(showToast = showToast)
            }
        } else {
            beginConfiguration()
        }
    }

    /**
     * 학번을 새로 등록한 직후 호출합니다. (권한요청 → CDM 페어링 → 스캔 등록 순서로 진행)
     */
    fun beginConfiguration() {
        if (initialSetupStarted) return
        initialSetupStarted = true
        val missingPermissions = requiredPermissions().filter {
            ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            RelayStatusStore.addEvent(activity, "Bluetooth 권한 요청")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            configureCompanionAndScan()
        }
    }

    private fun configureCompanionAndScan() {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            RelayStatusStore.addEvent(activity, "이 기기는 CDM을 지원하지 않아 PendingIntent 스캔만 사용합니다.")
            registerBackgroundScan()
            return
        }
        if (hasCompanionAssociation()) {
            RelayStatusStore.addEvent(activity, "기존 동반 기기 등록 확인")
            registerBackgroundScan()
        } else {
            requestCompanionDevice()
        }
    }

    private fun requestCompanionDevice() {
        RelayStatusStore.addEvent(
            activity,
            "0312 서비스를 광고 중인 라즈베리파이를 검색합니다."
        )
        val deviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(
                ScanFilter.Builder()
                    .setServiceUuid(BleConstants.targetParcelUuid)
                    .build()
            )
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)
            .build()
        cdmManager.associate(request, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                launchAssociationChooser(intentSender)
            }

            @Suppress("DEPRECATION")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                launchAssociationChooser(chooserLauncher)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                activity.runOnUiThread {
                    chooserOpen = false
                    RelayStatusStore.addEvent(activity, "동반 기기 등록됨")
                    registerBackgroundScan()
                }
            }

            override fun onFailure(error: CharSequence?) {
                activity.runOnUiThread {
                    chooserOpen = false
                    val message = "동반 기기 등록 실패: ${error ?: "알 수 없는 오류"}"
                    RelayStatusStore.addEvent(activity, message)
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            }
        }, null)
    }

    private fun launchAssociationChooser(intentSender: IntentSender) {
        activity.runOnUiThread {
            if (!chooserOpen) {
                chooserOpen = true
                associationLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        }
    }

    private fun registerBackgroundScan(showToast: Boolean = true) {
        val result = BleScanRegistrar.register(activity)
        RelayStatusStore.addEvent(activity, result.message)
        if (showToast) {
            Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
        }
        RelayStatusStore.setInitialSetupComplete(activity, result.success)
    }

    @Suppress("DEPRECATION")
    private fun hasCompanionAssociation(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdmManager.myAssociations.isNotEmpty()
        } else {
            cdmManager.associations.isNotEmpty()
        }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasRequiredBlePermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
