package com.example.doorlock

internal object BlePayloadCodec {
    const val encodedStudentIdLength = 20
    const val initialAdvertisementLength = encodedStudentIdLength + 1
    const val openConfirmationLength = encodedStudentIdLength + 1
    const val presenceAdvertisementLength = 2
    const val heartbeatRosterLength = 24

    private const val visibleValue = 1
    private const val hiddenValue = 0
    private val hexDigits = "0123456789ABCDEF".toCharArray()

    fun encodeStudentId(studentId: String): ByteArray {
        require(studentId.length == 10 && studentId.all(Char::isDigit)) {
            "Student ID must contain exactly 10 digits."
        }

        val swapped = studentId.toByteArray(Charsets.US_ASCII)
        for (index in swapped.indices step 2) {
            val first = swapped[index]
            swapped[index] = swapped[index + 1]
            swapped[index + 1] = first
        }

        val encoded = CharArray(swapped.size * 2)
        swapped.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            encoded[index * 2] = hexDigits[value ushr 4]
            encoded[index * 2 + 1] = hexDigits[value and 0x0F]
        }
        return String(encoded).toByteArray(Charsets.US_ASCII)
    }

    fun initialAdvertisement(studentId: String, presenceVisible: Boolean): ByteArray =
        appendByte(encodeStudentId(studentId), visibilityByte(presenceVisible))

    fun openConfirmationFilterData(studentId: String): ByteArray =
        appendByte(encodeStudentId(studentId), 0.toByte())

    fun openConfirmationFilterMask(): ByteArray =
        ByteArray(openConfirmationLength) { index ->
            if (index < encodedStudentIdLength) 0xFF.toByte() else 0
        }

    fun openSessionToken(payload: ByteArray?, studentId: String): Int? {
        if (payload == null || payload.size != openConfirmationLength) return null
        val encodedStudentId = encodeStudentId(studentId)
        for (index in encodedStudentId.indices) {
            if (payload[index] != encodedStudentId[index]) return null
        }
        return (payload.last().toInt() and 0xFF).takeIf { it in 1..255 }
    }

    fun presenceAdvertisement(sessionToken: Int, presenceVisible: Boolean): ByteArray =
        byteArrayOf(tokenByte(sessionToken), visibilityByte(presenceVisible))

    fun matchesHeartbeatRoster(payload: ByteArray?, sessionToken: Int): Boolean {
        if (payload == null || payload.size != heartbeatRosterLength) return false
        val expectedToken = tokenByte(sessionToken)
        return payload.any { it == expectedToken }
    }

    private fun visibilityByte(presenceVisible: Boolean): Byte =
        if (presenceVisible) visibleValue.toByte() else hiddenValue.toByte()

    private fun tokenByte(sessionToken: Int): Byte {
        require(sessionToken in 1..255) {
            "Session token must be between 1 and 255; 0 is reserved for an empty roster slot."
        }
        return sessionToken.toByte()
    }

    private fun appendByte(source: ByteArray, value: Byte): ByteArray =
        source.copyOf(source.size + 1).also { it[it.lastIndex] = value }
}
