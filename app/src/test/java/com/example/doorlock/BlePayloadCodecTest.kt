package com.example.doorlock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun presenceHeartbeat_usesSessionTokenInsteadOfStudentId() {
        assertArrayEquals(
            byteArrayOf(200.toByte(), 1),
            BlePayloadCodec.presenceAdvertisement(200, true)
        )
        assertTrue(BlePayloadCodec.matchesHeartbeat(byteArrayOf(200.toByte()), 200))
    }
}
