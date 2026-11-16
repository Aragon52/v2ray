package com.artesys.netcon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetconLicenseManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("netcon_license_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun trialIsActiveDuringFirstThreeDaysAndFreezesAfter72Hours() {
        val startEpoch = 1_700_000_000_000L
        val prefs = context.getSharedPreferences("netcon_license_prefs", Context.MODE_PRIVATE)

        // Day 0: fresh install
        val initial = NetconLicenseManager.evaluateFromPreferences(
            prefs = prefs,
            packageFirstInstallMs = startEpoch,
            nowEpochMs = startEpoch,
        )
        assertEquals(NetconLicenseManager.State.TRIAL_ACTIVE, initial.state)
        assertTrue(initial.isAccessAllowed)
        assertFalse(initial.isFrozen)
        assertEquals(NetconLicenseManager.TRIAL_DURATION_MS, initial.remainingTrialMs)

        // 2 days + 23 hours later: still active
        val almostExpired = startEpoch + NetconLicenseManager.TRIAL_DURATION_MS - 60_000L
        val midTrial = NetconLicenseManager.evaluateFromPreferences(
            prefs = prefs,
            packageFirstInstallMs = startEpoch,
            nowEpochMs = almostExpired,
        )
        assertEquals(NetconLicenseManager.State.TRIAL_ACTIVE, midTrial.state)
        assertTrue(midTrial.isAccessAllowed)
        assertEquals(60_000L, midTrial.remainingTrialMs)

        // Exactly 72 hours (3 days) elapsed: trial expires and APK freezes
        val atExpiry = startEpoch + NetconLicenseManager.TRIAL_DURATION_MS
        val expired = NetconLicenseManager.evaluateFromPreferences(
            prefs = prefs,
            packageFirstInstallMs = startEpoch,
            nowEpochMs = atExpiry,
        )
        assertEquals(NetconLicenseManager.State.TRIAL_EXPIRED_FROZEN, expired.state)
        assertFalse(expired.isAccessAllowed)
        assertTrue(expired.isFrozen)
        assertEquals(0L, expired.remainingTrialMs)
    }

    @Test
    fun clockRollbackDoesNotExtendExpiredThreeDayTrial() {
        val startEpoch = 1_700_000_000_000L
        val prefs = context.getSharedPreferences("netcon_license_prefs", Context.MODE_PRIVATE)

        val afterExpiry = startEpoch + NetconLicenseManager.TRIAL_DURATION_MS + 10_000L
        val expired = NetconLicenseManager.evaluateFromPreferences(
            prefs = prefs,
            packageFirstInstallMs = startEpoch,
            nowEpochMs = afterExpiry,
        )
        assertTrue(expired.isFrozen)

        // Attempt to roll system clock back to day 1
        val rolledBack = startEpoch + 3_600_000L
        val afterRollback = NetconLicenseManager.evaluateFromPreferences(
            prefs = prefs,
            packageFirstInstallMs = startEpoch,
            nowEpochMs = rolledBack,
        )
        assertTrue(afterRollback.isFrozen)
        assertFalse(afterRollback.isAccessAllowed)
    }

    @Test
    fun oneKeyWorksOnlyForItsAssignedAccountAndRejectsOtherAccounts() {
        val startEpoch = 1_700_000_000_000L
        NetconLicenseManager.resetTrialWindow(context, startEpoch)
        val afterExpiry = startEpoch + NetconLicenseManager.TRIAL_DURATION_MS + 5_000L

        val aliceAccount = "alice@artesys.io"
        val bobAccount = "bob@artesys.io"
        val aliceKey = NetconLicenseManager.generateLicenseKeyForAccount(aliceAccount, "FULL", "2026")

        // 1. Key generated for Alice MUST fail when Bob tries to activate it with Bob's account
        val bobAttempt = NetconLicenseManager.activateLicenseKey(
            context = context,
            rawKey = aliceKey,
            accountIdentifier = bobAccount,
            nowEpochMs = afterExpiry,
        )
        assertFalse(bobAttempt.success)
        assertTrue(bobAttempt.status.isFrozen)

        // 2. Key generated for Alice succeeds when activated with Alice's account
        val aliceAttempt = NetconLicenseManager.activateLicenseKey(
            context = context,
            rawKey = aliceKey,
            accountIdentifier = aliceAccount,
            nowEpochMs = afterExpiry,
        )
        assertTrue(aliceAttempt.success)
        assertTrue(aliceAttempt.status.isLicensed)
        assertEquals(aliceAccount, aliceAttempt.status.activatedAccount)
        assertFalse(aliceAttempt.status.isFrozen)
    }
}
