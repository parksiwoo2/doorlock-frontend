package com.example.doorlock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePayloadCodecTest {
    @Test
    fun studentId_isPairSwappedThenEncodedAsUppercaseHex() {
        assertArrayEquals(
            "30323332323134333635".toByteArray(Charsets.US_ASCII),
            BlePayloadCodec.encodeStudentId("2023123456")
        )
    }

    @Test
    fun initialAdvertisement_appendsVisibilityByte() {
        val visible = BlePayloadCodec.initialAdvertisement("2023123456", true)
        val hidden = BlePayloadCodec.initialAdvertisement("2023123456", false)

        assertEquals(BlePayloadCodec.initialAdvertisementLength, visible.size)
        assertEquals(1, visible.last().toInt())
        assertEquals(0, hidden.last().toInt())
    }

    @Test
    fun openConfirmation_requiresEncodedStudentIdAndReturnsUnsignedToken() {
        val payload = BlePayloadCodec.encodeStudentId("2023123456") + byteArrayOf(0xFE.toByte())

        assertEquals(254, BlePayloadCodec.openSessionToken(payload, "2023123456"))
        assertNull(BlePayloadCodec.openSessionToken(payload, "2023123457"))
        assertNull(
            BlePayloadCodec.openSessionToken(
                BlePayloadCodec.encodeStudentId("2023123456") + byteArrayOf(0),
                "2023123456"
            )
        )
    }

    @Test
    fun presenceHeartbeat_findsSessionTokenIn24ByteRoster() {
        assertArrayEquals(
            byteArrayOf(200.toByte(), 1),
            BlePayloadCodec.presenceAdvertisement(200, true)
        )
        val roster = ByteArray(BlePayloadCodec.heartbeatRosterLength)
        roster[17] = 200.toByte()

        assertTrue(BlePayloadCodec.matchesHeartbeatRoster(roster, 200))
        assertFalse(BlePayloadCodec.matchesHeartbeatRoster(roster, 199))
        assertFalse(BlePayloadCodec.matchesHeartbeatRoster(byteArrayOf(200.toByte()), 200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sessionToken_zeroIsReservedForEmptyRosterSlots() {
        BlePayloadCodec.presenceAdvertisement(0, true)
    }
}
