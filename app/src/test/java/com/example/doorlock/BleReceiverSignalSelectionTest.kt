package com.example.doorlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleReceiverSignalSelectionTest {
    @Test
    fun allSignalsAdvertised_selectsOnlySignalExpectedByCurrentPhase() {
        assertEquals(
            BleReceiver.SignalType.ENTRY,
            select(RelayStatusStore.RelayPhase.WATCHING_0312, true, true, true)
        )
        assertEquals(
            BleReceiver.SignalType.OPEN_CONFIRMED,
            select(RelayStatusStore.RelayPhase.REQUESTING_OPEN, true, true, true)
        )
        assertEquals(
            BleReceiver.SignalType.HEARTBEAT,
            select(RelayStatusStore.RelayPhase.INSIDE_ROOM, true, true, true)
        )
    }

    @Test
    fun insideRoom_doesNotTreatOpenOrEntryAsHeartbeat() {
        assertNull(
            select(
                RelayStatusStore.RelayPhase.INSIDE_ROOM,
                hasEntrySignal = true,
                hasOpenSignal = true,
                hasHeartbeatSignal = false
            )
        )
    }

    private fun select(
        phase: RelayStatusStore.RelayPhase,
        hasEntrySignal: Boolean,
        hasOpenSignal: Boolean,
        hasHeartbeatSignal: Boolean
    ): BleReceiver.SignalType? = BleReceiver.selectSignalForPhase(
        phase,
        hasEntrySignal,
        hasOpenSignal,
        hasHeartbeatSignal
    )
}
