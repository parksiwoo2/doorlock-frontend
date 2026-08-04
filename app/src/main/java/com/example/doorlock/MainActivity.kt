package com.example.doorlock

import android.Manifest
import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var setupContainer: View
    private lateinit var mainContainer: View
    private lateinit var setupProgress: View
    private lateinit var statusText: TextView
    private lateinit var relayStatusText: TextView
    private lateinit var retryButton: Button
    private lateinit var studentIdContainer: View
    private lateinit var studentIdInput: EditText
    private lateinit var saveStudentIdButton: Button
    private var chooserOpen = false
    private var initialSetupStarted = false

    private val cdmManager by lazy {
        getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasRequiredBlePermissions()) {
            configureCompanionAndScan()
        } else {
            showSetupError("필수 Bluetooth 권한이 거부되었습니다.")
        }
    }

    private val associationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        chooserOpen = false
        if (result.resultCode == Activity.RESULT_OK) {
            RelayStatusStore.addEvent(this, "동반 기기 등록 완료")
            registerBackgroundScan()
        } else {
            showSetupError("동반 기기 등록이 취소되었습니다.")
        }
    }

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshStatus()
            mainHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupContainer = findViewById(R.id.setup_container)
        mainContainer = findViewById(R.id.main_container)
        setupProgress = findViewById(R.id.setup_progress)
        statusText = findViewById(R.id.status_text)
        relayStatusText = findViewById(R.id.relay_status_text)
        retryButton = findViewById(R.id.retry_button)
        studentIdContainer = findViewById(R.id.student_id_container)
        studentIdInput = findViewById(R.id.student_id_input)
        saveStudentIdButton = findViewById(R.id.save_student_id_button)
        retryButton.setOnClickListener {
            retryButton.visibility = View.GONE
            beginConfiguration()
        }
        saveStudentIdButton.setOnClickListener { saveStudentIdAndContinue() }

        if (RelayStatusStore.studentId(this) == null) {
            showStudentIdInput()
        } else if (hasCompanionAssociation() && hasRequiredBlePermissions()) {
            showMainPage()
            registerBackgroundScan(showToast = false)
        } else if (savedInstanceState == null) {
            showSetupProgress(R.string.setup_preparing)
            mainHandler.post { startInitialSetupOnce() }
        } else {
            showSetupError(getString(R.string.setup_retry_description), recordEvent = false)
        }
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(refreshTask)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshTask)
        super.onPause()
    }

    private fun beginConfiguration() {
        showSetupProgress(R.string.setup_preparing)
        val missingPermissions = requiredPermissions().filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            RelayStatusStore.addEvent(this, "Bluetooth 권한 요청")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            configureCompanionAndScan()
        }
    }

    private fun configureCompanionAndScan() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            RelayStatusStore.addEvent(this, "이 기기는 CDM을 지원하지 않아 PendingIntent 스캔만 사용합니다.")
            registerBackgroundScan()
            return
        }
        if (hasCompanionAssociation()) {
            RelayStatusStore.addEvent(this, "기존 동반 기기 등록 확인")
            registerBackgroundScan()
        } else {
            requestCompanionDevice()
        }
    }

    private fun requestCompanionDevice() {
        statusText.setText(R.string.status_waiting)
        RelayStatusStore.addEvent(
            this,
            "0312 서비스를 광고 중인 라즈베리파이를 검색합니다."
        )
        val deviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(
                android.bluetooth.le.ScanFilter.Builder()
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
                runOnUiThread {
                    chooserOpen = false
                    RelayStatusStore.addEvent(this@MainActivity, "동반 기기 등록됨")
                    registerBackgroundScan()
                }
            }

            override fun onFailure(error: CharSequence?) {
                runOnUiThread {
                    chooserOpen = false
                    showSetupError("동반 기기 등록 실패: ${error ?: "알 수 없는 오류"}")
                }
            }
        }, null)
    }

    private fun launchAssociationChooser(intentSender: IntentSender) {
        runOnUiThread {
            if (!chooserOpen) {
                chooserOpen = true
                associationLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        }
    }

    private fun registerBackgroundScan(showToast: Boolean = true) {
        val result = BleScanRegistrar.register(this)
        RelayStatusStore.addEvent(this, result.message)
        if (showToast) {
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        }
        if (result.success) {
            showMainPage()
        } else {
            showSetupError(result.message, recordEvent = false)
        }
        refreshStatus()
    }

    private fun startInitialSetupOnce() {
        if (initialSetupStarted) return
        initialSetupStarted = true
        beginConfiguration()
    }

    private fun showStudentIdInput() {
        setupContainer.visibility = View.VISIBLE
        mainContainer.visibility = View.GONE
        setupProgress.visibility = View.GONE
        statusText.visibility = View.GONE
        retryButton.visibility = View.GONE
        studentIdContainer.visibility = View.VISIBLE
    }

    private fun saveStudentIdAndContinue() {
        val studentId = studentIdInput.text.toString().trim()
        if (studentId.length != 10 || !studentId.all(Char::isDigit)) {
            studentIdInput.error = getString(R.string.student_id_error)
            return
        }
        RelayStatusStore.setStudentId(this, studentId)
        RelayStatusStore.addEvent(this, "학번 등록 완료")
        studentIdInput.clearFocus()
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(studentIdInput.windowToken, 0)
        beginConfiguration()
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
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showSetupProgress(messageRes: Int) {
        setupContainer.visibility = View.VISIBLE
        mainContainer.visibility = View.GONE
        studentIdContainer.visibility = View.GONE
        setupProgress.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        retryButton.visibility = View.GONE
        statusText.setText(messageRes)
    }

    private fun showSetupError(message: String, recordEvent: Boolean = true) {
        if (recordEvent) RelayStatusStore.addEvent(this, message)
        setupContainer.visibility = View.VISIBLE
        mainContainer.visibility = View.GONE
        studentIdContainer.visibility = View.GONE
        setupProgress.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showMainPage() {
        setupContainer.visibility = View.GONE
        mainContainer.visibility = View.VISIBLE
        refreshStatus()
    }

    private fun refreshStatus() {
        relayStatusText.setText(
            when {
                RelayStatusStore.isAdvertising(this) -> R.string.status_advertising
                RelayStatusStore.isScanRegistered(this) -> R.string.status_ready
                else -> R.string.status_not_configured
            }
        )
    }
}
