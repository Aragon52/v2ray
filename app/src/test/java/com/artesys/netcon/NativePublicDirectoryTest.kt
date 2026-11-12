package com.artesys.netcon

import org.junit.Assert.assertThrows
import org.junit.Test

class NativePublicDirectoryTest {
    @Test
    fun rejectsFeedLargerThanTwoMegabytesBeforeParsing() {
        val oversized = "x".repeat(2_000_001)
        assertThrows(IllegalArgumentException::class.java) {
            NativePublicDirectory.parse(oversized, null)
        }
    }

    @Test
    fun rejectsUnsupportedCountryBeforeRequest() {
        assertThrows(IllegalArgumentException::class.java) {
            NativePublicDirectory.fetch("XX")
        }
    }
}