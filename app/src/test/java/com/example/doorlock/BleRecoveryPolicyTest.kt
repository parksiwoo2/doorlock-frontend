package com.example.doorlock

import android.bluetooth.le.ScanCallback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRecoveryPolicyTest {
    @Test
    fun scanStart_onlyZeroIsSuccess() {
        assertTrue(BleScanRegistrar.isSuccessfulScanStart(0))
        assertFalse(
            BleScanRegistrar.isSuccessfulScanStart(
                ScanCallback.SCAN_FAILED_ALREADY_STARTED
            )
        )
        assertFalse(
            BleScanRegistrar.isSuccessfulScanStart(
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR
            )
        )
    }

    @Test
    fun persistedHeartbeat_isRecoverableOnlyBeforeTimeout() {
        val lastHeartbeatAt = 100_000L

        assertTrue(BleRelayService.isHeartbeatFresh(lastHeartbeatAt, 129_999L))
        assertFalse(BleRelayService.isHeartbeatFresh(lastHeartbeatAt, 130_000L))
        assertFalse(BleRelayService.isHeartbeatFresh(lastHeartbeatAt, 99_999L))
        assertFalse(BleRelayService.isHeartbeatFresh(null, 110_000L))
    }
}
